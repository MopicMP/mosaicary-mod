package com.mopicmp.mosaicary.client.skins;

/**
 * A skin's pixels, made into what the game itself draws, and the arm width read off them.
 *
 * The game does two things to every skin it downloads before drawing it. A skin in the
 * layout from before 1.8 (64 x 32) is copied into the modern 64 x 64 one, the left arm and
 * leg mirrored from the right ones. And the base layer is made solid, so that see-through
 * places come out black. With this mod they stay as drawn, in the game's download too
 * ({@code SkinTextureDownloaderMixin}), so only the first of the two happens here: a skin
 * shown before it has gone through Mojang looks exactly as it will once it has.
 *
 * Pixels are ARGB ints, row by row.
 */
public final class SkinPixels {

	private SkinPixels() { }

	/**
	 * ARGB as ABGR and back: red and blue change places. The game's pictures keep their pixels as
	 * ABGR before 1.21.2; 1.20.1 has nothing of its own to turn one into the other.
	 */
	public static int swapRedBlue(int colour) {
		return (colour & 0xFF00FF00) | ((colour & 0xFF) << 16) | ((colour >>> 16) & 0xFF);
	}

	/**
	 * The skin as the game draws it, 64 x 64; null for any size the game would refuse
	 * (anything but 64 x 64 and 64 x 32).
	 */
	public static int[] normalise(int[] pixels, int width, int height) {
		if (width != 64 || (height != 64 && height != 32) || pixels.length < width * height) return null;
		int[] out = new int[64 * 64];
		System.arraycopy(pixels, 0, out, 0, 64 * height);
		boolean legacy = height == 32;
		if (legacy) {
			// The left leg and arm, mirrored from the right ones, face by face.
			copy(out, 4, 16, 16, 32, 4, 4);
			copy(out, 8, 16, 16, 32, 4, 4);
			copy(out, 0, 20, 24, 32, 4, 12);
			copy(out, 4, 20, 16, 32, 4, 12);
			copy(out, 8, 20, 8, 32, 4, 12);
			copy(out, 12, 20, 16, 32, 4, 12);
			copy(out, 44, 16, -8, 32, 4, 4);
			copy(out, 48, 16, -8, 32, 4, 4);
			copy(out, 40, 20, 0, 32, 4, 12);
			copy(out, 44, 20, -8, 32, 4, 12);
			copy(out, 48, 20, -16, 32, 4, 12);
			copy(out, 52, 20, -8, 32, 4, 12);
		}
		if (legacy) clearHatIfSolid(out);
		clearPackCorners(out);
		return out;
	}

	/** Copies a block to an offset, mirrored left to right, as the game does for the old layout. */
	private static void copy(int[] image, int x, int y, int dx, int dy, int w, int h) {
		for (int row = 0; row < h; row++) {
			for (int column = 0; column < w; column++) {
				int from = (y + row) * 64 + (x + column);
				int to = (y + row + dy) * 64 + (x + dx + w - 1 - column);
				image[to] = image[from];
			}
		}
	}

	/** The corners of the sheet that no part of the body draws, each 8 by 8, where the Fresh Moves pack reads its eyes and brows. */
	public static final int[][] CORNERS = { { 0, 0 }, { 24, 0 }, { 32, 0 }, { 56, 0 } };

	/** A corner with more solid pixels than this is a filled background, not something drawn for the pack. */
	public static final int CORNER_DRAWN_MOST = 32;

	/**
	 * Empties the corners the Fresh Moves pack reads, as the pack puts whatever they hold in front
	 * of the face, usually as black blocks where the eyes are. A skin that carries a copy of its
	 * eyes there ({@link FreshEyes#read}) keeps them, but for a corner filled solid: some editors
	 * save a skin with a colour behind everything, and of the eighteen skins the pack's author
	 * prepared none fills more than 16 pixels of one. Any other skin has them emptied, whatever they
	 * hold: a background, an author's signature, a pattern, cut-offs of hair; the game itself never
	 * draws them. Of 1429 skins found on players, 109 held something there the pack would put on the
	 * face; all eighteen of the author's are read as carrying eyes.
	 *
	 * Pixels may be ARGB or ABGR: only alpha is read, and colours are compared with each other.
	 *
	 * @return whether anything was emptied
	 */
	public static boolean clearPackCorners(int[] image) {
		boolean eyes = FreshEyes.read(image) != null;
		boolean cleared = false;
		for (int[] corner : CORNERS) {
			int solid = 0;
			int seen = 0;
			for (int y = corner[1]; y < corner[1] + 8; y++) {
				for (int x = corner[0]; x < corner[0] + 8; x++) {
					int alpha = image[y * 64 + x] >>> 24;
					if (alpha >= 128) solid++;
					if (alpha != 0) seen++;
				}
			}
			if (seen == 0 || eyes && solid <= CORNER_DRAWN_MOST) continue;
			cleared = true;
			for (int y = corner[1]; y < corner[1] + 8; y++) {
				for (int x = corner[0]; x < corner[0] + 8; x++) image[y * 64 + x] = 0;
			}
		}
		return cleared;
	}

