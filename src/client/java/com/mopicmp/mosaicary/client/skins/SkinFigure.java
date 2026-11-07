package com.mopicmp.mosaicary.client.skins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mopicmp.mosaicary.client.compat.GenderFigure;

/**
 * A skin worn, mid-stride, as the site's cards draw it: the picture the site makes of every skin
 * it has ({@code site/shared/skinRender.ts}, {@code render.png}), made here for those it does not
 * have, a file of the player's own or a texture known only by its hash. So a shelf of them looks
 * the same whichever way each one came.
 *
 * Twelve boxes, turned to show the figure's right side and looked at from a little above, without
 * perspective, the farthest first, lit from above and in front. Each pixel of the picture asks
 * each face turned to the viewer which pixel of the skin lands on it, on a grid three times finer
 * than the picture and averaged down, so the edges come out smooth and the pixels stay square.
 * The game's own 3D view draws one picture at a time, and could not fill a shelf.
 *
 * What other mods add to a player can be drawn too: the outer layer as 3D Skin Layers builds it,
 * a cube a pixel deep on the base for each pixel drawn, and Female Gender Mod's breasts, two boxes
 * under the chest placed by its own reckoning (GenderFigure), from a picture with their faces laid
 * out under the skin.
 *
 * Pure arithmetic, safe off the client thread. Pixels are ARGB, row by row.
 */
public final class SkinFigure {

	private SkinFigure() { }

	/** How wide and tall the picture is. */
	public static final int SIZE = 256;

	private static final int FINE = 3;
	private static final double TURN = -30;
	private static final double PITCH = 20;
	private static final double LEG_SWING = 22;
	private static final double ARM_SWING = 26;
	private static final double ARM_SPREAD = 3;
	/** How much larger than itself a face of the base is drawn, so that the seams do not show the background. */
	private static final double SEAM = 0.01;
	private static final double[] LIGHT = normal(new double[] { 0.3, 0.75, 0.6 });

	private static final double YAW_COS = Math.cos(Math.toRadians(TURN));
	private static final double YAW_SIN = Math.sin(Math.toRadians(TURN));
	private static final double PITCH_COS = Math.cos(Math.toRadians(PITCH));
	private static final double PITCH_SIN = Math.sin(Math.toRadians(PITCH));

	/** A rectangle of the texture, where its top left corner lands in the model and where its two edges point. */
	private record Face(int x, int y, int w, int h, double[] at, double[] across, double[] down) { }

	/**
	 * One box: its faces, base and outer, and how it is placed: a point of it lands at {@code turn}
	 * times the point, plus {@code shift}. A limb turns about its joint; the breasts as their mod turns them.
	 */
	private static final class Part {
		final double[] turn;
		final double[] shift;
		final double[] layerFrom;
		final double[] layerTo;
		final List<Face> base;
		final List<Face> outer;
		final double depth;

		Part(double[] from, double[] to, int[] baseUv, int[] outerUv, double grow, double[] turn, double[] shift) {
			this.turn = turn;
			this.shift = shift;
			int[] size = { (int) Math.round(to[0] - from[0]), (int) Math.round(to[1] - from[1]), (int) Math.round(to[2] - from[2]) };
			layerFrom = new double[] { from[0] - grow, from[1] - grow, from[2] - grow };
			layerTo = new double[] { to[0] + grow, to[1] + grow, to[2] + grow };
			base = faces(from, to, baseUv, size);
			outer = outerUv == null ? List.of() : faces(layerFrom, layerTo, outerUv, size);
			depth = seen(new double[] { (from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2 })[2];
		}

		/** A limb, turned about its joint: forward about x first, then out from the body about z. */
		static Part limb(double[] from, double[] to, int[] baseUv, int[] outerUv, double grow, double[] pivot, double swing, double spread) {
			double forward = -swing * Math.PI / 180;
			double out = (pivot[0] < 0 ? -spread : spread) * Math.PI / 180;
			double[] turn = times(about(2, out), about(0, forward));
			double[] moved = apply(turn, pivot);
			return new Part(from, to, baseUv, outerUv, grow, turn, new double[] { pivot[0] - moved[0], pivot[1] - moved[1], pivot[2] - moved[2] });
		}

		double[] seen(double[] point) {
			double[] placed = apply(turn, point);
			return camera(new double[] { placed[0] + shift[0], placed[1] + shift[1], placed[2] + shift[2] });
		}

		double[] turned(double[] direction) {
			return camera(apply(turn, direction));
		}
	}

