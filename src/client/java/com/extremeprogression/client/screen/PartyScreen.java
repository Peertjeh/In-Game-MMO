package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientParty;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.command.PartyCommand;
import com.extremeprogression.network.PartyActionPayload;
import com.extremeprogression.network.PartyPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Party screen (design §16): on the left the party's members (the leader marked, a dot for who is near and
 * earning, so giving the bonus) or the invitations waiting for the player; on the right the players online who are in
 * no party, to invite. Its buttons do what {@code /party} does; the screen follows every change the server sends.
 */
public class PartyScreen extends Screen {
	static final int W = 360, H = 214;
	private static final int ROW = 16, LW = 200;

	private static int opened = 0;

	private final Screen parent;
	private int x0, y0;
	private int seen = -1;
	private int page = 0;

	public PartyScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.party.title"));
		this.parent = parent;
		opened++;
	}

	/** How many times the Party screen was opened (for tests). */
	public static int opened() {
		return opened;
	}

	private static PartyPayload view() {
		PartyPayload p = ClientParty.current();
		return p != null ? p : new PartyPayload(true, 5, 0, 0, List.of(), List.of(), List.of());
	}

	private int py() {
		return y0 + 30;
	}

	private int ph() {
		return H - 30 - 26;
	}

	private int rows() {
		return (ph() - 34) / ROW;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		seen = ClientParty.updates();
		PartyPayload v = view();
		boolean leader = v.members().stream().anyMatch(m -> m.leader() && me(m));
		int y = py() + 18;
		if (v.inParty()) {
			for (PartyPayload.Member m : v.members().subList(0, Math.min(v.members().size(), rows()))) {
				if (leader && !me(m)) {
					button(Component.translatable("gui.extremeprogression.party.lead"), "gui.extremeprogression.party.lead_tip", m.name(), PartyActionPayload.Action.LEADER,
							m.name(), x0 + LW - 52, y, 31);
					button(Component.literal("✕"), "gui.extremeprogression.party.remove_tip", m.name(), PartyActionPayload.Action.KICK, m.name(), x0 + LW - 19, y, 15);
				}
				y += ROW;
			}
		} else {
			for (String from : v.invites().subList(0, Math.min(v.invites().size(), rows()))) {
				button(Component.literal("✓"), "gui.extremeprogression.party.accept_tip", from, PartyActionPayload.Action.ACCEPT, from, x0 + LW - 36, y, 15);
				button(Component.literal("✕"), "gui.extremeprogression.party.decline_tip", from, PartyActionPayload.Action.DECLINE, from, x0 + LW - 19, y, 15);
				y += ROW;
			}
		}
		// The players to invite, a page at a time
		List<String> invitable = v.invitable();
		int pages = Math.max(1, (invitable.size() + rows() - 1) / rows());
		page = Math.max(0, Math.min(page, pages - 1));
		y = py() + 18;
		for (String name : invitable.subList(page * rows(), Math.min(invitable.size(), (page + 1) * rows()))) {
			Button b = Button.builder(Component.translatable("gui.extremeprogression.party.invite"), x -> act(PartyActionPayload.Action.INVITE, name))
					.pos(x0 + W - 52, y).size(48, 15).build();
			b.setTooltip(Tooltip.create(Component.translatable("gui.extremeprogression.party.invite_tip", name)));
			this.addRenderableWidget(b);
			y += ROW;
		}
		if (pages > 1) {
			this.addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).pos(x0 + W - 38, py() + 3).size(16, 12).build());
			this.addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).pos(x0 + W - 20, py() + 3).size(16, 12).build());
		}
		// Along the foot: leave, and back
		int foot = y0 + H - 20;
		if (v.inParty()) {
			this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.party.leave"), b -> act(PartyActionPayload.Action.LEAVE, ""))
					.pos(this.width / 2 - 104, foot).size(100, 20).build());
		}
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(v.inParty() ? this.width / 2 + 4 : this.width / 2 - 50, foot).size(100, 20).build());
	}

	private void button(Component label, String tipKey, String tipArg, PartyActionPayload.Action action, String name, int x, int y, int w) {
		Button b = Button.builder(label, x2 -> act(action, name)).pos(x, y).size(w, 15).build();
		b.setTooltip(Tooltip.create(Component.translatable(tipKey, tipArg)));
		this.addRenderableWidget(b);
	}

	private void turn(int by) {
		page += by;
		rebuild();
	}

	/** Sends a party action to the server (for tests too). */
	public void act(PartyActionPayload.Action action, String name) {
		if (ClientPlayNetworking.canSend(PartyActionPayload.TYPE)) {
			ClientPlayNetworking.send(new PartyActionPayload(action, name));
		}
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void tick() {
		super.tick();
		if (seen != ClientParty.updates()) {
			rebuild();
		}
	}

	private static boolean me(PartyPayload.Member m) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getUUID().equals(m.id());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		PartyPayload v = view();
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.GOLD);
		String summary = !v.enabled() ? Ui.tr("gui.extremeprogression.party.off")
				: v.inParty() ? Ui.tr("gui.extremeprogression.party.summary", v.members().size(), v.maxSize(), PartyCommand.percent(v.bonus()), PartyCommand.percent(v.bonusMax()))
				: Ui.tr("gui.extremeprogression.party.alone");
		Ui.centered(g, this.font, Ui.ellipsize(this.font, summary, W), this.width / 2, y0 + 15, v.bonus() > 0 ? Ui.OK : Ui.MUTED);
		int py = py(), ph = ph();
		Ui.panel(g, x0, py, LW, ph);
		Ui.panel(g, x0 + LW + 4, py, W - LW - 4, ph);
		List<Component> tip = null;
		// Left: the members, or the invitations
		int ix = x0 + 6;
		int y = py + 18;
		if (v.inParty()) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.party.members", v.members().size(), v.maxSize()), ix, py + 5, Ui.GOLD);
			for (PartyPayload.Member m : v.members().subList(0, Math.min(v.members().size(), rows()))) {
				int dot = !m.online() ? Ui.DIM : me(m) ? Ui.TEXT : m.active() ? Ui.OK : m.near() ? Ui.WARN : Ui.MUTED;
				Ui.text(g, this.font, "●", ix, y + 4, dot);
				String power = "✦" + m.total();
				int nameW = x0 + LW - 56 - this.font.width(power) - 4 - (ix + 9);
				String name = Ui.ellipsize(this.font, m.name(), nameW);
				Ui.text(g, this.font, name, ix + 9, y + 4, m.online() ? (me(m) ? Ui.WARN : Ui.TEXT) : Ui.DIM);
				String lead = " · " + Ui.tr("gui.extremeprogression.party.leader");
				if (m.leader() && this.font.width(name + lead) <= nameW) {
					Ui.text(g, this.font, lead, ix + 9 + this.font.width(name), y + 4, Ui.GOLD);
				}
				Ui.right(g, this.font, power, x0 + LW - 56, y + 4, m.online() ? Ui.GOLD : Ui.DIM);
				if (mouseX >= ix && mouseX < x0 + LW - 54 && mouseY >= y && mouseY < y + ROW - 1) {
					tip = memberTip(m);
				}
				y += ROW;
			}
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.party.legend"), LW - 12), ix, py + ph - 12, Ui.DIM);
		} else {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.party.invitations"), ix, py + 5, Ui.GOLD);
			if (v.invites().isEmpty()) {
				g.textWithWordWrap(this.font, Component.translatable("gui.extremeprogression.party.no_invitations"), ix, py + 20, LW - 12, Ui.argb(255, Ui.MUTED));
			}
			for (String from : v.invites().subList(0, Math.min(v.invites().size(), rows()))) {
				Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.party.invites_you", from), LW - 50), ix, y + 4, Ui.TEXT);
				y += ROW;
			}
		}
		// Right: whom to invite
		int rx = x0 + LW + 10;
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.party.invite_header"), rx, py + 5, Ui.GOLD);
		y = py + 18;
		List<String> invitable = v.invitable();
		if (!v.enabled() || v.inParty() && v.members().size() >= v.maxSize() || invitable.isEmpty()) {
			String key = !v.enabled() ? "gui.extremeprogression.party.off" : v.inParty() && v.members().size() >= v.maxSize() ? "gui.extremeprogression.party.full"
					: "gui.extremeprogression.party.nobody";
			g.textWithWordWrap(this.font, Component.translatable(key), rx, y + 2, W - LW - 16, Ui.argb(255, Ui.MUTED));
		} else {
			for (String name : invitable.subList(page * rows(), Math.min(invitable.size(), (page + 1) * rows()))) {
				Ui.text(g, this.font, Ui.ellipsize(this.font, name, W - LW - 70), rx, y + 4, Ui.TEXT);
				y += ROW;
			}
		}
		if (tip != null) {
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	private static List<Component> memberTip(PartyPayload.Member m) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(m.name() + (m.leader() ? " · " + Ui.tr("gui.extremeprogression.party.leader") : "")).withStyle(s -> s.withColor(Ui.GOLD)));
		out.add(Component.literal(Ui.tr("gui.extremeprogression.party.level", m.total(), m.mastery())));
		String state = !m.online() ? "offline" : me(m) ? "you" : m.active() ? "active" : m.near() ? "near" : "far";
		out.add(Component.literal(Ui.tr("gui.extremeprogression.party.state." + state)).withStyle(s -> s.withColor(m.active() ? Ui.OK : Ui.MUTED)));
		return out;
	}

	@Override
	public void onClose() {
		if (parent != null) {
			Minecraft.getInstance().gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
