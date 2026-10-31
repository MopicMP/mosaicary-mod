package com.mopicmp.mosaicary.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The catalogue's colours and the one shape everything sits in: a flat card.
 *
 * Grey-blue panels with a light blue accent, the palette the site uses. Every piece
 * of text on the catalogue screen is drawn on one of these cards, never on the
 * bare background.
 */
public final class Theme {

	/** How wide a tooltip grows before it goes on to the next line. */
	private static final int TOOLTIP_WIDTH = 200;

	/** Words on the pointer, broken into lines rather than running across the screen. */
	public static void tooltip(GuiGraphicsExtractor graphics, Font font, Component words, int x, int y) {
		graphics.setTooltipForNextFrame(font, font.split(words, TOOLTIP_WIDTH), x, y);
	}

	public static final int SCREEN = 0xE60E1117;
	public static final int CARD = 0xFF1B2028;
	public static final int CARD_HOVER = 0xFF252C37;
	public static final int CARD_PRESSED = 0xFF2E3846;
	public static final int EDGE = 0xFF2E3846;
	public static final int EDGE_HOVER = 0xFF4A5A70;
	public static final int FIELD = 0xFF12161D;
	public static final int TEXT = 0xFFECEFF1;
	public static final int TEXT_DIM = 0xFF9AA5B1;
	public static final int TEXT_OFF = 0xFF5D6874;
	public static final int ACCENT = 0xFF6CC6FF;
	public static final int ACCENT_DARK = 0xFF1E4F73;
	public static final int WARN = 0xFFFFB86B;
	public static final int ERROR = 0xFFFF8A80;

	private Theme() { }

	/** A flat card with a one-pixel edge. */
	public static void card(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int fill, int edge) {
		graphics.fill(x0, y0, x1, y1, edge);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
	}

	public static void card(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		card(graphics, x0, y0, x1, y1, CARD, EDGE);
	}

	/** An opaque colour from 0xRRGGBB. */
	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/** The colour made darker by a factor between 0 and 1, keeping alpha. */
	public static int darker(int argb, float factor) {
		int a = argb >>> 24;
		int r = (int) (((argb >> 16) & 255) * factor);
		int g = (int) (((argb >> 8) & 255) * factor);
		int b = (int) ((argb & 255) * factor);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