	/** A turn by {@code radians} about axis 0 (x), 1 (y) or 2 (z), as a matrix of nine, row by row. */
	private static double[] about(int axis, double radians) {
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		return switch (axis) {
			case 0 -> new double[] { 1, 0, 0, 0, c, -s, 0, s, c };
			case 1 -> new double[] { c, 0, s, 0, 1, 0, -s, 0, c };
			default -> new double[] { c, -s, 0, s, c, 0, 0, 0, 1 };
		};
	}

	private static double[] times(double[] a, double[] b) {
		double[] out = new double[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
		}
		return out;
	}

	private static double[] apply(double[] m, double[] v) {
		return new double[] { m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2] };
	}

	private static double[] camera(double[] v) {
		double across = v[0] * YAW_COS - v[2] * YAW_SIN;
		double toward = v[0] * YAW_SIN + v[2] * YAW_COS;
		return new double[] { across, v[1] * PITCH_COS - toward * PITCH_SIN, toward * PITCH_COS + v[1] * PITCH_SIN };
	}

	/** The six faces of a box, by Minecraft's box UV: front, the wearer's left, back, right, top and bottom. */
	private static List<Face> faces(double[] from, double[] to, int[] uv, int[] size) {
		int u = uv[0];
		int v = uv[1];
		int w = size[0];
		int h = size[1];
		int d = size[2];
		double wide = to[0] - from[0];
		double tall = to[1] - from[1];
		double deep = to[2] - from[2];
		return List.of(
			new Face(u + d, v + d, w, h, new double[] { from[0], to[1], to[2] }, new double[] { wide, 0, 0 }, new double[] { 0, -tall, 0 }),
			new Face(u + d + w, v + d, d, h, new double[] { to[0], to[1], to[2] }, new double[] { 0, 0, -deep }, new double[] { 0, -tall, 0 }),
			new Face(u + 2 * d + w, v + d, w, h, new double[] { to[0], to[1], from[2] }, new double[] { -wide, 0, 0 }, new double[] { 0, -tall, 0 }),
			new Face(u, v + d, d, h, new double[] { from[0], to[1], from[2] }, new double[] { 0, 0, deep }, new double[] { 0, -tall, 0 }),
			new Face(u + d, v, w, d, new double[] { from[0], to[1], from[2] }, new double[] { wide, 0, 0 }, new double[] { 0, 0, deep }),
			new Face(u + d + w, v, w, d, new double[] { from[0], from[1], to[2] }, new double[] { wide, 0, 0 }, new double[] { 0, 0, -deep }));
	}

	/** The boxes, the farthest first: the left leg and the right arm forward, the other two back. */
	private static List<Part> parts(boolean slim) {
		int arm = slim ? 3 : 4;
		List<Part> out = new ArrayList<>();
		out.add(Part.limb(new double[] { -4, 0, -2 }, new double[] { 0, 12, 2 }, new int[] { 0, 16 }, new int[] { 0, 32 }, 0.25, new double[] { -2, 12, 0 }, -LEG_SWING, 0));
		out.add(Part.limb(new double[] { 0, 0, -2 }, new double[] { 4, 12, 2 }, new int[] { 16, 48 }, new int[] { 0, 48 }, 0.25, new double[] { 2, 12, 0 }, LEG_SWING, 0));
		out.add(Part.limb(new double[] { -4 - arm, 12, -2 }, new double[] { -4, 24, 2 }, new int[] { 40, 16 }, new int[] { 40, 32 }, 0.25, new double[] { -5, 22, 0 }, ARM_SWING, ARM_SPREAD));
		out.add(Part.limb(new double[] { -4, 12, -2 }, new double[] { 4, 24, 2 }, new int[] { 16, 16 }, new int[] { 16, 32 }, 0.25, new double[] { 0, 12, 0 }, 0, 0));
		out.add(Part.limb(new double[] { 4, 12, -2 }, new double[] { 4 + arm, 24, 2 }, new int[] { 32, 48 }, new int[] { 48, 48 }, 0.25, new double[] { 5, 22, 0 }, -ARM_SWING, ARM_SPREAD));
		out.add(Part.limb(new double[] { -4, 24, -4 }, new double[] { 4, 32, 4 }, new int[] { 0, 0 }, new int[] { 32, 0 }, 0.5, new double[] { 0, 24, 0 }, 0, 0));
		out.sort(Comparator.comparingDouble(part -> part.depth));
		return out;
	}

