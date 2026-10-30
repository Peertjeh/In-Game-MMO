package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.network.TreePayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.tree.TreeRules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The confirmation before a respec (design §4, §6): exactly what it costs, worked out with the same rules the server
 * uses, and when the next one will be possible. Nothing happens until the player confirms.
 */
public class RespecScreen extends Screen {
	private static final int W = 300, H = 132;

	private final Skill skill;
	private final Screen parent;
	private int x0, y0;
	private Button confirm;

	public RespecScreen(Skill skill, Screen parent) {
		super(Component.translatable("gui.extremeprogression.respec.title", Component.translatable(skill.translationKey())));
		this.skill = skill;
		this.parent = parent;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = (this.height - H) / 2;
		confirm = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.respec.confirm"), b -> {
			SkillScreen.send(new TreePayload(TreePayload.Action.RESPEC, skill.id, ""));
			this.onClose();
		}).pos(this.width / 2 - 104, y0 + H - 26).size(100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
				.pos(this.width / 2 + 4, y0 + H - 26).size(100, 20).build());
	}

	@Override
	public void tick() {
		confirm.active = ClientProgress.respecWait(skill.id) == 0;
	}

	/** What the respec would do, line by line. */
	private List<String> lines() {
		PlayerProgress p = ClientProgress.asProgress();
		int before = p.get(skill).level;
		int points = TreeRules.spent(p.get(skill));
		TreeRules.respec(p, skill, 0, false);
		int after = p.get(skill).level;
		List<String> out = new ArrayList<>();
		int width = W - 16;
		out.addAll(Ui.wrap(this.font, Ui.tr("gui.extremeprogression.respec.loss", Math.round(Config.RESPEC_XP_SHARE * 100), before, after), width));
		out.addAll(Ui.wrap(this.font, Ui.tr("gui.extremeprogression.respec.freed", points), width));
		long wait = ClientProgress.respecWait(skill.id);
		if (wait > 0) {
			long hours = (wait + 3_599_999) / 3_600_000;
			out.addAll(Ui.wrap(this.font, Ui.tr("gui.extremeprogression.respec.waiting", hours / 24, hours % 24), width));
		} else {
			out.addAll(Ui.wrap(this.font, Ui.tr("gui.extremeprogression.respec.wait", Ui.tr(skill.translationKey()), Math.round(Config.RESPEC_DAYS)), width));
		}
		return out;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.panel(g, x0, y0, W, H);
		Ui.text(g, this.font, this.title.getString(), x0 + 8, y0 + 8, Ui.DANGER);
		Ui.divider(g, x0 + 8, y0 + 20, W - 16);
		int y = y0 + 26;
		for (String line : lines()) {
			if (y > y0 + H - 36) {
				break;
			}
			Ui.text(g, this.font, line, x0 + 8, y, Ui.TEXT);
			y += 10;
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	public boolean isPauseScreen() {
		return false;
	}
}
