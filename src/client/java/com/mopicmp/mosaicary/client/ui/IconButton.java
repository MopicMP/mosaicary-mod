package com.mopicmp.mosaicary.client.ui;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A flat button with an icon, and a short label beside it when there is room.
 *
 * The explanation of what it does goes in the tooltip, never under the button. A
 * disabled button keeps its tooltip, so it can say why it is disabled.
 */
public class IconButton extends AbstractWidget {

	private Supplier<Icon> icon;
	private Component label;
	private final Runnable onPress;
	private int accent = Theme.ACCENT;
	private boolean primary;

	public IconButton(int x, int y, int width, int height, Icon icon, Component label, Component tooltip,
			Runnable onPress) {
		super(x, y, width, height, label == null ? tooltip : label);
		this.icon = () -> icon;
		this.label = label;
		this.onPress = onPress;
		if (tooltip != null) setTooltip(Tooltip.create(tooltip));
	}

	/** A square button with only an icon; the tooltip names it. */
	public static IconButton square(int x, int y, int size, Icon icon, Component tooltip, Runnable onPress) {
		return new IconButton(x, y, size, size, icon, null, tooltip, onPress);
	}

	/** The width a labelled button needs for its icon and label. */
	public static int widthFor(Component label) {
		return Icons.SIZE + 6 + (label == null ? 0 : Minecraft.getInstance().font.width(label) + 6);
	}

	public IconButton icon(Supplier<Icon> icon) {
		this.icon = icon;
		return this;
	}

	public IconButton label(Component label) {
		this.label = label;
		if (label != null) setMessage(label);
		return this;
	}

	public IconButton tooltip(Component tooltip) {
		setTooltip(tooltip == null ? null : Tooltip.create(tooltip));
		return this;
	}

	/** Drawn filled with the accent colour, for the main action of a card. */
	public IconButton primary(boolean primary) {
		this.primary = primary;
		return this;
	}

	public IconButton accent(int accent) {
		this.accent = accent;
		return this;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active) onPress.run();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean on = active;
		boolean hovered = on && isHovered();
		int fill = primary ? (hovered ? Theme.darker(accent, 0.85f) : Theme.ACCENT_DARK) : hovered ? Theme.CARD_HOVER : Theme.CARD;
		int edge = primary ? accent : hovered ? Theme.EDGE_HOVER : Theme.EDGE;
		if (!on) {
			fill = Theme.CARD;
			edge = Theme.EDGE;
		}
		Theme.card(graphics, getX(), getY(), getRight(), getBottom(), fill, edge);
		int ink = !on ? Theme.TEXT_OFF : primary ? Theme.TEXT : hovered ? accent : Theme.TEXT;

		Font font = Minecraft.getInstance().font;
		boolean showLabel = label != null && getWidth() >= widthFor(label);
		int contentWidth = showLabel ? widthFor(label) - 6 : Icons.SIZE;
		int left = getX() + (getWidth() - contentWidth) / 2;
		int iconTop = getY() + (getHeight() - Icons.SIZE) / 2;
		Icons.draw(graphics, icon.get(), left, iconTop, ink);
		if (showLabel) {
			graphics.text(font, label, left + Icons.SIZE + 6, getY() + (getHeight() - font.lineHeight) / 2 + 1, ink, false);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
