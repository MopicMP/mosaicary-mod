package com.mopicmp.mosaicary.client.skins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

	/** One box: its faces, base and outer, turned about its joint. */
	private static final class Part {
		final double[] pivot;
		final double swing;
		final double spread;
		final double[] layerFrom;
		final double[] layerTo;
		final List<Face> base;
		final List<Face> outer;
		final double depth;

		Part(double[] from, double[] to, int[] baseUv, int[] outerUv, double grow, double[] pivot, double swing, double spread) {
			this.pivot = pivot;
			this.swing = swing;
			this.spread = spread;
			int[] size = { (int) (to[0] - from[0]), (int) (to[1] - from[1]), (int) (to[2] - from[2]) };
			layerFrom = new double[] { from[0] - grow, from[1] - grow, from[2] - grow };
			layerTo = new double[] { to[0] + grow, to[1] + grow, to[2] + grow };
			base = faces(from, to, baseUv, size);
			outer = faces(layerFrom, layerTo, outerUv, size);
			depth = seen(new double[] { (from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2 })[2];
		}

		/** A direction turned with the limb: forward about x first, then out from the body about z. */
		double[] bend(double[] v) {
			double forward = -swing * Math.PI / 180;
			double cf = Math.cos(forward);
			double sf = Math.sin(forward);
			double y1 = v[1] * cf - v[2] * sf;
			double z1 = v[1] * sf + v[2] * cf;
			double out = (pivot[0] < 0 ? -spread : spread) * Math.PI / 180;
			double co = Math.cos(out);
			double so = Math.sin(out);
			return new double[] { v[0] * co - y1 * so, v[0] * so + y1 * co, z1 };
		}

		double[] seen(double[] point) {
			double[] bent = bend(new double[] { point[0] - pivot[0], point[1] - pivot[1], point[2] - pivot[2] });
			return camera(new double[] { bent[0] + pivot[0], bent[1] + pivot[1], bent[2] + pivot[2] });
		}

		double[] turned(double[] direction) {
			return camera(bend(direction));
		}
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
		out.add(new Part(new double[] { -4, 0, -2 }, new double[] { 0, 12, 2 }, new int[] { 0, 16 }, new int[] { 0, 32 }, 0.25, new double[] { -2, 12, 0 }, -LEG_SWING, 0));
		out.add(new Part(new double[] { 0, 0, -2 }, new double[] { 4, 12, 2 }, new int[] { 16, 48 }, new int[] { 0, 48 }, 0.25, new double[] { 2, 12, 0 }, LEG_SWING, 0));
		out.add(new Part(new double[] { -4 - arm, 12, -2 }, new double[] { -4, 24, 2 }, new int[] { 40, 16 }, new int[] { 40, 32 }, 0.25, new double[] { -5, 22, 0 }, ARM_SWING, ARM_SPREAD));
		out.add(new Part(new double[] { -4, 12, -2 }, new double[] { 4, 24, 2 }, new int[] { 16, 16 }, new int[] { 16, 32 }, 0.25, new double[] { 0, 12, 0 }, 0, 0));
		out.add(new Part(new double[] { 4, 12, -2 }, new double[] { 4 + arm, 24, 2 }, new int[] { 32, 48 }, new int[] { 48, 48 }, 0.25, new double[] { 5, 22, 0 }, -ARM_SWING, ARM_SPREAD));
		out.add(new Part(new double[] { -4, 24, -4 }, new double[] { 4, 32, 4 }, new int[] { 0, 0 }, new int[] { 32, 0 }, 0.5, new double[] { 0, 24, 0 }, 0, 0));
		out.sort(Comparator.comparingDouble(part -> part.depth));
		return out;
	}

	/** The picture of a skin of 64 by 64, as {@link SkinPixels#normalise} gives it. */
	public static int[] render(int[] drawing, boolean slim) {
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

		float[] grid = new float[side * side * 4];
		for (Part part : parts) {
			paint(grid, side, drawing, part, part.base, SEAM, scale, offsetX, offsetY);
			paint(grid, side, drawing, part, part.outer, 0, scale, offsetX, offsetY);
		}
		return averaged(grid, side);
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
