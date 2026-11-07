package com.mopicmp.mosaicary.client.skins;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** A skin made into what the game draws, and its arms read off its pixels. */
class SkinPixelsTest {

	private static int[] blank(int height) {
		return new int[64 * height];
	}

	@Test
	void sizesTheGameRefusesGiveNothing() {
		assertNull(SkinPixels.normalise(new int[128 * 128], 128, 128));
		assertNull(SkinPixels.normalise(new int[64 * 48], 64, 48));
	}

	@Test
	void everyLayerStaysAsDrawn() {
		int[] skin = blank(64);
		skin[8 * 64 + 8] = 0x10FF0000;   // head front, base layer, nearly clear
		skin[8 * 64 + 40] = 0x10FF0000;  // hat front, outer layer
		int[] out = SkinPixels.normalise(skin, 64, 64);
		assertEquals(0x10FF0000, out[8 * 64 + 8]);
		assertEquals(0x10FF0000, out[8 * 64 + 40]);
		assertEquals(0x00000000, out[0]);
	}

	@Test
	void aCornerFilledWithABackgroundIsEmptied() {
		int[] skin = blank(64);
		for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) skin[y * 64 + x] = 0xFF000000;
		skin[12 * 64 + 12] = 0xFF123456; // the face stays
		int[] out = SkinPixels.normalise(skin, 64, 64);
		assertEquals(0, out[0]);
		assertEquals(0, out[7 * 64 + 7]);
		assertEquals(0xFF123456, out[12 * 64 + 12]);
	}

	private static final int WHITE = 0xFFFFFFFF;
	private static final int PUPIL = 0xFF223344;
	private static final int LID = 0xFFC08060;

	/**
	 * Eyes on face row 6, white and pupil in columns 1, 2, 5 and 6, with their copy for Fresh Moves
	 * in the corner the pack reads them from: a cell at (4, 7), the lid's colour a row above.
	 */
	private static int[] withEyes() {
		int[] skin = blank(64);
		int[] drawn = { WHITE, PUPIL, PUPIL, WHITE };
		int[] columns = { 1, 2, 5, 6 };
		for (int i = 0; i < 4; i++) {
			skin[14 * 64 + 8 + columns[i]] = drawn[i];
			skin[7 * 64 + 4 + i] = drawn[i];
			skin[6 * 64 + 4 + i] = LID;
		}
		return skin;
	}

	@Test
	void whatIsDrawnForThePackInACornerStays() {
		int[] skin = withEyes();
		// A skin prepared by hand can have more for the pack in the other corners, brows or lashes.
		skin[2 * 64 + 25] = 0xFF654321;
		int[] out = SkinPixels.normalise(skin, 64, 64);
		assertEquals(WHITE, out[7 * 64 + 4]);
		assertEquals(PUPIL, out[7 * 64 + 5]);
		assertEquals(LID, out[6 * 64 + 4]);
		assertEquals(0xFF654321, out[2 * 64 + 25]);
		assertEquals(false, SkinPixels.clearPackCorners(out));
	}

	@Test
	void aSignatureInACornerIsEmptied() {
		// A letter drawn where the pack reads its eyes, with no copy of the eyes on the skin: the
		// pack would draw it over the face as black eyes.
		int[] skin = blank(64);
		for (int y = 0; y < 8; y++) skin[y * 64 + 30] = 0xFF000000;
		skin[13 * 64 + 9] = 0xFF070A22; // the face stays
		int[] out = SkinPixels.normalise(skin, 64, 64);
		for (int y = 0; y < 8; y++) assertEquals(0, out[y * 64 + 30]);
		assertEquals(0xFF070A22, out[13 * 64 + 9]);
	}

	@Test
	void aCornerFilledWithABackgroundIsEmptiedBesideACopyOfTheEyes() {
		int[] skin = withEyes();
		for (int y = 0; y < 8; y++) for (int x = 56; x < 64; x++) skin[y * 64 + x] = 0xFF000000;
		int[] out = SkinPixels.normalise(skin, 64, 64);
		assertEquals(0, out[56]);
		assertEquals(WHITE, out[7 * 64 + 4]);
	}

	/** A skin solid wherever the body is drawn and clear everywhere else. */
	private static int[] solidBody(boolean slim) {
		int[] skin = blank(64);
		int[][] base = { { 8, 0, 16, 8 }, { 0, 8, 32, 8 }, { 20, 16, 16, 4 }, { 16, 20, 24, 12 }, { 4, 16, 8, 4 }, { 0, 20, 16, 12 },
			{ 20, 48, 8, 4 }, { 16, 52, 16, 12 } };
		for (int[] r : base) for (int y = r[1]; y < r[1] + r[3]; y++) for (int x = r[0]; x < r[0] + r[2]; x++) skin[y * 64 + x] = 0xFF808080;
		int wide = slim ? 3 : 4;
		for (int[] uv : new int[][] { { 40, 16 }, { 32, 48 } }) {
			for (int x = uv[0] + 4; x < uv[0] + 4 + 2 * wide; x++) for (int y = uv[1]; y < uv[1] + 4; y++) skin[y * 64 + x] = 0xFF808080;
			for (int x = uv[0]; x < uv[0] + 8 + 2 * wide; x++) for (int y = uv[1] + 4; y < uv[1] + 16; y++) skin[y * 64 + x] = 0xFF808080;
		}
		return skin;
	}

	@Test
	void seeThroughIsReadOnTheBodyOnly() {
		int[] skin = solidBody(false);
		// Solid body, empty outer layer and corners, a copy of the eyes in a corner: nothing to warn about.
		skin[7 * 64 + 4] = 0xFF123456;
		assertEquals(false, SkinPixels.seeThrough(skin, false));
		// A hole in the base layer of the chest.
		skin[24 * 64 + 22] = 0;
		skin[24 * 64 + 23] = 0;
		assertEquals(false, SkinPixels.seeThrough(skin, false), "two pixels are a slip of the pen");
		skin[24 * 64 + 24] = 0;
		assertEquals(true, SkinPixels.seeThrough(skin, false));
	}

	@Test
	void seeThroughCountsHalfClearOuterPixelsOnly() {
		int[] skin = solidBody(false);
		for (int x = 40; x < 44; x++) skin[8 * 64 + x] = 0xFF00FF00; // a solid hat: not see-through
		assertEquals(false, SkinPixels.seeThrough(skin, false));
		for (int x = 40; x < 43; x++) skin[9 * 64 + x] = 0x80FF0000; // half clear
		assertEquals(true, SkinPixels.seeThrough(skin, false));
	}

	@Test
	void aSlimSkinHasNoHolesInColumnsItsArmsDoNotUse() {
		int[] slim = solidBody(true);
		assertEquals(false, SkinPixels.seeThrough(slim, true));
		assertEquals(true, SkinPixels.seeThrough(slim, false));
	}

	@Test
	void theOldLayoutGetsItsLeftLegMirroredFromTheRight() {
		int[] skin = blank(32);
		// The right leg's front face is x 4..7, y 20..31; one marked column at its left edge.
		for (int y = 20; y < 32; y++) skin[y * 64 + 4] = 0xFF00FF00;
		int[] out = SkinPixels.normalise(skin, 64, 32);
		// The left leg's front (x 20..23, y 52..63) is the right one mirrored: the mark at its right edge.
		for (int y = 52; y < 64; y++) {
			assertEquals(0xFF00FF00, out[y * 64 + 23]);
			assertEquals(0x00000000, out[y * 64 + 20]);
		}
	}

	@Test
	void anOldSkinWithASolidHatAreaHasNoHat() {
		int[] skin = blank(32);
		for (int y = 0; y < 32; y++) for (int x = 32; x < 64; x++) skin[y * 64 + x] = 0xFF123456;
		int[] out = SkinPixels.normalise(skin, 64, 32);
		assertEquals(0x00123456, out[8 * 64 + 40]);
	}

	@Test
	void armsAreReadAsTheSiteReadsThem() {
		int[] slim = blank(64);
		for (int y = 20; y < 32; y++) for (int x = 44; x < 47; x++) slim[y * 64 + x] = 0xFFFFFFFF;
		assertEquals("slim", SkinPixels.armsOf(slim, 64, 64));
		int[] wide = slim.clone();
		wide[25 * 64 + 54] = 0xFFFFFFFF;
		assertEquals("classic", SkinPixels.armsOf(wide, 64, 64));
		// A blank arm tells nothing, and the old layout is always classic.
		assertEquals("classic", SkinPixels.armsOf(blank(64), 64, 64));
		assertEquals("classic", SkinPixels.armsOf(blank(32), 64, 32));
	}

	@Test
	void thePngHeaderGivesTheSize() {
		byte[] header = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 64, 0, 0, 0, 32 };
		assertArrayEquals(new int[] { 64, 32 }, SkinPixels.pngSize(header));
		header[1] = 'X';
		assertNull(SkinPixels.pngSize(header));
	}
}
