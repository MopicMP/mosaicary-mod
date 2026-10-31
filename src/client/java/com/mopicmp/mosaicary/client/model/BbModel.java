package com.mopicmp.mosaicary.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A Blockbench project, read into faces and bones.
 *
 * Nothing here knows about the game: the renderer turns this into textures and
 * vertices, and the same object will serve a preview in a screen and a model worn
 * in the world.
 *
 * @param name      the project's own name, which may differ from the site's
 * @param format    Blockbench's id for the format, such as {@code free} or {@code bedrock}
 * @param version   the project format version the file was written in
 * @param traits    how the format behaves where drawing is concerned
 * @param textures  every texture, in the file's order
 * @param root      the nameless top-level bone; every other bone hangs from it
 * @param bones     every bone except the root, in outliner order (parents before children)
 * @param byName    bones by name; the first of several bones with the same name wins
 * @param animations the animations in the file, in its order
 * @param placeholders values the author set for Molang names the game would supply,
 *                  read from the project's animation variable placeholders
 * @param cubes     cube elements in the file, drawn or not, as the site counts them
 * @param meshes    mesh elements in the file, the same way
 */
public record BbModel(String name, String format, String version, Traits traits,
		List<Texture> textures, Bone root, List<Bone> bones, Map<String, Bone> byName,
		List<Animation> animations, Map<String, Molang.Expression> placeholders, int cubes, int meshes) {

	/**
	 * The Molang values the animations start from: the author's placeholders,
	 * evaluated in the order they were written.
	 */
	public Map<String, Double> startingValues() {
		Map<String, Double> values = new java.util.HashMap<>();
		for (Map.Entry<String, Molang.Expression> placeholder : placeholders.entrySet()) {
			values.put(placeholder.getKey(), placeholder.getValue().eval(values));
		}
		return values;
	}

	/** An animation by name, ignoring case, or null. */
	public Animation animation(String name) {
		for (Animation animation : animations) {
			if (animation.name().equalsIgnoreCase(name)) return animation;
		}
		return null;
	}

	/** A bone by its name, or null. */
	public Bone bone(String name) {
		return byName.get(name);
	}

	/** Every quad, all bones together. */
	public int quadCount() {
		int count = root.quads().size();
		for (Bone bone : bones) count += bone.quads().size();
		return count;
	}

	/** A quad placed in model space at rest, with the bone it came from. */
	public record Placed(Bone bone, Quad quad, float[] positions, float[] normal) { }

	/**
	 * Every visible quad moved to model space at rest, in model pixels.
	 *
	 * For measuring (the bounds, tests); the renderer keeps bones apart instead so
	 * that each can move.
	 */
	public List<Placed> restQuads() {
		List<Placed> out = new ArrayList<>();
		collect(root, new Matrix4f(), out);
		return Collections.unmodifiableList(out);
	}

	private static void collect(Bone bone, Matrix4f parent, List<Placed> out) {
		if (!bone.visible()) return;
		Matrix4f here = new Matrix4f(parent).mul(bone.isRoot() ? new Matrix4f() : bone.restLocal());
		Matrix3f turn = new Matrix3f(here).invert().transpose();
		Vector3f point = new Vector3f();
		for (Quad quad : bone.quads()) {
			float[] positions = new float[12];
			for (int i = 0; i < 4; i++) {
				here.transformPosition(quad.x(i), quad.y(i), quad.z(i), point);
				positions[i * 3] = point.x;
				positions[i * 3 + 1] = point.y;
				positions[i * 3 + 2] = point.z;
			}
			Vector3f normal = turn.transform(new Vector3f(quad.normal()[0], quad.normal()[1], quad.normal()[2]));
			if (normal.lengthSquared() > 0) normal.normalize();
			out.add(new Placed(bone, quad, positions, new float[] { normal.x, normal.y, normal.z }));
		}
		for (Bone child : bone.children()) collect(child, here, out);
	}

	/**
	 * The box around everything drawn at rest, in model pixels, as
	 * {minX, minY, minZ, maxX, maxY, maxZ}; null when nothing is drawn.
	 */
	public float[] bounds() {
		float[] box = null;
		for (Placed placed : restQuads()) {
			for (int i = 0; i < 4; i++) {
				float x = placed.positions()[i * 3];
				float y = placed.positions()[i * 3 + 1];
				float z = placed.positions()[i * 3 + 2];
				if (box == null) {
					box = new float[] { x, y, z, x, y, z };
				} else {
					box[0] = Math.min(box[0], x);
					box[1] = Math.min(box[1], y);
					box[2] = Math.min(box[2], z);
					box[3] = Math.max(box[3], x);
					box[4] = Math.max(box[4], y);
					box[5] = Math.max(box[5], z);
				}
			}
		}
		return box;
	}
}
