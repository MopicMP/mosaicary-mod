package com.mopicmp.mosaicary.client.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One animation of a project, played the way Blockbench's timeline plays it.
 *
 * The rules are the ones the website's viewer uses (site/src/lib/bbanim.ts), which
 * were taken from Blockbench's own {@code keyframe.js} and
 * {@code timeline_animators.js}: four interpolations (linear, step, Catmull-Rom,
 * Bezier), a twelve-hundredth of a second counted as "on a key", a key with two
 * values leaving by the second and arriving by the first, and the turned-round
 * signs of files older than format 5.0.
 *
 * A pose is additive, as in Blockbench: the rotation in degrees is added to the
 * bone's rest rotation, the position in model pixels to its rest offset, and the
 * scale multiplies.
 *
 * @param name   as the author named it
 * @param uuid   the file's id for it, or empty
 * @param length in seconds
 * @param loop   {@code loop}, {@code once} or {@code hold}
 * @param tracks one per animated bone
 */
public record Animation(String name, String uuid, float length, Loop loop, List<Track> tracks) {

	public enum Loop { LOOP, ONCE, HOLD }

	public enum Channel { ROTATION, POSITION, SCALE }

	/** Within this of a key, in seconds, is on it. */
	private static final double EPSILON = 1.0 / 1200;

	/** How long the last frame of a clip that does not loop is held in a preview before it starts again. */
	private static final double HOLD_IN_PREVIEW = 1;

	/** One keyframe: a time and one or two points of three compiled values. */
	public record Key(double time, String interpolation, Molang.Expression[][] points,
			double[] leftTime, double[] leftValue, double[] rightTime, double[] rightValue) { }

	/** The keyframes of one bone, by channel, sorted by time. */
	public record Track(Bone bone, Map<Channel, List<Key>> keys) { }

	/** A bone's change for one moment: degrees, pixels and factors. */
	public static final class Change {
		public final float[] rotation = new float[3];
		public final float[] position = new float[3];
		public final float[] scale = { 1, 1, 1 };
	}

	/**
	 * Where a viewer is in the clip after watching it for {@code elapsed} seconds.
	 *
	 * A looping clip goes round. One that plays once, or plays and holds, is shown to
	 * its end, held there for a second so the last pose is seen, and started again;
	 * the site's viewer does the same.
	 */
	public double timeInPreview(double elapsed) {
		if (length <= 0) return 0;
		if (loop == Loop.LOOP) return elapsed % length;
		return Math.min(length, elapsed % (length + HOLD_IN_PREVIEW));
	}

	/**
	 * Where a clip played by the game is after {@code elapsed} seconds: looping
	 * clips go round, the others stop on their last frame.
	 */
	public double timeInPlay(double elapsed) {
		if (length <= 0) return 0;
		if (loop == Loop.LOOP) return elapsed % length;
		return Math.min(length, elapsed);
	}

	/**
	 * The change of every animated bone at {@code seconds}, with {@code values}
	 * supplying Molang names beyond {@code query.anim_time} (which is set here).
	 *
	 * @param unreadable counts the values that could not be read, for a log line;
	 *                   may be null
	 */
	public Map<Bone, Change> sample(double seconds, Map<String, Double> values, int[] unreadable) {
		Map<Bone, Change> out = new IdentityHashMap<>();
		values.put("query.anim_time", seconds);
		values.putIfAbsent("query.life_time", seconds);
		for (Track track : tracks) {
			Change change = new Change();
			for (Map.Entry<Channel, List<Key>> entry : track.keys().entrySet()) {
				double[] value = sampleChannel(entry.getValue(), seconds, loop == Loop.LOOP, values, unreadable);
				if (value == null) continue;
				switch (entry.getKey()) {
					case ROTATION -> {
						for (int i = 0; i < 3; i++) change.rotation[i] += (float) value[i];
					}
					case POSITION -> {
						for (int i = 0; i < 3; i++) change.position[i] += (float) value[i];
					}
					case SCALE -> {
						// Nought would make the matrix singular; Blockbench keeps a hair above it.
						for (int i = 0; i < 3; i++) change.scale[i] *= value[i] == 0 ? 0.00001f : (float) value[i];
					}
				}
			}
			out.put(track.bone(), change);
		}
		return out;
	}

	private static double read(Key key, int axis, int point, Map<String, Double> values, int[] unreadable) {
		Molang.Expression[] at = key.points()[Math.max(0, Math.min(point, key.points().length - 1))];
		Molang.Expression expression = at[axis];
		if (expression.unreadable() && unreadable != null) unreadable[0]++;
		double value = expression.eval(values);
		return Double.isFinite(value) ? value : 0;
	}

