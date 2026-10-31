package com.mopicmp.mosaicary.client.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** One animation giving way to the next: the frames in between. */
class BlendTest {

	private static Bone bone(String name) {
		return new Bone(name, name, null, new float[3], new float[3], new float[] { 1, 1, 1 }, true, true);
	}

	private static float[] rotation(Posing posing, Bone bone) {
		float[] rotation = new float[3];
		posing.pose(bone, rotation, new float[3], new float[] { 1, 1, 1 });
		return rotation;
	}

	@Test
	void goesPartWayFromOneFrameToTheNext() {
		Bone arm = bone("arm");
		Posing.Frame walk = new Posing.Frame();
		walk.of(arm).rotation[0] = 40;
		Posing.Frame stand = new Posing.Frame();
		stand.of(arm).rotation[0] = -20;
		assertArrayEquals(new float[] { 40, 0, 0 }, rotation(Posing.Frame.blend(walk, stand, 0), arm));
		assertArrayEquals(new float[] { 10, 0, 0 }, rotation(Posing.Frame.blend(walk, stand, 0.5f), arm));
		assertArrayEquals(new float[] { -20, 0, 0 }, rotation(Posing.Frame.blend(walk, stand, 1), arm));
	}

	@Test
	void aBoneOnlyOneFrameMovesComesBackToRestInsteadOfJumping() {
		Bone tail = bone("tail");
		Posing.Frame wag = new Posing.Frame();
		wag.of(tail).rotation[1] = 30;
		wag.of(tail).scale[0] = 2;
		Posing.Frame still = new Posing.Frame();
		Posing.Frame half = Posing.Frame.blend(wag, still, 0.5f);
		assertArrayEquals(new float[] { 0, 15, 0 }, rotation(half, tail));
		float[] scale = { 1, 1, 1 };
		half.pose(tail, new float[3], new float[3], scale);
		assertArrayEquals(new float[] { 1.5f, 1, 1 }, scale);
	}
}
