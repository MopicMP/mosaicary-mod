package com.mopicmp.mosaicary.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Matrix4f;

/**
 * One group of the outliner, with the faces hanging from it.
 *
 * In formats where groups are bones, a bone stands at its pivot, turned by its
 * rotation, and everything under it moves with it. The quads are kept in the
 * bone's own space, measured from its pivot, so that an animation can turn a bone
 * by changing one matrix rather than rebuilding geometry.
 *
 * The model's top level is a bone too: a nameless root at the origin that holds
 * everything not inside a group, and in formats without bones, everything.
 */
public final class Bone {

	private final String uuid;
	private final String name;
	private final Bone parent;
	private final List<Bone> children = new ArrayList<>();
	private final List<Quad> quads = new ArrayList<>();

	/** The pivot in model pixels, absolute (not relative to the parent). */
	private final float[] pivot;
	/** Rest rotation in degrees about X, Y, Z, applied Z first, then Y, then X. */
	private final float[] rotation;
	private final float[] scale;
	private final boolean visible;
	/** Whether this group moves its contents: false in formats without bones. */
	private final boolean rigged;

	Bone(String uuid, String name, Bone parent, float[] pivot, float[] rotation, float[] scale,
			boolean visible, boolean rigged) {
		this.uuid = uuid;
		this.name = name;
		this.parent = parent;
		this.pivot = pivot;
		this.rotation = rotation;
		this.scale = scale;
		this.visible = visible;
		this.rigged = rigged;
		if (parent != null) parent.children.add(this);
	}

	public String uuid() {
		return uuid;
	}

	public String name() {
		return name;
	}

	public Bone parent() {
		return parent;
	}

	public List<Bone> children() {
		return Collections.unmodifiableList(children);
	}

	public List<Quad> quads() {
		return Collections.unmodifiableList(quads);
	}

	void add(Quad quad) {
		quads.add(quad);
	}

	public float[] pivot() {
		return pivot.clone();
	}

	public float[] rotation() {
		return rotation.clone();
	}

	public float[] scale() {
		return scale.clone();
	}

	public boolean visible() {
		return visible;
	}

	public boolean rigged() {
		return rigged;
	}

	public boolean isRoot() {
		return parent == null;
	}

	/**
	 * Where this bone's pivot sits relative to its parent's pivot, in model pixels.
	 * For the root and for groups that are not bones, the offset that keeps the
	 * quads where they are.
	 */
	public float[] offset() {
		if (parent == null) return pivot.clone();
		return new float[] { pivot[0] - parent.pivot[0], pivot[1] - parent.pivot[1], pivot[2] - parent.pivot[2] };
	}

	/**
	 * This bone's rest transform relative to its parent, in model pixels, with an
	 * extra turn, shift and scale on top for animation. Pass zeros and ones for the
	 * rest pose.
	 */
	public Matrix4f local(float[] extraRotation, float[] extraOffset, float[] extraScale, Matrix4f into) {
		float[] at = offset();
		into.identity().translate(at[0] + extraOffset[0], at[1] + extraOffset[1], at[2] + extraOffset[2]);
		if (rigged) {
			into.rotateZ((float) Math.toRadians(rotation[2] + extraRotation[2]))
				.rotateY((float) Math.toRadians(rotation[1] + extraRotation[1]))
				.rotateX((float) Math.toRadians(rotation[0] + extraRotation[0]))
				.scale(scale[0] * extraScale[0], scale[1] * extraScale[1], scale[2] * extraScale[2]);
		}
		return into;
	}

	/** The rest transform relative to the parent. */
	public Matrix4f restLocal() {
		return local(new float[3], new float[3], new float[] { 1, 1, 1 }, new Matrix4f());
	}

	/** The rest transform from this bone's space to model space. */
	public Matrix4f restToModel() {
		Matrix4f matrix = restLocal();
		for (Bone up = parent; up != null; up = up.parent) {
			matrix = up.restLocal().mul(matrix);
		}
		return matrix;
	}

	@Override
	public String toString() {
		return "Bone[" + name + ", " + quads.size() + " quads, " + children.size() + " children]";
	}
}