	/** One channel at one moment: Blockbench's {@code interpolate}, or null where it leaves the bone alone. */
	static double[] sampleChannel(List<Key> keys, double time, boolean looped, Map<String, Double> values,
			int[] unreadable) {
		Key first = null;
		Key last = null;
		for (Key key : keys) {
			if (key.time() < time) {
				if (first == null || key.time() > first.time()) first = key;
			} else if (last == null || key.time() < last.time()) {
				last = key;
			}
		}

		Key held = null;
		if (first != null && on(first, time)) held = first;
		else if (last != null && on(last, time)) held = last;
		else if (first != null && first.interpolation().equals("step")) held = first;
		else if (first != null && last == null) held = first;
		else if (last != null && first == null) held = last;

		double[] out = new double[3];
		if (held != null) {
			// Leaving a key with two values uses the second; arriving at one, the first.
			int point = held.time() > time || on(held, time) ? 0 : held.points().length - 1;
			for (int axis = 0; axis < 3; axis++) out[axis] = read(held, axis, point, values, unreadable);
			return out;
		}
		if (first == null || last == null) return null;
		Key before = first;
		Key after = last;
		double alpha = (time - before.time()) / (after.time() - before.time());
		String a = before.interpolation();
		String b = after.interpolation();

		if (a.equals("linear") && (b.equals("linear") || b.equals("step"))) {
			int from = before.points().length > 1 ? 1 : 0;
			for (int axis = 0; axis < 3; axis++) {
				double start = read(before, axis, from, values, unreadable);
				out[axis] = start + (read(after, axis, 0, values, unreadable) - start) * alpha;
			}
			return out;
		}

		if (a.equals("catmullrom") || b.equals("catmullrom")) {
			List<Key> order = new ArrayList<>(keys);
			order.sort(Comparator.comparingDouble(Key::time));
			int index = order.indexOf(before);
			Key outer = index > 0 ? order.get(index - 1) : null;
			Key further = index + 2 < order.size() ? order.get(index + 2) : null;
			if (looped && order.size() >= 3) {
				if (outer == null) outer = order.get(order.size() - 2);
				if (further == null) further = order.get(1);
			}
			for (int axis = 0; axis < 3; axis++) {
				List<double[]> points = new ArrayList<>();
				if (outer != null && before.points().length == 1) {
					points.add(new double[] { outer.time(), read(outer, axis, 1, values, unreadable) });
				}
				points.add(new double[] { before.time(), read(before, axis, 1, values, unreadable) });
				points.add(new double[] { after.time(), read(after, axis, 0, values, unreadable) });
				if (further != null && after.points().length == 1) {
					points.add(new double[] { further.time(), read(further, axis, 0, values, unreadable) });
				}
				// Blockbench offsets by one whenever a key before exists, whether or not it
				// went into the curve; copied as it is so the two read the same values.
				out[axis] = splineY(points, (alpha + (outer != null ? 1 : 0)) / (points.size() - 1));
			}
			return out;
		}

		if (a.equals("bezier") || b.equals("bezier")) {
			double gap = after.time() - before.time();
			for (int axis = 0; axis < 3; axis++) {
				double start = read(before, axis, 1, values, unreadable);
				double stop = read(after, axis, 0, values, unreadable);
				double[] p0 = { before.time(), start };
				double[] p1 = { before.time() + clamp(before.rightTime()[axis], 0, gap), start + before.rightValue()[axis] };
				double[] p2 = { after.time() + clamp(after.leftTime()[axis], -gap, 0), stop + after.leftValue()[axis] };
				double[] p3 = { after.time(), stop };
				out[axis] = bezierAt(p0, p1, p2, p3, time);
			}
			return out;
		}
		return null;
	}

	private static boolean on(Key key, double time) {
		return Math.abs(key.time() - time) < EPSILON;
	}

	private static double clamp(double x, double low, double high) {
		return Math.max(low, Math.min(high, x));
	}

	/**
	 * three.js's {@code SplineCurve.getPoint(u).y}: a uniform Catmull-Rom spline through
	 * the points, the ends repeated, read at {@code u} along the whole curve.
	 */
	static double splineY(List<double[]> points, double u) {
		double point = (points.size() - 1) * u;
		int index = (int) Math.floor(point);
		double weight = point - index;
		int n = points.size();
		double p0 = points.get(index == 0 ? index : index - 1)[1];
		double p1 = points.get(Math.min(index, n - 1))[1];
		double p2 = points.get(index > n - 2 ? n - 1 : index + 1)[1];
		double p3 = points.get(index > n - 3 ? n - 1 : index + 2)[1];
		double v0 = (p2 - p0) * 0.5;
		double v1 = (p3 - p1) * 0.5;
		double t2 = weight * weight;
		double t3 = weight * t2;
		return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * weight + p1;
	}

	/**
	 * A cubic Bezier read off by time the way Blockbench does: 201 points along the
	 * curve, the two nearest in time, and a straight line between them.
	 */
	static double bezierAt(double[] p0, double[] p1, double[] p2, double[] p3, double time) {
		int count = 201;
		double[] xs = new double[count];
		double[] ys = new double[count];
		for (int i = 0; i < count; i++) {
			double t = i / (double) (count - 1);
			double u = 1 - t;
			double w0 = u * u * u;
			double w1 = 3 * u * u * t;
			double w2 = 3 * u * t * t;
			double w3 = t * t * t;
			xs[i] = w0 * p0[0] + w1 * p1[0] + w2 * p2[0] + w3 * p3[0];
			ys[i] = w0 * p0[1] + w1 * p1[1] + w2 * p2[1] + w3 * p3[1];
		}
		int nearest = 0;
		for (int i = 1; i < count; i++) if (Math.abs(xs[i] - time) < Math.abs(xs[nearest] - time)) nearest = i;
		int next = -1;
		for (int i = 0; i < count; i++) {
			if (i == nearest) continue;
			if (next < 0 || Math.abs(xs[i] - time) < Math.abs(xs[next] - time)) next = i;
		}
		if (next < 0 || xs[next] == xs[nearest]) return ys[nearest];
		return ys[nearest] + (ys[next] - ys[nearest]) * clamp((time - xs[nearest]) / (xs[next] - xs[nearest]), 0, 1);
	}
}
