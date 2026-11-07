package com.mopicmp.mosaicary.client.skins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mopicmp.mosaicary.client.compat.GenderFigure;

import org.junit.jupiter.api.Test;

/** A card's picture as the site draws it, and with 3D Skin Layers' cubes and Female Gender Mod's breasts. */
class SkinFigureTest {

	/** A skin solid everywhere a part is drawn, its outer layer drawn all over, and the breasts' faces under it. */
	private static int[] skin(int height) {
		int[] out = new int[64 * height];
		for (int i = 0; i < out.length; i++) out[i] = 0xFF808080;
		return out;
	}

	private static int opaque(int[] picture) {
		int count = 0;
		for (int pixel : picture) if ((pixel >>> 24) > 0) count++;
		return count;
	}

	@Test
	void theCubesStandOutOfTheFlatLayer() {
		int[] flat = SkinFigure.render(skin(64), false);
		int[] cubes = SkinFigure.render(skin(64), false, true, null);
		assertEquals(SkinFigure.SIZE * SkinFigure.SIZE, cubes.length);
		assertTrue(opaque(cubes) > opaque(flat), "cubes " + opaque(cubes) + ", flat " + opaque(flat));
	}

	@Test
	void theBreastsChangeTheChest() {
		GenderFigure.Placement[] breasts = {
			new GenderFigure.Placement(true, 0, 1, -2, (float) Math.toRadians(-28), 0, -0.24f, 1.05f),
			new GenderFigure.Placement(true, 0, 1, -2, (float) Math.toRadians(-28), 0, -0.24f, 1.05f),
		};
		int[] drawing = skin(GenderFigure.PICTURE_HEIGHT);
		for (int y = 64; y < GenderFigure.PICTURE_HEIGHT; y++) for (int x = 0; x < 64; x++) drawing[y * 64 + x] = 0xFFFF0000;
		for (boolean cubes : new boolean[] { false, true }) {
			int[] without = SkinFigure.render(drawing, false, cubes, null);
			int[] with = SkinFigure.render(drawing, false, cubes, breasts);
			int red = 0;
			for (int pixel : with) if (((pixel >> 16) & 0xFF) > ((pixel >> 8) & 0xFF) + 40) red++;
			for (int pixel : without) assertTrue(((pixel >> 16) & 0xFF) <= ((pixel >> 8) & 0xFF) + 40, "red without the breasts");
			assertTrue(red > 100, "the breasts' faces drawn: " + red + " red pixels, cubes " + cubes);
		}
	}

	@Test
	void hiddenBreastsAreNotDrawn() {
		GenderFigure.Placement none = new GenderFigure.Placement(false, 0, 0, 0, 0, 0, 0, 1);
		int[] drawing = skin(GenderFigure.PICTURE_HEIGHT);
		int[] plain = SkinFigure.render(drawing, true, false, null);
		int[] hidden = SkinFigure.render(drawing, true, false, new GenderFigure.Placement[] { none, none });
		assertEquals(java.util.Arrays.hashCode(plain), java.util.Arrays.hashCode(hidden));
	}
}