	/**
	 * An old skin whose hat area is filled solid everywhere had no hat at all: the old
	 * editors filled the unused area with a colour. The game clears it, and so does this.
	 */
	private static void clearHatIfSolid(int[] image) {
		for (int y = 0; y < 32; y++) {
			for (int x = 32; x < 64; x++) {
				if ((image[y * 64 + x] >>> 24) < 128) return;
			}
		}
		for (int y = 0; y < 32; y++) {
			for (int x = 32; x < 64; x++) image[y * 64 + x] &= 0x00FFFFFF;
		}
	}

	/** Half see-through in the outer layer, as the site counts it: fainter is nothing, stronger is solid. */
	private static final int HALF_SEEN_FROM = 26;
	private static final int HALF_SEEN_TO = 230;

	/** So many such pixels make a skin one that looks different: one or two are a slip of the pen. */
	private static final int SEEN_OTHERWISE = 3;

	/** Each part of the body by its size and where its base and outer layers are on the texture. */
	private static final int[][] PARTS = {
		{ 8, 8, 8, 0, 0, 32, 0 },
		{ 8, 12, 4, 16, 16, 16, 32 },
		{ 4, 12, 4, 0, 16, 0, 32 },
		{ 4, 12, 4, 16, 48, 0, 48 },
	};

	/** The arms, whose width depends on the body. */
	private static final int[][] ARMS = { { 40, 16, 40, 32 }, { 32, 48, 48, 48 } };

	/**
	 * Whether players without this mod see the skin otherwise than it is drawn: see-through places
	 * in its base layer, or half see-through pixels in the outer one. By the rule the site marks
	 * skins with ({@code site/shared/skin.ts}, {@code seeThroughOf}): read only on the faces the
	 * body is made of, for the arms it is worn with, as a slim skin read as wide would show holes
	 * that are not there.
	 */
	public static boolean seeThrough(int[] pixels, boolean slim) {
		if (pixels == null || pixels.length < 64 * 64) return false;
		int[] seen = { 0 };
		for (int[] part : PARTS) {
			faces(pixels, part[0], part[1], part[2], part[3], part[4], true, seen);
			faces(pixels, part[0], part[1], part[2], part[5], part[6], false, seen);
		}
		int wide = slim ? 3 : 4;
		for (int[] arm : ARMS) {
			faces(pixels, wide, 12, 4, arm[0], arm[1], true, seen);
			faces(pixels, wide, 12, 4, arm[2], arm[3], false, seen);
		}
		return seen[0] >= SEEN_OTHERWISE;
	}

	/** Counts the pixels that show otherwise on the six faces of a box, by Minecraft's box UV. */
	private static void faces(int[] pixels, int w, int h, int d, int u, int v, boolean base, int[] seen) {
		int[][] rects = {
			{ u + d, v + d, w, h }, { u + d + w, v + d, d, h }, { u + 2 * d + w, v + d, w, h }, { u, v + d, d, h },
			{ u + d, v, w, d }, { u + d + w, v, w, d },
		};
		for (int[] rect : rects) {
			for (int y = rect[1]; y < rect[1] + rect[3]; y++) {
				for (int x = rect[0]; x < rect[0] + rect[2]; x++) {
					int alpha = pixels[y * 64 + x] >>> 24;
					if (base ? alpha < 255 : alpha >= HALF_SEEN_FROM && alpha < HALF_SEEN_TO) seen[0]++;
				}
			}
		}
	}

	/** Anything under this counts as nothing drawn: antialiasing leaves stray low alphas. */
	private static final int CLEAR = 8;

	/** Patches the slim layout never uses: the far side of each arm's back face and underside. */
	private static final int[][] ONLY_WIDE = { { 54, 20, 2, 12 }, { 50, 16, 2, 4 }, { 46, 52, 2, 12 }, { 42, 48, 2, 4 } };

	/** Where the right arm is surely drawn, if it is drawn at all. */
	private static final int[] ARM_FRONT = { 44, 20, 4, 12 };

	/**
	 * Which arms a skin was drawn for, by the rule the site reads uploads with
	 * ({@code site/shared/skinShape.ts}): the slim layout never touches four small patches
	 * the wide one fills, so all four blank means slim. A blank arm, or the old layout,
	 * tells nothing and is classic, the game's default.
	 *
	 * @return {@code classic} or {@code slim}
	 */
	public static String armsOf(int[] pixels, int width, int height) {
		if (width != 64 || height != 64 || pixels.length < 64 * 64) return "classic";
		if (!anyInk(pixels, ARM_FRONT)) return "classic";
		for (int[] patch : ONLY_WIDE) if (anyInk(pixels, patch)) return "classic";
		return "slim";
	}

	private static boolean anyInk(int[] pixels, int[] patch) {
		for (int y = patch[1]; y < patch[1] + patch[3]; y++) {
			for (int x = patch[0]; x < patch[0] + patch[2]; x++) {
				if ((pixels[y * 64 + x] >>> 24) >= CLEAR) return true;
			}
		}
		return false;
	}

	/** The width and height a PNG declares in its header, or null when it is not a PNG. */
	public static int[] pngSize(byte[] bytes) {
		if (bytes == null || bytes.length < 24) return null;
		int[] signature = { 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
		for (int i = 0; i < signature.length; i++) if ((bytes[i] & 0xFF) != signature[i]) return null;
		int width = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
		int height = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
		return new int[] { width, height };
	}
}
