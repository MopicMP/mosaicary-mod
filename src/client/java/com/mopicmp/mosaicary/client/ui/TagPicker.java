package com.mopicmp.mosaicary.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.skins.SkinQuery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
//#if MC>=11700
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//#endif
//#if MC>=12111
import net.minecraft.client.input.MouseButtonEvent;
//#endif
import net.minecraft.network.chat.Component;

/**
 * The tag filter: a button like a dropdown that opens a list of tags with a search line
 * and a box to tick on each, up to {@link SkinQuery#TAGS_MOST} at once.
 *
 * Tags are never laid out as a row of words to click: there are hundreds of them. The
 * list opens under the button like any other dropdown; typing while it is open narrows
 * it, the most used first. Two rows above the tags say whether a skin needs any of the
 * ticked tags or all of them, and clear the ticks.
 *
 * As with {@link Dropdown}, the screen draws the open list last and offers it clicks,
 * keys and the wheel first.
 */
// Before 1.19.3 a widget has no getters for its place, no isHovered and no tooltip (PlacedWidget).
//#if MC>=11903
public final class TagPicker extends AbstractWidget {
//#else
//$$ public final class TagPicker extends com.mopicmp.mosaicary.client.platform.PlacedWidget {
//#endif

	private static final int ROW = 14;
	private static final int WIDTH = 190;
	/** The search line, the any/all row and the clear row above the tags. */
	private static final int HEAD = 3;

	private final Supplier<List<SkinQuery.Tag>> tags;
	private final BiConsumer<List<String>, Boolean> onChange;
	private final List<String> ticked;
	private boolean all;
	private boolean open;
	private String typed = "";
	private int first;

	public TagPicker(int x, int y, int width, int height, Supplier<List<SkinQuery.Tag>> tags, List<String> ticked, boolean all,
			BiConsumer<List<String>, Boolean> onChange) {
		super(x, y, width, height, Component.translatable("mosaicary.skins.filter.tags"));
		this.tags = tags;
		this.ticked = new ArrayList<>(ticked);
		this.all = all;
		this.onChange = onChange;
		refreshTooltip();
	}

	public boolean isOpen() {
		return open;
	}

	public void close() {
		open = false;
		typed = "";
	}

	private void refreshTooltip() {
		Component said = ticked.isEmpty() ? Component.translatable("mosaicary.skins.tags.none")
			: Component.translatable(all ? "mosaicary.skins.tags.all.list" : "mosaicary.skins.tags.any.list", String.join(", ", ticked));
		setTooltip(Tooltip.create(Component.translatable("mosaicary.skins.filter.tags").append(": ").append(said)));
	}

	private Component label() {
		if (ticked.isEmpty()) return Component.translatable("mosaicary.skins.tags.none");
		if (ticked.size() == 1) return Component.literal(ticked.get(0));
		return Component.translatable("mosaicary.skins.tags.count", ticked.size());
	}

	/** The width that shows the icon, a label of a usual length and the chevron. */
	public static int labelledWidth() {
		Font font = Minecraft.getInstance().font;
		return 4 + Icons.SIZE + 4 + Math.max(font.width(Component.translatable("mosaicary.skins.tags.none")), 60) + 4 + Icons.SIZE + 2;
	}