	/** The game's model turns y down and z to the back; the figure here has them up and to the front. */
	private static final double[] FLIP = { 1, 0, 0, 0, -1, 0, 0, 0, -1 };
	/** The body's own place in the game's model, at the neck, in the figure's pixels above the feet. */
	private static final double NECK = 24;

	/**
	 * The parts of one breast placed as Female Gender Mod places it under the body, which stands
	 * straight on a card: the box, and its outer layer, which is a box of its own drawn flat, or the
	 * box's own outer layer for its cubes.
	 */
	private static List<Part> breast(GenderFigure.Placement placed, boolean left, boolean cubes) {
		if (placed == null || !placed.visible()) return List.of();
		int u = left ? GenderFigure.LEFT_U : GenderFigure.RIGHT_U;
		double x0 = left ? -GenderFigure.WIDTH : 0;
		// The box in the game's model is x0 to x0 + 4 across, 0 to 5 down and 0 to 3 back; flipped, as the parts here are.
		double[] from = { x0, -GenderFigure.HEIGHT, -GenderFigure.DEPTH };
		double[] to = { x0 + GenderFigure.WIDTH, 0, 0 };
		double[] turn = times(about(1, placed.yRot()), about(0, placed.xRot()));
		double[] seen = times(FLIP, times(turn, FLIP));
		double[] at = apply(FLIP, new double[] { placed.x(), placed.y(), placed.z() });
		double[] shift = { at[0], at[1] + NECK, at[2] };
		List<Part> out = new ArrayList<>();
		int[] skin = { u, GenderFigure.SKIN_V };
		int[] overlay = { u, GenderFigure.OVERLAY_V };
		out.add(new Part(from, to, skin, cubes ? overlay : null, 0, seen, shift));
		if (!cubes) {
			double grown = placed.wearScale();
			double[] wear = new double[9];
			for (int i = 0; i < 9; i++) wear[i] = seen[i] * grown;
			double[] back = apply(FLIP, apply(turn, new double[] { 0, 0, placed.wearZ() }));
			out.add(new Part(from, to, overlay, null, 0, wear, new double[] { shift[0] + back[0], shift[1] + back[1], shift[2] + back[2] }));
		}
		return out;
	}

	/** The picture of a skin of 64 by 64, as {@link SkinPixels#normalise} gives it. */
	public static int[] render(int[] drawing, boolean slim) {
		return render(drawing, slim, false, null);
	}

	/**
	 * The same with what other mods add: with {@code cubes}, the outer layer as 3D Skin Layers' cubes;
	 * with {@code breasts} (GenderFigure.still, left then right), Female Gender Mod's breasts, the
	 * drawing then 64 by 128 with their faces laid out under the skin (GenderFigure.laidOut).
	 */
	public static int[] render(int[] drawing, boolean slim, boolean cubes, GenderFigure.Placement[] breasts) {
		List<Part> parts = parts(slim);
		int side = SIZE * FINE;

		// The figure fitted by the corners of every box: nine tenths of the picture, in the middle.
		double left = Double.POSITIVE_INFINITY;
		double top = Double.POSITIVE_INFINITY;
		double right = Double.NEGATIVE_INFINITY;
		double bottom = Double.NEGATIVE_INFINITY;
		for (Part part : parts) {
			for (double x : new double[] { part.layerFrom[0], part.layerTo[0] }) {
				for (double y : new double[] { part.layerFrom[1], part.layerTo[1] }) {
					for (double z : new double[] { part.layerFrom[2], part.layerTo[2] }) {
						double[] at = part.seen(new double[] { x, y, z });
						left = Math.min(left, at[0]);
						right = Math.max(right, at[0]);
						top = Math.min(top, -at[1]);
						bottom = Math.max(bottom, -at[1]);
					}
				}
			}
		}
		double room = side * 0.9;
		double scale = Math.min(room / (right - left), room / (bottom - top));
		double offsetX = (side - (right - left) * scale) / 2 - left * scale;
		double offsetY = (side - (bottom - top) * scale) / 2 - top * scale;

		// The breasts come after the body, the farthest first with the rest: the fit is the plain figure's, as on the site.
		List<Part> drawn = new ArrayList<>(parts);
		if (breasts != null && breasts.length == 2) {
			drawn.addAll(breast(breasts[0], true, cubes));
			drawn.addAll(breast(breasts[1], false, cubes));
			drawn.sort(Comparator.comparingDouble(part -> part.depth));
		}
		float[] grid = new float[side * side * 4];
		for (Part part : drawn) {
			if (!cubes) {
				paint(grid, side, drawing, part, part.base, SEAM, scale, offsetX, offsetY);
				paint(grid, side, drawing, part, part.outer, 0, scale, offsetX, offsetY);
				continue;
			}
			// The cubes on the far side first, then the box, then those on the near side, each the farthest first.
			List<Face> far = new ArrayList<>();
			List<Face> near = new ArrayList<>();
			cubes(drawing, part, far, near);
			paint(grid, side, drawing, part, far, SEAM, scale, offsetX, offsetY);
			paint(grid, side, drawing, part, part.base, SEAM, scale, offsetX, offsetY);
			paint(grid, side, drawing, part, near, SEAM, scale, offsetX, offsetY);
		}
		return averaged(grid, side);
	}

