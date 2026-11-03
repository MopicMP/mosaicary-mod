package com.mopicmp.mosaicary.client.ui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//#if MC>=12111
import net.minecraft.client.input.MouseButtonEvent;
//#endif
import net.minecraft.network.chat.Component;

/**
 * A choice of one value from a list: the current value on a button, the rest in a
 * list that opens under it.
 *
 * A choice among several values is always one of these or a row of icon buttons,
 * never a line of words to pick from, so it takes one row however many values the
 * list grows to. The current value is shortened to fit the button, with dots, and
 * the tooltip names it whole; only a button with no room for words at all shows its
 * icon alone.
 *
 * The open list is drawn and clicked by the screen, above everything else: the
 * screen calls {@link #extractList} last and offers clicks to {@link #clickList}
 * first.
 */
public class Dropdown<T> extends AbstractWidget {

	public record Option<T>(T value, Component label) { }

	private static final int ROW = 14;

	private final Icon icon;
	private final Component name;
	private final List<Option<T>> options;
	private final Consumer<T> onChange;
	private int selected;
	private boolean open;

	public Dropdown(int x, int y, int width, int height, Icon icon, Component name, List<Option<T>> options,
			T current, Consumer<T> onChange) {
		super(x, y, width, height, name);
		this.icon = icon;
		this.name = name;
		this.options = List.copyOf(options);
		this.onChange = onChange;
		select(current);
	}

	/** The width that shows the icon, the longest label and the chevron. */
	public static <T> int widthFor(List<Option<T>> options) {
		Font font = Minecraft.getInstance().font;
		int widest = 0;
		for (Option<T> option : options) widest = Math.max(widest, font.width(option.label()));
		return 4 + Icons.SIZE + 4 + widest + 4 + Icons.SIZE + 2;
	}

	/** The width that shows only the icon and the chevron. */
	public static int narrowWidth() {
		return 4 + Icons.SIZE + Icons.SIZE + 4;
	}