	//#if MC>=12111
	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
	//#else
	//$$ @Override
	//$$ public void onClick(double mouseX, double mouseY) {
	//#endif
		open = !open;
		if (open) {
			first = 0;
			typed = "";
		}
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hovered = isHovered() || open;
		boolean lit = !ticked.isEmpty();
		Theme.card(graphics, getX(), getY(), getRight(), getBottom(), hovered ? Theme.CARD_HOVER : Theme.CARD,
			open || lit ? Theme.ACCENT : hovered ? Theme.EDGE_HOVER : Theme.EDGE);
		int top = getY() + (getHeight() - Icons.SIZE) / 2;
		Icons.draw(graphics, Icon.TAG, getX() + 4, top, hovered || lit ? Theme.ACCENT : Theme.TEXT);
		int room = getWidth() - (4 + Icons.SIZE + 4) - (Icons.SIZE + 4);
		if (room >= 16) {
			Font font = Minecraft.getInstance().font;
			String text = label().getString();
			if (font.width(text) > room) text = font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
			graphics.text(font, text, getX() + 4 + Icons.SIZE + 4, getY() + (getHeight() - font.lineHeight) / 2 + 1, Theme.TEXT, false);
		}
		Icons.draw(graphics, Icon.EXPAND, getRight() - Icons.SIZE - 2, top, Theme.TEXT_DIM);
	}

	/** The tags the search line lets through, the ticked ones first, then the most used. */
	private List<SkinQuery.Tag> listed() {
		List<SkinQuery.Tag> out = new ArrayList<>();
		String needle = typed.toLowerCase(Locale.ROOT);
		for (String tag : ticked) out.add(new SkinQuery.Tag(tag, countOf(tag)));
		for (SkinQuery.Tag tag : tags.get()) {
			if (ticked.contains(tag.tag())) continue;
			if (!needle.isEmpty() && !tag.tag().toLowerCase(Locale.ROOT).contains(needle)) continue;
			out.add(tag);
		}
		return out;
	}

	private int countOf(String tag) {
		for (SkinQuery.Tag one : tags.get()) if (one.tag().equals(tag)) return one.count();
		return 0;
	}

	private static int screenHeight() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	private int listLeft(int screenWidth) {
		int width = Math.max(getWidth(), WIDTH);
		return Math.max(2, Math.min(getX(), screenWidth - width - 2));
	}

	private int listWidth() {
		return Math.max(getWidth(), WIDTH);
	}

	private int rows() {
		int fit = Math.max(1, (screenHeight() - getBottom() - 8) / ROW - HEAD);
		return Math.max(1, Math.min(fit, Math.max(1, listed().size())));
	}

	public void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenWidth) {
		if (!open) return;
		Font font = Minecraft.getInstance().font;
		int left = listLeft(screenWidth);
		int width = listWidth();
		int top = getBottom() + 1;
		List<SkinQuery.Tag> listed = listed();
		int rows = rows();
		first = Math.max(0, Math.min(first, listed.size() - rows));
		int height = (HEAD + rows) * ROW + 2;
		Theme.card(graphics, left, top, left + width, top + height, Theme.CARD, Theme.ACCENT);

		// The search line: what has been typed, or how to search.
		int y = top + 1;
		graphics.fill(left + 1, y, left + width - 1, y + ROW, Theme.FIELD);
		Icons.draw(graphics, Icon.SEARCH, left + 3, y + (ROW - Icons.SIZE) / 2, Theme.TEXT_DIM);
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		String line = typed.isEmpty() ? Component.translatable("mosaicary.skins.tags.type").getString() : typed + (caret ? "_" : "");
		graphics.text(font, line, left + 3 + Icons.SIZE + 3, y + (ROW - font.lineHeight) / 2 + 1,
			typed.isEmpty() ? Theme.TEXT_OFF : Theme.TEXT, false);

		// Any or all.
		y += ROW;
		row(graphics, font, left, y, width, mouseX, mouseY, all ? Icon.CHECK_ON : Icon.CHECK_OFF,
			Component.translatable("mosaicary.skins.tags.all").getString(), ticked.size() > 1 ? Theme.TEXT : Theme.TEXT_OFF);

		// Clearing the ticks.
		y += ROW;
		row(graphics, font, left, y, width, mouseX, mouseY, Icon.CLOSE,
			Component.translatable("mosaicary.skins.tags.clear").getString(), ticked.isEmpty() ? Theme.TEXT_OFF : Theme.TEXT);
		graphics.fill(left + 1, y + ROW - 1, left + width - 1, y + ROW, Theme.EDGE);

		y += ROW;
		if (listed.isEmpty()) {
			Component said = tags.get().isEmpty() ? Component.translatable("mosaicary.skins.tags.loading")
				: Component.translatable("mosaicary.skins.tags.nomatch");
			graphics.text(font, said, left + 8, y + (ROW - font.lineHeight) / 2 + 1, Theme.TEXT_DIM, false);
			return;
		}
		for (int i = 0; i < rows && first + i < listed.size(); i++) {
			SkinQuery.Tag tag = listed.get(first + i);
			boolean on = ticked.contains(tag.tag());
			boolean full = !on && ticked.size() >= SkinQuery.TAGS_MOST;
			String text = tag.tag() + (tag.count() > 0 ? "  " + tag.count() : "");
			row(graphics, font, left, y + i * ROW, width, mouseX, mouseY, on ? Icon.CHECK_ON : Icon.CHECK_OFF, text,
				full ? Theme.TEXT_OFF : on ? Theme.ACCENT : Theme.TEXT_DIM);
		}
		if (rows < listed.size()) {
			int track = rows * ROW;
			int thumb = Math.max(6, track * rows / listed.size());
			int at = (track - thumb) * first / Math.max(1, listed.size() - rows);
			graphics.fill(left + width - 3, y + at, left + width - 1, y + at + thumb, Theme.EDGE_HOVER);
		}
	}

	private static void row(GuiGraphicsExtractor graphics, Font font, int left, int y, int width, int mouseX, int mouseY, Icon icon,
			String text, int ink) {
		boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + ROW;
		if (hovered) graphics.fill(left + 1, y, left + width - 1, y + ROW, Theme.CARD_HOVER);
		Icons.draw(graphics, icon, left + 3, y + (ROW - Icons.SIZE) / 2, ink);
		int room = width - Icons.SIZE - 12;
		if (font.width(text) > room) text = font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
		graphics.text(font, text, left + 3 + Icons.SIZE + 3, y + (ROW - font.lineHeight) / 2 + 1, ink, false);
	}

	/** Takes a click while the list is open. Returns whether the click was used. */
	public boolean clickList(double mouseX, double mouseY, int screenWidth) {
		if (!open) return false;
		int left = listLeft(screenWidth);
		int width = listWidth();
		int top = getBottom() + 1;
		List<SkinQuery.Tag> listed = listed();
		int rows = rows();
		int bottom = top + 1 + (HEAD + rows) * ROW;
		if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= bottom) {
			if (isMouseOver(mouseX, mouseY)) {
				close();
				return true;
			}
			close();
			return false;
		}
		int index = (int) ((mouseY - top - 1) / ROW);
		if (index == 1) {
			if (ticked.size() > 1) {
				all = !all;
				changed();
			}
		} else if (index == 2) {
			if (!ticked.isEmpty()) {
				ticked.clear();
				all = false;
				changed();
			}
		} else if (index >= HEAD) {
			int at = first + index - HEAD;
			if (at < listed.size()) {
				String tag = listed.get(at).tag();
				if (ticked.remove(tag)) {
					if (ticked.size() < 2) all = false;
					changed();
				} else if (ticked.size() < SkinQuery.TAGS_MOST) {
					ticked.add(tag);
					changed();
				}
			}
		}
		return true;
	}

	private void changed() {
		refreshTooltip();
		onChange.accept(List.copyOf(ticked), all);
	}

	public boolean scrollList(double mouseX, double mouseY, double amount, int screenWidth) {
		if (!open) return false;
		int left = listLeft(screenWidth);
		if (mouseX < left || mouseX >= left + listWidth()) return false;
		first = Math.max(0, Math.min(Math.max(0, listed().size() - rows()), first - (int) Math.signum(amount)));
		return true;
	}

	/** Typing narrows the list while it is open. Returns whether the character was taken. */
	public boolean typed(int codepoint) {
		if (!open || Character.isISOControl(codepoint) || typed.length() >= 30) return open;
		typed += new String(Character.toChars(codepoint));
		first = 0;
		return true;
	}

	/** Backspace takes a character back, Escape closes. Returns whether the key (its key code) was taken. */
	public boolean key(int key) {
		if (!open) return false;
		if (key == InputConstants.KEY_BACKSPACE) {
			if (!typed.isEmpty()) typed = typed.substring(0, typed.offsetByCodePoints(typed.length(), -1));
			first = 0;
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			close();
			return true;
		}
		return true;
	}

	//#if MC>=11700
	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("mosaicary.skins.filter.tags").append(": ").append(label()));
	}
	//#else
	//$$ // Before 1.17 a widget is narrated by one message, its label unless it says otherwise.
	//$$ @Override
	//$$ protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
	//$$ 	return new net.minecraft.network.chat.TranslatableComponent("mosaicary.skins.filter.tags").append(": ").append(label());
	//$$ }
	//#endif

	//#if MC<12002
	//$$ /** The widget's right and bottom edges, which 1.20.1 does not give, only its place and size. */
	//$$ private int getRight() {
	//$$ 	return getX() + getWidth();
	//$$ }
	//$$
	//$$ private int getBottom() {
	//$$ 	return getY() + getHeight();
	//$$ }
	//#endif
}
