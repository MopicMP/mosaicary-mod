package com.mopicmp.mosaicary.client.model;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Extra rotation, offset and scale per bone for one frame, on top of the rest pose.
 *
 * The renderer asks for every bone it draws; a bone nobody moves keeps its rest pose.
 * Rotations are degrees added to the rest rotation, offsets model pixels added to
 * the rest offset, scales factors.
 */
public interface Posing {

	void pose(Bone bone, float[] rotation, float[] offset, float[] scale);

	/** A frame built up from several sources: an animation, then motion on top. */
	final class Frame implements Posing {
		private final Map<Bone, Animation.Change> changes = new IdentityHashMap<>();

		/** Adds the changes of an animation sample. */
		public Frame add(Map<Bone, Animation.Change> sample) {
			for (Map.Entry<Bone, Animation.Change> entry : sample.entrySet()) {
				Animation.Change mine = of(entry.getKey());
				Animation.Change more = entry.getValue();
				for (int i = 0; i < 3; i++) {
					mine.rotation[i] += more.rotation[i];
					mine.position[i] += more.position[i];
					mine.scale[i] *= more.scale[i];
				}
			}
			return this;
		}

		/** The change of one bone, made on first use so it can be added to. */
		public Animation.Change of(Bone bone) {
			return changes.computeIfAbsent(bone, b -> new Animation.Change());
		}

		/** Forgets every change, for an overlay that takes the whole body. */
		public void clear() {
			changes.clear();
		}

		public boolean isEmpty() {
			return changes.isEmpty();
		}

		/**
		 * A frame part of the way from one to the other: {@code toward} 0 is {@code from},
		 * 1 is {@code to}.
		 *
		 * Every bone either of them moves is in the result; a bone one of them leaves
		 * alone counts as standing at rest there, so a bone only the old animation moved
		 * comes back to rest over the blend instead of jumping.
		 */
		public static Frame blend(Frame from, Frame to, float toward) {
			Frame out = new Frame();
			Animation.Change rest = new Animation.Change();
			java.util.Set<Bone> bones = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			bones.addAll(from.changes.keySet());
			bones.addAll(to.changes.keySet());
			for (Bone bone : bones) {
				Animation.Change a = from.changes.getOrDefault(bone, rest);
				Animation.Change b = to.changes.getOrDefault(bone, rest);
				Animation.Change into = out.of(bone);
				for (int i = 0; i < 3; i++) {
					into.rotation[i] = a.rotation[i] + (b.rotation[i] - a.rotation[i]) * toward;
					into.position[i] = a.position[i] + (b.position[i] - a.position[i]) * toward;
					into.scale[i] = a.scale[i] + (b.scale[i] - a.scale[i]) * toward;
				}
			}
			return out;
		}

		@Override
		public void pose(Bone bone, float[] rotation, float[] offset, float[] scale) {
			Animation.Change change = changes.get(bone);
			if (change == null) return;
			for (int i = 0; i < 3; i++) {
				rotation[i] += change.rotation[i];
				offset[i] += change.position[i];
				scale[i] *= change.scale[i];
			}
		}
	}
}
