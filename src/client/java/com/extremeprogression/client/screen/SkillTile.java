package com.extremeprogression.client.screen;

import java.util.function.Consumer;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** One skill on the Character Sheet: icon, name, level and bar. Clicking it opens the skill's screen. */
final class SkillTile extends AbstractButton {
	static final int W = 135, H = 22;

	private final Skill skill;
	private final Consumer<Skill> open;
	/** The tooltip's text as last built, so it is only rebuilt when the numbers change. */
	private String tooltipFor = "";

	SkillTile(int x, int y, Skill skill, Consumer<Skill> open) {
		super(x, y, W, H, Component.translatable(skill.translationKey()));
		this.skill = skill;
		this.open = open;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		open.accept(skill);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		ProgressPayload.Entry e = SkillView.entry(skill);
		boolean mastered = SkillView.mastered(e);
		int x = getX(), y = getY();
		Ui.tile(g, x, y, W, H, isHovered() ? Ui.TEXT : Ui.mix(Ui.INK_RAISED, Ui.TEXT, 0.12));
		g.fakeItem(SkillView.icon(skill), x + 3, y + 3);
		String level = mastered ? "M" + e.masteryRanks() : "Lv " + e.level();
		int levelColor = mastered ? Ui.GOLD : Ui.TEXT;
		Ui.right(g, font, level, x + W - 4, y + 3, levelColor);
		// An opposing skill that costs this one XP: its share, in red, before the level (design §9)
		double penalty = SkillView.opposingPenalty(skill);
		int levelW = font.width(level);
		if (penalty > 0) {
			String cut = "−" + Ui.percent(penalty);
			Ui.right(g, font, cut, x + W - 4 - levelW - 4, y + 3, Ui.DANGER);
			levelW += font.width(cut) + 4;
		}
		String name = Ui.ellipsize(font, Ui.tr(skill.translationKey()), W - 26 - levelW - 6);
		Ui.text(g, font, name, x + 22, y + 3, Ui.TEXT);
		Ui.meter(g, x + 22, y + 14, W - 26, 4, SkillView.fill(e), mastered ? Ui.GOLD : skill.category.accent);
		updateTooltip(e, mastered);
	}

	private void updateTooltip(ProgressPayload.Entry e, boolean mastered) {
		ProgressPayload p = ClientProgress.current();
		String warning = SkillView.opposingWarning(skill);
		String key = e + "|" + (p == null ? "" : p.total() + "," + p.cap()) + "|" + warning;
		if (key.equals(tooltipFor)) {
			return;
		}
		tooltipFor = key;
		MutableComponent tip = Component.translatable(skill.translationKey()).withStyle(s -> s.withColor(skill.category.accent));
		tip.append("\n").append(Component.translatable(skill.category.translationKey()).withStyle(ChatFormatting.GRAY));
		if (p == null) {
			setTooltip(Tooltip.create(tip));
			return;
		}
		if (mastered) {
			tip.append("\n").append(Component.translatable("gui.extremeprogression.tile.mastery", e.masteryRanks(), p.masteryRanksMax()));
			if (e.masteryRanks() < p.masteryRanksMax()) {
				tip.append("\n").append(Component.translatable("gui.extremeprogression.tile.xp", Ui.number(e.masteryXp()), Ui.number(p.masteryXpPerRank()),
						Ui.percent(SkillView.fill(e))).withStyle(ChatFormatting.GRAY));
			}
		} else {
			tip.append("\n").append(Component.translatable("gui.extremeprogression.tile.level", e.level(), p.maxLevel()));
			tip.append("\n").append(Component.translatable("gui.extremeprogression.tile.xp", Ui.number(e.xp()), Ui.number(e.need()),
					Ui.percent(SkillView.fill(e))).withStyle(ChatFormatting.GRAY));
			if (p.total() >= p.cap()) {
				tip.append("\n").append(Component.translatable("gui.extremeprogression.tile.capped", p.cap()).withStyle(ChatFormatting.YELLOW));
			}
		}
		if (warning != null) {
			tip.append("\n").append(Component.literal(warning).withStyle(ChatFormatting.RED));
		}
		tip.append("\n").append(Component.translatable("gui.extremeprogression.tile.open").withStyle(ChatFormatting.DARK_GRAY));
		setTooltip(Tooltip.create(tip));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
