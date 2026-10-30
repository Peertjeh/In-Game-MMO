package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Config;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * One skill (design §9): its level and milestones, its tree (the three specializations as columns, the trunk below
 * them) and a detail panel for the selected part. Milestone 2 shows the tree; choosing and buying come in milestone 5.
 */
public class SkillScreen extends Screen {
	private static final int W = 420, H = CharacterSheetScreen.H;
	private static final int TREE_W = 300, HEADER_H = 44;

	private final Skill skill;
	private final Screen parent;
	private int x0, y0;
	/** What the detail panel shows: a specialization, its ability, its price, or a trunk level (kept across resizes). */
	private String selectedKey = null;
	private final java.util.Map<String, Detail> details = new java.util.HashMap<>();

	private record Detail(String title, String kind, List<String> lines, String requirement) {}

	public SkillScreen(Skill skill, Screen parent) {
		super(Component.translatable(skill.translationKey()));
		this.skill = skill;
		this.parent = parent;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		details.clear();
		int ty = y0 + HEADER_H + 6;
		int colW = (TREE_W - 12 - 8) / 3;
		List<Specialization> specs = Specialization.of(skill);
		for (int i = 0; i < specs.size(); i++) {
			Specialization spec = specs.get(i);
			int cx = x0 + 6 + i * (colW + 4);
			int cy = ty + 6;
			if (selectedKey == null) {
				selectedKey = spec.id;
			}
			node(cx, cy, colW, 20, Component.translatable(spec.translationKey()), this::specState, spec.id, specDetail(spec));
			node(cx, cy + 24, colW, 16, Component.literal("◆ ").append(Component.translatable(spec.translationKey() + ".ability")), this::specState,
					spec.id + ".ability", abilityDetail(spec));
			node(cx, cy + 44, colW, 16, Component.translatable("gui.extremeprogression.skill.price"), this::specState, spec.id + ".price", priceDetail(spec));
		}
		// The trunk: two rows of two
		int trunkY = ty + 6 + 64 + 16;
		int nodeW = (TREE_W - 12 - 4) / 2;
		for (int i = 0; i < Skill.TRUNK_LEVELS.length; i++) {
			int level = Skill.TRUNK_LEVELS[i];
			int nx = x0 + 6 + (i % 2) * (nodeW + 4);
			int ny = trunkY + (i / 2) * 20;
			String key = "trunk.extremeprogression." + skill.id + "." + level;
			Detail d = new Detail(Ui.tr(key), Ui.tr("gui.extremeprogression.skill.trunk_kind", level), wrap(Ui.tr(key + ".desc")),
					Ui.tr("gui.extremeprogression.skill.unlocks_at", level));
			node(nx, ny, nodeW, 16, Component.literal(level + " · ").append(Component.translatable(key)),
					() -> SkillView.entry(skill).level() >= level ? TreeNode.State.OWNED : TreeNode.State.LOCKED, "trunk." + level, d);
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.skill.back"), b -> this.onClose())
				.pos(this.width / 2 - 104, y0 + H - 20).size(120, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> closeAll())
				.pos(this.width / 2 + 20, y0 + H - 20).size(84, 20).build());
	}

	private void node(int x, int y, int w, int h, Component label, java.util.function.Supplier<TreeNode.State> state, String key, Detail detail) {
		details.put(key, detail);
		this.addRenderableWidget(new TreeNode(x, y, w, h, label, state, () -> key.equals(selectedKey), () -> selectedKey = key));
	}

	/** Choosing a specialization comes in milestone 5: until then every column is shown, none is reachable yet. */
	private TreeNode.State specState() {
		return TreeNode.State.LOCKED;
	}

	private Detail specDetail(Specialization spec) {
		String key = spec.translationKey();
		List<String> lines = new ArrayList<>();
		lines.add(Ui.tr("gui.extremeprogression.skill.gear", Ui.tr(key + ".gear")));
		lines.addAll(wrap(Ui.tr(key + ".style")));
		if (spec.conflict() != null) {
			lines.addAll(wrap(Ui.tr("gui.extremeprogression.skill.conflict", Ui.tr(spec.conflict().translationKey()),
					Ui.tr(spec.conflict().skill.translationKey()))));
		}
		return new Detail(Ui.tr(key), Ui.tr("gui.extremeprogression.skill.spec_kind"), lines, Ui.tr("gui.extremeprogression.skill.spec_levels",
				Config.FIRST_SPECIALIZATION_LEVEL, Config.SECOND_SPECIALIZATION_LEVEL));
	}