	/** How far 3D Skin Layers' cubes stand out of the base, in the figure's pixels. */
	private static final double CUBE_DEPTH = 1;

	/**
	 * The faces of the cubes 3D Skin Layers makes of a part's outer layer: one cube on the base for each
	 * pixel drawn, as deep as {@link #CUBE_DEPTH}, without the sides two cubes share. Into {@code far}
	 * those on the faces turned away from the viewer, into {@code near} the rest, each the farthest first.
	 */
	private static void cubes(int[] drawing, Part part, List<Face> far, List<Face> near) {
		List<double[]> farDepths = new ArrayList<>();
		List<double[]> nearDepths = new ArrayList<>();
		List<Face> farFaces = new ArrayList<>();
		List<Face> nearFaces = new ArrayList<>();
		for (int k = 0; k < part.outer.size() && k < part.base.size(); k++) {
			Face base = part.base.get(k);
			Face layer = part.outer.get(k);
			double[] outward = normal(cross(base.down(), base.across()));
			boolean facing = part.turned(outward)[2] > 1e-6;
			double[] a = scaled(base.across(), 1.0 / base.w());
			double[] d = scaled(base.down(), 1.0 / base.h());
			double[] n = scaled(outward, CUBE_DEPTH);
			for (int j = 0; j < layer.h(); j++) {
				for (int i = 0; i < layer.w(); i++) {
					if (!drawnAt(drawing, layer, i, j)) continue;
					double[] c = plus(base.at(), plus(scaled(base.across(), (double) i / base.w()), scaled(base.down(), (double) j / base.h())));
					int u = layer.x() + i;
					int v = layer.y() + j;
					List<Face> cube = new ArrayList<>();
					cube.add(oriented(u, v, plus(c, n), a, d, outward));
					if (!drawnAt(drawing, layer, i - 1, j)) cube.add(oriented(u, v, c, d, n, scaled(a, -1)));
					if (!drawnAt(drawing, layer, i + 1, j)) cube.add(oriented(u, v, plus(c, a), d, n, a));
					if (!drawnAt(drawing, layer, i, j - 1)) cube.add(oriented(u, v, c, a, n, scaled(d, -1)));
					if (!drawnAt(drawing, layer, i, j + 1)) cube.add(oriented(u, v, plus(c, d), a, n, d));
					double depth = part.seen(plus(c, plus(scaled(a, 0.5), plus(scaled(d, 0.5), scaled(n, 0.5)))))[2];
					for (Face face : cube) {
						(facing ? nearFaces : farFaces).add(face);
						(facing ? nearDepths : farDepths).add(new double[] { depth });
					}
				}
			}
		}
		sortedInto(farFaces, farDepths, far);
		sortedInto(nearFaces, nearDepths, near);
	}

	private static void sortedInto(List<Face> faces, List<double[]> depths, List<Face> into) {
		Integer[] order = new Integer[faces.size()];
		for (int i = 0; i < order.length; i++) order[i] = i;
		java.util.Arrays.sort(order, Comparator.comparingDouble(i -> depths.get(i)[0]));
		for (int i : order) into.add(faces.get(i));
	}

	/** Whether the outer layer has a pixel drawn at (i, j) of a face's rectangle; none outside it. */
	private static boolean drawnAt(int[] drawing, Face layer, int i, int j) {
		if (i < 0 || j < 0 || i >= layer.w() || j >= layer.h()) return false;
		int index = (layer.y() + j) * 64 + layer.x() + i;
		return index < drawing.length && (drawing[index] >>> 24) != 0;
	}

	/** A face of a cube, all of one pixel, its two edges in the order that turns it {@code outward}. */
	private static Face oriented(int u, int v, double[] at, double[] first, double[] second, double[] outward) {
		double[] facing = cross(second, first);
		boolean right = facing[0] * outward[0] + facing[1] * outward[1] + facing[2] * outward[2] >= 0;
		return right ? new Face(u, v, 1, 1, at, first, second) : new Face(u, v, 1, 1, at, second, first);
	}

