package com.mopicmp.mosaicary.client.skins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Live eyes, as the Fresh Moves resource pack moves them, for the wardrobe's preview.
 *
 * The pack (for Entity Model Features) does not look for the eyes on the face: it draws them
 * from a copy the skin carries in corners the game never shows. This reads that copy and
 * moves the eyes the way the cards on mosaicary.com do (the same timings and the same
 * shapes; the site's {@code shared/freshEyes.ts}, the launcher's {@code freshEyes.ts}), so
 * whoever picks a skin sees what the pack will show in the world. A skin without the copy
 * has nothing to move: no eyes are guessed on the face.
 *
 * Pixels are ARGB, the skin 64 x 64 as the game draws it ({@link SkinPixels#normalise}).
 */
public final class FreshEyes {

	private FreshEyes() { }

	/** Where a skin keeps the copy of its eyes: the face rows they are on, and the copy's corner. */
	private record Cell(int[] rows, int u, int v) { }

	/** The pack's cells (its player.jem): two by one and two by two, at each of four heights. */
	private static final Cell[] CELLS = {
		new Cell(new int[] { 6 }, 4, 7),
		new Cell(new int[] { 5 }, 4, 5),
		new Cell(new int[] { 4 }, 4, 3),
		new Cell(new int[] { 3 }, 4, 1),
		new Cell(new int[] { 5, 6 }, 24, 6),
		new Cell(new int[] { 4, 5 }, 24, 3),
		new Cell(new int[] { 3, 4 }, 28, 6),
		new Cell(new int[] { 2, 3 }, 28, 3),
	};

	/** The face's columns the copy holds: white and pupil of one eye, pupil and white of the other. */
	private static final int[] COLUMNS = { 1, 2, 5, 6 };

	/**
	 * The eyes of a face, ready to move.
	 *
	 * @param rows  the face rows they are on, top first
	 * @param drawn per row, columns 1, 2, 5 and 6 as drawn
	 * @param lids  the lid's colour above each of those columns
	 */
	public record Eyes(int[] rows, int[][] drawn, int[] lids) { }

	/**
	 * The eyes the skin carries copied for Fresh Moves, or null. The cell taken is the one that
	 * copies the face exactly: a skin prepared by hand can have brows or lashes in other cells.
	 */
	public static Eyes read(int[] pixels) {
		if (pixels == null || pixels.length < 64 * 64) return null;
		for (Cell cell : CELLS) {
			int[] lids = new int[COLUMNS.length];
			boolean whole = true;
			for (int i = 0; i < COLUMNS.length && whole; i++) {
				lids[i] = pixels[(cell.v - 1) * 64 + cell.u + i];
				whole = alpha(lids[i]) >= 128;
			}
			int[][] drawn = new int[cell.rows.length][COLUMNS.length];
			for (int j = 0; j < cell.rows.length && whole; j++) {
				for (int i = 0; i < COLUMNS.length && whole; i++) {
					int copy = pixels[(cell.v + j) * 64 + cell.u + i];
					int face = pixels[(8 + cell.rows[j]) * 64 + 8 + COLUMNS[i]];
					whole = alpha(copy) >= 128 && (copy & 0xFFFFFF) == (face & 0xFFFFFF);
					drawn[j][i] = copy;
				}
			}
			if (whole) return new Eyes(cell.rows.clone(), drawn, lids);
		}
		return null;
	}

	private static int alpha(int argb) {
		return argb >>> 24;
	}

	/**
	 * A moment of the eyes: {@code look} from -1 (every pupil to the left) to 1 (to the right),
	 * 0 as drawn; {@code lid}, the part of their height the lid covers.
	 */
	public record Pose(double look, double lid) {
		public static final Pose REST = new Pose(0, 0);
	}

	/** A rectangle painted over the face, in face pixels; fractions half way. */
	public record Patch(double x, double y, double w, double h, int colour) { }

	/**
	 * The eyes at a moment, as rectangles painted in order over the face: the two-pixel white,
	 * the one-pixel pupil sliding over it, at most a pixel, and the lid coming down from above.
	 * As the pack moves them.
	 */
	public static List<Patch> patches(Eyes eyes, Pose pose) {
		double aside = Math.max(-1, Math.min(1, pose.look()));
		List<Patch> out = new ArrayList<>();
		for (int j = 0; j < eyes.rows().length; j++) {
			int row = eyes.rows()[j];
			int[] drawn = eyes.drawn()[j];
			out.add(new Patch(1, row, 2, 1, drawn[0]));
			out.add(new Patch(2 + Math.min(0, aside), row, 1, 1, drawn[1]));
			out.add(new Patch(5, row, 2, 1, drawn[3]));
			out.add(new Patch(5 + Math.max(0, aside), row, 1, 1, drawn[2]));
		}
		double shut = Math.max(0, Math.min(1, pose.lid())) * eyes.rows().length;
		if (shut > 0) {
			for (int i = 0; i < COLUMNS.length; i++) out.add(new Patch(COLUMNS[i], eyes.rows()[0], 1, shut, eyes.lids()[i]));
		}
		return out;
	}

	/** How many parts of a pixel the pupil travels by: eighths. */
	public static final int STEPS = 8;

	/** A moment of a move: when, from its start, in milliseconds, and where the eyes are. */
	public record Key(double at, double look, double lid) { }

	// The cards' timings on mosaicary.com, in milliseconds: slower than the pack's, because up
	// close its speed reads as a jump.
	private static final double BLINK_GO = 100, BLINK_STAY = 60, BLINK_BACK = 150, BLINK_AGAIN = 130;
	private static final double GLANCE_GO = 220, GLANCE_STAY = 1100, GLANCE_ACROSS = 320, GLANCE_BACK = 260;
	/** How far the lids come down while glancing aside, in pixels. */
	private static final double GLANCE_LID = 0.3;
	/** The pause between one move and the next: at least, and how much more at most. */
	public static final double GAP_LEAST = 2200, GAP_MORE = 3600;

	/** A move at random: a blink, sometimes two, or a glance aside and back. */
	public static List<Key> move(int rows, Random random) {
		List<Key> keys = new ArrayList<>();
		keys.add(new Key(0, 0, 0));
		Runnable blink = () -> {
			to(keys, BLINK_GO, 0, 1);
			to(keys, BLINK_STAY, 0, 1);
			to(keys, BLINK_BACK, 0, 0);
		};
		double roll = random.nextDouble();
		if (roll < 0.5) {
			blink.run();
		} else if (roll < 0.62) {
			blink.run();
			to(keys, BLINK_AGAIN, 0, 0);
			blink.run();
		} else {
			int side = random.nextDouble() < 0.5 ? -1 : 1;
			double lid = GLANCE_LID / rows;
			to(keys, GLANCE_GO, side, lid);
			to(keys, GLANCE_STAY * (0.8 + random.nextDouble() * 0.5), side, lid);
			if (random.nextDouble() < 0.4) {
				to(keys, GLANCE_ACROSS, -side, lid);
				to(keys, GLANCE_STAY * (0.6 + random.nextDouble() * 0.4), -side, lid);
			}
			to(keys, GLANCE_BACK, 0, 0);
		}
		return keys;
	}

	private static void to(List<Key> keys, double after, double look, double lid) {
		keys.add(new Key(keys.get(keys.size() - 1).at() + after, look, lid));
	}

	private static double eased(double k) {
		return k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
	}

	/** Where the eyes are at a time within a move. */
	public static Pose at(List<Key> keys, double time) {
		for (int i = 1; i < keys.size(); i++) {
			Key a = keys.get(i - 1);
			Key b = keys.get(i);
			if (time >= b.at()) continue;
			double k = eased((time - a.at()) / (b.at() - a.at()));
			return new Pose(a.look() + (b.look() - a.look()) * k, a.lid() + (b.lid() - a.lid()) * k);
		}
		Key last = keys.get(keys.size() - 1);
		return new Pose(last.look(), last.lid());
	}

	/** How long a move lasts, in milliseconds. */
	public static double length(List<Key> keys) {
		return keys.get(keys.size() - 1).at();
	}

	/** The same for two moments that would be drawn the same, at {@link #STEPS} per pixel. */
	public static long keyOf(Pose pose, int rows) {
		return Math.round(pose.look() * STEPS) * 1000 + Math.round(pose.lid() * rows * STEPS);
	}
}