	private Detail abilityDetail(Specialization spec) {
		String key = spec.translationKey() + ".ability";
		return new Detail(Ui.tr(key), Ui.tr("gui.extremeprogression.skill.ability_kind", Ui.tr(spec.translationKey())), wrap(Ui.tr(key + ".desc")),
				Ui.tr("gui.extremeprogression.skill.ability_key"));
	}

	private Detail priceDetail(Specialization spec) {
		return new Detail(Ui.tr("gui.extremeprogression.skill.price_of", Ui.tr(spec.translationKey())), Ui.tr("gui.extremeprogression.skill.price_kind"),
				wrap(Ui.tr(spec.translationKey() + ".tradeoff")), "");
	}

	private List<String> wrap(String text) {
		return Ui.wrap(this.font, text, W - TREE_W - 6 - 12);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		ProgressPayload p = ClientProgress.current();
		ProgressPayload.Entry e = SkillView.entry(skill);
		boolean mastered = SkillView.mastered(e);

		// The header: icon, name, category and opposing skill; level and bar; milestones
		Ui.panel(g, x0, y0, W, HEADER_H);
		g.fakeItem(SkillView.icon(skill), x0 + 6, y0 + 6);
		Ui.text(g, this.font, this.title.getString(), x0 + 26, y0 + 6, skill.category.accent);
		String sub = Ui.tr(skill.category.translationKey());
		if (skill.opposite() != null) {
			sub = Ui.tr("gui.extremeprogression.skill.opposite", sub, Ui.tr(skill.opposite().translationKey()));
		}
		Ui.text(g, this.font, Ui.ellipsize(this.font, sub, 170), x0 + 26, y0 + 17, Ui.MUTED);
		int bx = x0 + 202, bw = W - 202 - 8;
		if (mastered && p != null) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.skill.mastery", e.masteryRanks(), p.masteryRanksMax()), bx, y0 + 6, Ui.GOLD);
			if (e.masteryRanks() < p.masteryRanksMax()) {
				Ui.right(g, this.font, Ui.number(e.masteryXp()) + " / " + Ui.number(p.masteryXpPerRank()), bx + bw, y0 + 6, Ui.MUTED);
			}
		} else {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.skill.level", e.level()), bx, y0 + 6, Ui.TEXT);
			Ui.right(g, this.font, Ui.number(e.xp()) + " / " + Ui.number(e.need()) + " XP", bx + bw, y0 + 6, Ui.MUTED);
		}
		Ui.meter(g, bx, y0 + 17, bw, 5, SkillView.fill(e), mastered ? Ui.GOLD : skill.category.accent);
		milestones(g, x0 + 6, y0 + 31, e.level());

		// The tree and the detail panel
		int ty = y0 + HEADER_H + 6, th = H - HEADER_H - 6 - 26;
		Ui.panel(g, x0, ty, TREE_W, th);
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.skill.trunk"), x0 + 6, ty + 6 + 64 + 5, Ui.MUTED);
		int dx = x0 + TREE_W + 6, dw = W - TREE_W - 6;
		Ui.panel(g, dx, ty, dw, th);
		Detail selected = details.get(selectedKey);
		if (selected != null) {
			int y = ty + 6;
			Ui.text(g, this.font, Ui.ellipsize(this.font, selected.title(), dw - 12), dx + 6, y, Ui.TEXT);
			Ui.text(g, this.font, Ui.ellipsize(this.font, selected.kind(), dw - 12), dx + 6, y + 10, Ui.MUTED);
			Ui.divider(g, dx + 6, y + 21, dw - 12);
			y += 26;
			for (String line : selected.lines()) {
				if (y > ty + th - 22) {
					break;
				}
				Ui.text(g, this.font, line, dx + 6, y, Ui.TEXT);
				y += 10;
			}
			if (!selected.requirement().isEmpty()) {
				Ui.text(g, this.font, Ui.ellipsize(this.font, selected.requirement(), dw - 12), dx + 6, ty + th - 14, Ui.MUTED);
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
}
