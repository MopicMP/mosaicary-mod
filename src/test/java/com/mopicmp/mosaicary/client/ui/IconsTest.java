package com.mopicmp.mosaicary.client.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

/** Which icon sheet is used at which GUI scale, and that every sheet is where the enum says. */
class IconsTest {

	@Test
	void sheetCellMatchesTheScreenPixels() {
		assertArrayEquals(new int[] { 16, 32, 48, 64 }, Icon.CELLS);
		assertEquals(16, Icons.cellFor(1));
		assertEquals(32, Icons.cellFor(2));
		assertEquals(48, Icons.cellFor(3));
		assertEquals(64, Icons.cellFor(4));
		// No 80 px sheet: the nearest one, 64, is used.
		assertEquals(64, Icons.cellFor(5));
		assertEquals(64, Icons.cellFor(8));
	}

	@Test
	void everySheetHasTheLayoutTheEnumDescribes() throws Exception {
		for (int cell : Icon.CELLS) {
			try (InputStream in = IconsTest.class.getResourceAsStream("/assets/mosaicary/" + Icon.sheet(cell))) {
				assertNotNull(in, "missing sheet " + cell);
				BufferedImage sheet = ImageIO.read(in);
				assertEquals(Icon.COLUMNS * cell, sheet.getWidth());
				assertEquals(Icon.ROWS * cell, sheet.getHeight());
				// Every icon's cell has something drawn in it.
				for (Icon icon : Icon.values()) {
					int ink = 0;
					for (int y = 0; y < cell; y++) {
						for (int x = 0; x < cell; x++) {
							if ((sheet.getRGB(icon.column * cell + x, icon.row * cell + y) >>> 24) > 128) ink++;
						}
					}
					// Half a cell width of solid pixels: the smallest icon, a chevron, has about that.
					assertTrue(ink > cell / 2, icon + " is empty on the " + cell + " px sheet");
				}
			}
		}
	}
}