	private static double[] scaled(double[] v, double by) {
		return new double[] { v[0] * by, v[1] * by, v[2] * by };
	}

	private static void paint(float[] grid, int side, int[] drawing, Part part, List<Face> faces, double bleed, double scale, double offsetX, double offsetY) {
		for (Face face : faces) {
			double[] facing = normal(part.turned(cross(face.down(), face.across())));
			if (facing[2] <= 1e-6) continue;
			double bright = 0.5 + 0.5 * Math.max(0, facing[0] * LIGHT[0] + facing[1] * LIGHT[1] + facing[2] * LIGHT[2]);
			double level = Math.round(bright * 32) / 32.0;
			double[] o = part.seen(face.at());
			double[] a = part.seen(plus(face.at(), face.across()));
			double[] d = part.seen(plus(face.at(), face.down()));
			drawFace(grid, side, drawing, face, (a[0] - o[0]) * scale, (o[1] - a[1]) * scale, (d[0] - o[0]) * scale, (o[1] - d[1]) * scale,
				o[0] * scale + offsetX, -o[1] * scale + offsetY, level, bleed);
		}
	}

	/** Lays a face's rectangle over the grid, which holds colour premultiplied by alpha, by the inverse of its map. */
	private static void drawFace(float[] grid, int side, int[] drawing, Face face, double a, double b, double c, double d, double e, double f, double level, double bleed) {
		double det = a * d - b * c;
		if (Math.abs(det) < 1e-9) return;
		double low = -bleed;
		double high = 1 + bleed;
		double span = 1 + 2 * bleed;
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (double s : new double[] { low, high }) {
			for (double t : new double[] { low, high }) {
				double x = a * s + c * t + e;
				double y = b * s + d * t + f;
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		int x0 = Math.max(0, (int) Math.floor(minX));
		int x1 = Math.min(side - 1, (int) Math.ceil(maxX));
		int y0 = Math.max(0, (int) Math.floor(minY));
		int y1 = Math.min(side - 1, (int) Math.ceil(maxY));
		for (int y = y0; y <= y1; y++) {
			double dy = y + 0.5 - f;
			for (int x = x0; x <= x1; x++) {
				double dx = x + 0.5 - e;
				double s = (d * dx - c * dy) / det;
				double t = (a * dy - b * dx) / det;
				if (s < low || s >= high || t < low || t >= high) continue;
				int u = face.x() + Math.min(face.w() - 1, (int) Math.floor((s - low) / span * face.w()));
				int v = face.y() + Math.min(face.h() - 1, (int) Math.floor((t - low) / span * face.h()));
				int pixel = drawing[v * 64 + u];
				double alpha = (pixel >>> 24) / 255.0;
				if (alpha == 0) continue;
				int to = (y * side + x) * 4;
				double keep = 1 - alpha;
				grid[to] = (float) (((pixel >> 16) & 0xFF) / 255.0 * level * alpha + grid[to] * keep);
				grid[to + 1] = (float) (((pixel >> 8) & 0xFF) / 255.0 * level * alpha + grid[to + 1] * keep);
				grid[to + 2] = (float) ((pixel & 0xFF) / 255.0 * level * alpha + grid[to + 2] * keep);
				grid[to + 3] = (float) (alpha + grid[to + 3] * keep);
			}
		}
	}

	private static int[] averaged(float[] grid, int side) {
		int[] out = new int[SIZE * SIZE];
		int samples = FINE * FINE;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double r = 0;
				double g = 0;
				double b = 0;
				double al = 0;
				for (int sy = 0; sy < FINE; sy++) {
					for (int sx = 0; sx < FINE; sx++) {
						int at = ((y * FINE + sy) * side + x * FINE + sx) * 4;
						r += grid[at];
						g += grid[at + 1];
						b += grid[at + 2];
						al += grid[at + 3];
					}
				}
				if (al == 0) continue;
				out[y * SIZE + x] = (int) Math.round(al / samples * 255) << 24 | (int) Math.round(r / al * 255) << 16
					| (int) Math.round(g / al * 255) << 8 | (int) Math.round(b / al * 255);
			}
		}
		return out;
	}

	private static double[] plus(double[] a, double[] b) {
		return new double[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
	}

	private static double[] normal(double[] v) {
		double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		if (length == 0) length = 1;
		return new double[] { v[0] / length, v[1] / length, v[2] / length };
	}
}
