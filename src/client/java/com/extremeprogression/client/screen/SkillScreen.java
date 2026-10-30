package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.network.TreePayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.tree.TreeRules;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * One skill (design §9): its level, milestones and points; its tree, with the three specializations as columns (each
 * the same eight rows of nodes, design §6) and the trunk below them; and a detail panel for the selected part, from
 * which a specialization is chosen or a node bought. Every request goes to the server, which decides.
 */
public class SkillScreen extends Screen {
	/** Small enough for GUI scale 3 at 1280 × 720 (426 × 240), the scale Minecraft picks there by itself. */
	private static final int W = 420, H = 236;
	private static final int TREE_W = 276, HEADER_H = 34, ROW = 12;
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

	private final Skill skill;
	private final Screen parent;
	private int x0, y0;
	/** What the detail panel shows: a specialization's id, a node's key, or "trunk.<level>" (kept across resizes). */
	private String selectedKey = null;
	private Button action;
	private Button respec;
	/** The tree rules' view of the progression, rebuilt whenever the server sends a new one. */
	private PlayerProgress view;
	private int viewOf = -1;

	private record Detail(String title, String kind, List<String> lines, String status, int statusColor) {}

	public SkillScreen(Skill skill, Screen parent) {
		super(Component.translatable(skill.translationKey()));
		this.skill = skill;
		this.parent = parent;
	}

	private PlayerProgress view() {
		if (view == null || viewOf != ClientProgress.updates()) {
			view = ClientProgress.asProgress();
			viewOf = ClientProgress.updates();
		}
		return view;
	}

