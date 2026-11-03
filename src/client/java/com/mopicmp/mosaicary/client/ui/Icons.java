package com.mopicmp.mosaicary.client.ui;

import com.mopicmp.mosaicary.Mosaicary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws icons from whichever sheet lands one texel on one screen pixel.
 *
 * Every icon is drawn into a 16 x 16 square of GUI units. At GUI scale s that square
 * is 16 s screen pixels, so the sheet with that cell size maps texel to pixel and the
 * game's nearest-neighbour sampling changes nothing. When there is no exact sheet
 * (scale 5 and up), the nearest is used, the larger of two equally near ones, since
 * shrinking loses strokes and enlarging only makes them blocky.
 */
public final class Icons {

	/** The size of an icon in GUI units. */
	public static final int SIZE = 16;

	private static final Identifier[] SHEETS = new Identifier[Icon.CELLS.length];

	private Icons() { }

	/** The cell size to use at a GUI scale. */
	public static int cellFor(int guiScale) {
		int wanted = SIZE * Math.max(1, guiScale);
		int best = Icon.CELLS[0];
		for (int cell : Icon.CELLS) {
			int distance = Math.abs(cell - wanted);
			int bestDistance = Math.abs(best - wanted);
			if (distance < bestDistance || (distance == bestDistance && cell > best)) best = cell;
		}
		return best;
	}

	private static Identifier sheet(int cell) {
		for (int i = 0; i < Icon.CELLS.length; i++) {
			if (Icon.CELLS[i] == cell) {
				if (SHEETS[i] == null) SHEETS[i] = Mosaicary.id(Icon.sheet(cell));
				return SHEETS[i];
			}
		}
		throw new IllegalArgumentException("no sheet with cell " + cell);
	}

	/** Draws the icon tinted; the sheets are white, so the tint is the colour. */
	public static void draw(GuiGraphicsExtractor graphics, Icon icon, int x, int y, int colour) {
		// A whole number before 1.21.6 too, where the game keeps the scale as a double.
		int cell = cellFor((int) Minecraft.getInstance().getWindow().getGuiScale());
		Theme.blit(graphics, sheet(cell), x, y, icon.column * cell, icon.row * cell,
			SIZE, SIZE, cell, cell, Icon.COLUMNS * cell, Icon.ROWS * cell, colour);
	}
}