	public void select(T value) {
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).value().equals(value)) selected = i;
		}
		setTooltip(Tooltip.create(Component.empty().append(name).append(": ").append(options.get(selected).label())));
	}

	public T value() {
		return options.get(selected).value();
	}

	public boolean isOpen() {
		return open;
	}

	public void close() {
		open = false;
	}

	//#if MC>=12111
	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
	//#else
	//$$ @Override
	//$$ public void onClick(double mouseX, double mouseY) {
	//#endif
		open = !open;
		// Opened with the chosen value in view.
		if (open) first = Math.max(0, selected - visibleRows() / 2);
	}

	/**
	 * The room for the current value's words: between the icon and the chevron.
	 *
	 * Not "room for the longest value", which is what it used to be. A model with one
	 * long animation name among forty short ones showed no names in any row at all.
	 */
	private int labelRoom() {
		return getWidth() - (4 + Icons.SIZE + 4) - (Icons.SIZE + 2 + 2);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = isHovered() || open;
		Theme.card(graphics, getX(), getY(), getRight(), getBottom(), hovered ? Theme.CARD_HOVER : Theme.CARD,
			open ? Theme.ACCENT : hovered ? Theme.EDGE_HOVER : Theme.EDGE);
		int top = getY() + (getHeight() - Icons.SIZE) / 2;
		int ink = hovered ? Theme.ACCENT : Theme.TEXT;
		Icons.draw(graphics, icon, getX() + 4, top, ink);
		int room = labelRoom();
		if (room >= 16) {
			Font font = Minecraft.getInstance().font;
			String text = options.get(selected).label().getString();
			if (font.width(text) > room) {
				String dots = "...";
				text = font.plainSubstrByWidth(text, Math.max(0, room - font.width(dots))) + dots;
			}
			graphics.text(font, text, getX() + 4 + Icons.SIZE + 4,
				getY() + (getHeight() - font.lineHeight) / 2 + 1, Theme.TEXT, false);
		}
		Icons.draw(graphics, Icon.EXPAND, getRight() - Icons.SIZE - 2, top, Theme.TEXT_DIM);
	}

	private int listWidth() {
		return Math.max(getWidth(), widthFor(options) - Icons.SIZE - 2);
	}

	private int listLeft(int screenWidth) {
		return Math.max(2, Math.min(getX(), screenWidth - listWidth() - 2));
	}

	private static int screenHeight() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	/** How many options fit on the screen at once; a longer list scrolls. */
	private int visibleRows() {
		return Math.max(1, Math.min(options.size(), (screenHeight() - 8) / ROW));
	}

	/** Below the button when it fits, above it when that fits, else as high as it must be. */
	private int listTop() {
		int height = visibleRows() * ROW + 2;
		int below = getBottom() + 1;
		if (below + height <= screenHeight() - 2) return below;
		int above = getY() - 1 - height;
		if (above >= 2) return above;
		return Math.max(2, screenHeight() - 2 - height);
	}

	/** The first option shown, when the list is longer than the screen. */
	private int first;

	/** Draws the open list. Call after everything else on the screen. */
	public void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenWidth) {
		if (!open) return;
		Font font = Minecraft.getInstance().font;
		int left = listLeft(screenWidth);
		int top = listTop();
		int width = listWidth();
		int rows = visibleRows();
		first = Math.max(0, Math.min(first, options.size() - rows));
		Theme.card(graphics, left, top, left + width, top + rows * ROW + 2, Theme.CARD, Theme.ACCENT);
		for (int row = 0; row < rows; row++) {
			int i = first + row;
			int y = top + 1 + row * ROW;
			boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + ROW;
			if (hovered) graphics.fill(left + 1, y, left + width - 1, y + ROW, Theme.CARD_HOVER);
			int ink = i == selected ? Theme.ACCENT : hovered ? Theme.TEXT : Theme.TEXT_DIM;
			if (i == selected) graphics.fill(left + 1, y, left + 3, y + ROW, Theme.ACCENT);
			Component label = options.get(i).label();
			graphics.text(font, label, left + 8, y + (ROW - font.lineHeight) / 2 + 1, ink, false);
		}
		// A thin bar on the right says the list goes on.
		if (rows < options.size()) {
			int track = rows * ROW;
			int thumb = Math.max(6, track * rows / options.size());
			int at = (track - thumb) * first / Math.max(1, options.size() - rows);
			graphics.fill(left + width - 3, top + 1 + at, left + width - 1, top + 1 + at + thumb, Theme.EDGE_HOVER);
		}
	}

	/** Takes a click while the list is open. Returns whether the click was used. */
	public boolean clickList(double mouseX, double mouseY, int screenWidth) {
		if (!open) return false;
		int left = listLeft(screenWidth);
		int top = listTop();
		int width = listWidth();
		int rows = visibleRows();
		if (mouseX >= left && mouseX < left + width && mouseY >= top + 1 && mouseY < top + 1 + rows * ROW) {
			int index = first + (int) ((mouseY - top - 1) / ROW);
			open = false;
			if (index != selected && index < options.size()) {
				selected = index;
				select(options.get(index).value());
				onChange.accept(options.get(index).value());
			}
			return true;
		}
		if (isMouseOver(mouseX, mouseY)) {
			open = false;
			return true;
		}
		open = false;
		return false;
	}

	/**
	 * The middle of an option's row in the open list, or null when the list is shut
	 * or the option is scrolled out of view; for the game test, which clicks it.
	 */
	public double[] optionCenter(T value, int screenWidth) {
		if (!open) return null;
		int rows = visibleRows();
		for (int i = 0; i < options.size(); i++) {
			if (!options.get(i).value().equals(value)) continue;
			if (i < first || i >= first + rows) return null;
			return new double[] { listLeft(screenWidth) + listWidth() / 2.0, listTop() + 1 + (i - first) * ROW + ROW / 2.0 };
		}
		return null;
	}

	/** Scrolls an open list that is longer than the screen. Returns whether the wheel was used. */
	public boolean scrollList(double mouseX, double mouseY, double amount, int screenWidth) {
		if (!open || visibleRows() >= options.size()) return false;
		int left = listLeft(screenWidth);
		if (mouseX < left || mouseX >= left + listWidth()) return false;
		first = Math.max(0, Math.min(options.size() - visibleRows(), first - (int) Math.signum(amount)));
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.empty().append(name).append(": ").append(options.get(selected).label()));
	}
}