	private SkillData data() {
		return view().get(skill);
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		int ty = y0 + HEADER_H + 4;
		int colW = (TREE_W - 8 - 6) / 3;
		int half = (colW - 2) / 2;
		List<Specialization> specs = Specialization.of(skill);
		for (int i = 0; i < specs.size(); i++) {
			Specialization spec = specs.get(i);
			int cx = x0 + 4 + i * (colW + 3);
			if (selectedKey == null) {
				selectedKey = spec.id;
			}
			add(cx, ty + 4, colW, ROW, Component.translatable(spec.translationKey()), spec.id, () -> specState(spec), () -> specBadge(spec));
			for (NodeSlot slot : NodeSlot.values()) {
				boolean single = slot.kind == NodeSlot.Kind.ABILITY;
				int nx = single ? cx : cx + slot.column * (half + 2);
				int ny = ty + 19 + (8 - slot.row) * ROW;
				String key = TreeRules.key(spec, slot);
				add(nx, ny, single ? colW : half, ROW - 1, Component.literal(single ? nodeName(spec, slot) : cellName(slot)), key, () -> nodeState(spec, slot), () -> nodeBadge(spec, slot));
			}
		}
		// The trunk: two rows of two
		int trunkY = ty + 19 + 8 * ROW + 12;
		int nodeW = (TREE_W - 8 - 3) / 2;
		for (int i = 0; i < Skill.TRUNK_LEVELS.length; i++) {
			int level = Skill.TRUNK_LEVELS[i];
			int nx = x0 + 4 + (i % 2) * (nodeW + 3);
			int ny = trunkY + (i / 2) * (ROW + 1);
			String key = "trunk.extremeprogression." + skill.id + "." + level;
			add(nx, ny, nodeW, ROW, Component.literal(level + " · ").append(Component.translatable(key)), "trunk." + level,
					() -> data().level >= level ? TreeNode.State.OWNED : TreeNode.State.LOCKED, () -> data().level >= level ? "✓" : null);
		}
		int dx = x0 + TREE_W + 4, dw = W - TREE_W - 4;
		int th = treeHeight();
		action = this.addRenderableWidget(Button.builder(Component.empty(), b -> act()).pos(dx + 5, ty + th - 21).size(dw - 10, 16).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.skill.back"), b -> this.onClose())
				.pos(this.width / 2 - 128, y0 + H - 20).size(110, 20).build());
		respec = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.skill.respec"),
				b -> Minecraft.getInstance().gui.setScreen(new RespecScreen(skill, this))).pos(this.width / 2 - 14, y0 + H - 20).size(70, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> closeAll())
				.pos(this.width / 2 + 60, y0 + H - 20).size(70, 20).build());
		refreshButtons();
	}

	private void add(int x, int y, int w, int h, Component label, String key, java.util.function.Supplier<TreeNode.State> state,
			java.util.function.Supplier<String> badge) {
		this.addRenderableWidget(new TreeNode(x, y, w, h, label, state, () -> key.equals(selectedKey), () -> {
			selectedKey = key;
			refreshButtons();
		}, badge));
	}

	@Override
	public void tick() {
		refreshButtons();
	}

	/** The detail panel's button: choose the selected specialization, or buy a point in the selected node. */
	private void refreshButtons() {
		if (action == null) {
			return;
		}
		action.visible = false;
		Specialization spec = Specialization.byId(selectedKey);
		if (spec != null && TreeRules.canChoose(view(), spec) == TreeRules.Result.OK) {
			action.visible = true;
			action.setMessage(Component.translatable("gui.extremeprogression.skill.choose", Component.translatable(spec.translationKey())));
		}
		NodeSlot slot = TreeRules.slotOf(selectedKey);
		Specialization owner = slot == null ? null : Specialization.byId(selectedKey.substring(0, selectedKey.indexOf('/')));
		if (owner != null && TreeRules.canBuy(view(), owner, slot) == TreeRules.Result.OK) {
			action.visible = true;
			action.setMessage(Component.translatable("gui.extremeprogression.skill.buy", slot.cost));
		}
		respec.active = TreeRules.canRespec(view(), skill, 0) != TreeRules.Result.NOTHING_TO_RESET;
	}

	private void act() {
		Specialization spec = Specialization.byId(selectedKey);
		if (spec != null) {
			send(new TreePayload(TreePayload.Action.CHOOSE, skill.id, spec.id));
		} else if (TreeRules.slotOf(selectedKey) != null) {
			send(new TreePayload(TreePayload.Action.BUY, skill.id, selectedKey));
		}
	}

	static void send(TreePayload request) {
		if (ClientPlayNetworking.canSend(TreePayload.TYPE)) {
			ClientPlayNetworking.send(request);
		}
	}

	// --- States and texts ------------------------------------------------------------------------

	private TreeNode.State specState(Specialization spec) {
		SkillData d = data();
		int index = TreeRules.indexOf(d, spec);
		if (index >= 0) {
			return TreeRules.active(d, index) != null ? TreeNode.State.OWNED : TreeNode.State.DORMANT;
		}
		TreeRules.Result r = TreeRules.canChoose(view(), spec);
		return r == TreeRules.Result.OK ? TreeNode.State.REACHABLE : r == TreeRules.Result.CONFLICT ? TreeNode.State.CONFLICT : TreeNode.State.LOCKED;
	}

	private String specBadge(Specialization spec) {
		int index = TreeRules.indexOf(data(), spec);
		return index == 0 ? "1st" : index == 1 ? "2nd" : null;
	}

	private String nodeName(Specialization spec, NodeSlot slot) {
		return switch (slot.kind) {
			case ABILITY -> "◆ " + Ui.tr(spec.translationKey() + ".ability") + " " + ROMAN[slot.abilityRank()];
			case PASSIVE -> Ui.tr("gui.extremeprogression.node.passive", slot.ordinal() < 2 ? slot.column + 1 : slot.column + 3);
			case CHOICE -> Ui.tr(slot == NodeSlot.C1 ? "gui.extremeprogression.node.choice_a" : "gui.extremeprogression.node.choice_b");
		};
	}

	/** The short label of a half-width cell (P1 to P4, A or B), where the full name would not fit beside its rank. */
	private static String cellName(NodeSlot slot) {
		return slot.kind == NodeSlot.Kind.CHOICE ? (slot == NodeSlot.C1 ? "A" : "B") : "P" + (slot.ordinal() < 2 ? slot.column + 1 : slot.column + 3);
	}

	private TreeNode.State nodeState(Specialization spec, NodeSlot slot) {
		SkillData d = data();
		int rank = TreeRules.rank(d, spec, slot);
		TreeRules.Result r = TreeRules.canBuy(view(), spec, slot);
		if (r == TreeRules.Result.OK) {
			return TreeNode.State.REACHABLE;
		}
		if (rank > 0) {
			return TreeNode.State.OWNED;
		}
		if (r == TreeRules.Result.DORMANT) {
			return TreeNode.State.DORMANT;
		}
		if (TreeRules.indexOf(d, spec) < 0 && TreeRules.canChoose(view(), spec) == TreeRules.Result.CONFLICT) {
			return TreeNode.State.CONFLICT;
		}
		return TreeNode.State.LOCKED;
	}

	private String nodeBadge(Specialization spec, NodeSlot slot) {
		SkillData d = data();
		int rank = TreeRules.rank(d, spec, slot);
		if (slot.maxRank > 1) {
			NodeSlot partner = slot.partner();
			if (rank == 0 && partner != null && TreeRules.rank(d, spec, partner) > 0) {
				return "or";
			}
			return rank + "/" + slot.maxRank;
		}
		if (rank > 0) {
			return "✓";
		}
		return d.level < slot.level ? "Lv " + slot.level : slot.cost + " pt";
	}

	private Detail detail(String key) {
		if (key == null) {
			return null;
		}
		SkillData d = data();
		if (key.startsWith("trunk.")) {
			int level = Integer.parseInt(key.substring(6));
			String t = "trunk.extremeprogression." + skill.id + "." + level;
			boolean owned = d.level >= level;
			return new Detail(Ui.tr(t), Ui.tr("gui.extremeprogression.skill.trunk_kind", level), wrap(Ui.tr(t + ".desc")),
					owned ? Ui.tr("gui.extremeprogression.node.status.owned") : Ui.tr("gui.extremeprogression.skill.unlocks_at", level), owned ? Ui.OK : Ui.MUTED);
		}
		Specialization spec = Specialization.byId(key);
		if (spec != null) {
			String k = spec.translationKey();
			List<String> lines = new ArrayList<>();
			lines.addAll(wrap(Ui.tr("gui.extremeprogression.skill.gear", Ui.tr(k + ".gear"))));
			lines.addAll(wrap(Ui.tr(k + ".style")));
			lines.addAll(wrap(Ui.tr("gui.extremeprogression.skill.price_of", Ui.tr(k)) + ": " + Ui.tr(k + ".tradeoff")));
			if (spec.conflict() != null) {
				lines.addAll(wrap(Ui.tr("gui.extremeprogression.skill.conflict", Ui.tr(spec.conflict().translationKey()), Ui.tr(spec.conflict().skill.translationKey()))));
			}
			int index = TreeRules.indexOf(d, spec);
			String status;
			int color;
			if (index >= 0) {
				boolean active = TreeRules.active(d, index) != null;
				status = active ? Ui.tr(index == 0 ? "gui.extremeprogression.skill.spec.first" : "gui.extremeprogression.skill.spec.second")
						: Ui.tr("gui.extremeprogression.skill.spec.dormant", TreeRules.unlockLevel(index));
				color = active ? Ui.OK : 0x5555FF;
			} else {
				TreeRules.Result r = TreeRules.canChoose(view(), spec);
				status = r == TreeRules.Result.OK ? Ui.tr("gui.extremeprogression.skill.spec.choosable") : status(r, null);
				color = r == TreeRules.Result.OK ? Ui.WARN : r == TreeRules.Result.CONFLICT ? Ui.DANGER : Ui.MUTED;
			}
			return new Detail(Ui.tr(k), Ui.tr("gui.extremeprogression.skill.spec_levels", Config.FIRST_SPECIALIZATION_LEVEL, Config.SECOND_SPECIALIZATION_LEVEL),
					lines, status, color);
		}
		NodeSlot slot = TreeRules.slotOf(key);
		spec = slot == null ? null : Specialization.byId(key.substring(0, key.indexOf('/')));
		if (spec == null) {
			return null;
		}
		int rank = TreeRules.rank(d, spec, slot);
		String kind = switch (slot.kind) {
			case ABILITY -> Ui.tr("gui.extremeprogression.node.kind.ability", ROMAN[slot.abilityRank()], slot.cost);
			case PASSIVE -> Ui.tr("gui.extremeprogression.node.kind.passive", rank, slot.maxRank, slot.cost);
			case CHOICE -> Ui.tr("gui.extremeprogression.node.kind.choice", rank, slot.maxRank, slot.cost);
		};
		List<String> lines = new ArrayList<>();
		String keyName = TreeRules.indexOf(d, spec) == 1 ? "Shift + R" : "R";
		switch (slot.kind) {
			case ABILITY -> {
				lines.addAll(wrap(Ui.tr(spec.translationKey() + ".ability.desc")));
				lines.addAll(wrap(Ui.tr("gui.extremeprogression.node.desc.ability", keyName, spec.cooldownMinutes)));
			}
			case PASSIVE -> lines.addAll(wrap(Ui.tr("gui.extremeprogression.node.desc.passive", Ui.tr(spec.translationKey()))));
			case CHOICE -> lines.addAll(wrap(Ui.tr("gui.extremeprogression.node.desc.choice")));
		}
		List<String> needs = new ArrayList<>();
		for (NodeSlot r : slot.requires()) {
			needs.add(nodeName(spec, r));
		}
		if (!needs.isEmpty()) {
			lines.addAll(wrap(Ui.tr("gui.extremeprogression.node.requires", String.join(" / ", needs))));
		}
		if (slot.level > 0) {
			lines.addAll(wrap(Ui.tr("gui.extremeprogression.node.requires_level", slot.level)));
		}
		TreeRules.Result r = TreeRules.canBuy(view(), spec, slot);
		String status = r == TreeRules.Result.OK ? Ui.tr("gui.extremeprogression.node.status.buyable")
				: r == TreeRules.Result.MAX_RANK ? Ui.tr("gui.extremeprogression.node.status.owned") : status(r, spec);
		int color = r == TreeRules.Result.OK ? Ui.WARN : r == TreeRules.Result.MAX_RANK ? Ui.OK : Ui.MUTED;
		return new Detail(nodeName(spec, slot), kind, lines, status, color);
	}

	private static String status(TreeRules.Result r, Specialization spec) {
		return Ui.tr("message.extremeprogression.tree." + r.name().toLowerCase(java.util.Locale.ROOT));
	}

	private List<String> wrap(String text) {
		return Ui.wrap(this.font, text, W - TREE_W - 4 - 10);
	}

	private static int treeHeight() {
		return H - HEADER_H - 4 - 24;
	}

	// --- Drawing ---------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		ProgressPayload p = ClientProgress.current();
		ProgressPayload.Entry e = SkillView.entry(skill);
		boolean mastered = SkillView.mastered(e);

		// The header: icon, name, category and opposing skill; level and bar; milestones and points
		Ui.panel(g, x0, y0, W, HEADER_H);
		g.fakeItem(SkillView.icon(skill), x0 + 4, y0 + 4);
		Ui.text(g, this.font, this.title.getString(), x0 + 24, y0 + 4, skill.category.accent);
		String sub = Ui.tr(skill.category.translationKey());
		if (skill.opposite() != null) {
			sub = Ui.tr("gui.extremeprogression.skill.opposite", sub, Ui.tr(skill.opposite().translationKey()));
		}
		Ui.text(g, this.font, Ui.ellipsize(this.font, sub, 170), x0 + 24, y0 + 14, Ui.MUTED);
		int bx = x0 + 202, bw = W - 202 - 6;
		if (mastered && p != null) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.skill.mastery", e.masteryRanks(), p.masteryRanksMax()), bx, y0 + 4, Ui.GOLD);
			if (e.masteryRanks() < p.masteryRanksMax()) {
				Ui.right(g, this.font, Ui.number(e.masteryXp()) + " / " + Ui.number(p.masteryXpPerRank()), bx + bw, y0 + 4, Ui.MUTED);
			}
		} else {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.skill.level", e.level()), bx, y0 + 4, Ui.TEXT);
			Ui.right(g, this.font, Ui.number(e.xp()) + " / " + Ui.number(e.need()) + " XP", bx + bw, y0 + 4, Ui.MUTED);
		}
		Ui.meter(g, bx, y0 + 15, bw, 4, SkillView.fill(e), mastered ? Ui.GOLD : skill.category.accent);
		milestones(g, x0 + 4, y0 + 24, e.level());
		SkillData d = data();
		int free = TreeRules.free(d);
		Ui.right(g, this.font, Ui.tr("gui.extremeprogression.skill.points", free, TreeRules.earned(d)), x0 + W - 6, y0 + 24, free > 0 ? Ui.WARN : Ui.MUTED);

		// The tree and the detail panel
		int ty = y0 + HEADER_H + 4, th = treeHeight();
		Ui.panel(g, x0, ty, TREE_W, th);
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.skill.trunk"), x0 + 4, ty + 19 + 8 * ROW + 2, Ui.MUTED);
		int dx = x0 + TREE_W + 4, dw = W - TREE_W - 4;
		Ui.panel(g, dx, ty, dw, th);
		Detail selected = detail(selectedKey);
		if (selected != null) {
			int y = ty + 4;
			Ui.text(g, this.font, Ui.ellipsize(this.font, selected.title(), dw - 10), dx + 5, y, Ui.TEXT);
			List<String> kind = Ui.wrap(this.font, selected.kind(), dw - 10);
			for (int i = 0; i < kind.size() && i < 2; i++) {
				Ui.text(g, this.font, kind.get(i), dx + 5, y + 10 + i * 9, Ui.MUTED);
			}
			y += 10 + Math.min(2, kind.size()) * 9 + 1;
			Ui.divider(g, dx + 5, y, dw - 10);
			y += 4;
			List<String> status = Ui.wrap(this.font, selected.status(), dw - 10);
			int shown = Math.min(2, status.size());
			int bottom = ty + th - 22 - shown * 9 - 9;
			for (String line : selected.lines()) {
				if (y > bottom) {
					break;
				}
				Ui.text(g, this.font, line, dx + 5, y, Ui.TEXT);
				y += 9;
			}
			for (int i = 0; i < shown; i++) {
				Ui.text(g, this.font, status.get(i), dx + 5, ty + th - 22 - shown * 9 + i * 9, selected.statusColor());
			}
		}
	}

	/** The three milestones of every skill: first and second specialization, and Mastery. */
	private void milestones(GuiGraphicsExtractor g, int x, int y, int level) {
		int max = ClientProgress.current() != null ? ClientProgress.current().maxLevel() : Config.MAX_LEVEL;
		int[] at = {Config.FIRST_SPECIALIZATION_LEVEL, Config.SECOND_SPECIALIZATION_LEVEL, max};
		String[] keys = {"gui.extremeprogression.skill.milestone.first", "gui.extremeprogression.skill.milestone.second", "gui.extremeprogression.skill.milestone.mastery"};
		for (int i = 0; i < at.length; i++) {
			String label = at[i] + " · " + Ui.tr(keys[i]);
			int color = level >= at[i] ? Ui.OK : Ui.MUTED;
			Ui.text(g, this.font, label, x, y, color);
			x += this.font.width(label) + 12;
		}
	}

	/** Back to the Character Sheet (or wherever this screen was opened from). */
	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	private void closeAll() {
		if (parent != null) {
			parent.onClose();
		} else {
			Minecraft.getInstance().gui.setScreen(null);
		}
	}

	public boolean isPauseScreen() {
		return false;
	}

	/** The selected part, for tests. */
	public void select(String key) {
		selectedKey = key;
		refreshButtons();
	}
}
