package com.mopicmp.mosaicary.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
//#if MC>=12111
import net.minecraft.client.renderer.RenderPipelines;
//#endif

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

	/**
	 * Set by the screenshot run: screens are drawn as if the mouse were far away, so no hover
	 * state or tooltip lands in a screenshot.
	 */
	public static boolean hideMouse;

	private Theme() { }

	/**
	 * Part of a texture drawn into a place on the screen. Before 1.21.6 the game takes the place's
	 * size before the part's corner, and has no pipeline to name.
	 */
	public static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float u, float v,
			int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		//#if MC>=12111
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
		//#else
		//$$ com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		//$$ graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
		//#endif
	}

	/** The same, tinted with an ARGB colour; before 1.21.6 the tint is set around the drawing. */
	public static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float u, float v,
			int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int colour) {
		//#if MC>=12111
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, colour);
		//#else
		//$$ graphics.setColor((colour >> 16 & 0xFF) / 255f, (colour >> 8 & 0xFF) / 255f, (colour & 0xFF) / 255f, (colour >>> 24) / 255f);
		//$$ blit(graphics, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
		//$$ graphics.setColor(1, 1, 1, 1);
		//#endif
	}

	/**
	 * Whatever is drawn next goes over everything drawn so far. Before 1.21.6 the screen is drawn
	 * straight away rather than in layers, so what is drawn next is already on top, except where a
	 * figure drawn in depth would hide it: the depth is cleared.
	 */
	public static void nextLayer(GuiGraphicsExtractor graphics) {
		//#if MC>=12111
		graphics.nextStratum();
		//#else
		//$$ graphics.flush();
		//$$ com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
		//#endif
	}

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
