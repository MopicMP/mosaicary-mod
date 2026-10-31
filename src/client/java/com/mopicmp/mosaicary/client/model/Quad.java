package com.mopicmp.mosaicary.client.model;

/**
 * One drawn face: four corners, their texture coordinates, and the face's normal.
 *
 * A triangle is a quad whose last corner repeats its third, which is how the
 * game's quad-based vertex formats draw triangles anyway.
 *
 * Positions are in model pixels (sixteen to a block), in the space of the bone
 * the quad belongs to, measured from that bone's pivot. Texture coordinates are
 * fractions of the texture with v pointing down, the way the game samples. The
 * corners run counter-clockwise seen from the front.
 *
 * @param positions x, y, z of the four corners
 * @param uvs       u, v of the four corners
 * @param normal    x, y, z, unit length
 * @param texture   index into {@link BbModel#textures()}, or -1 for a face with
 *                  nothing to paint it with, drawn in {@code marker} instead
 * @param marker    0xRRGGBB colour of an unpainted face, the way Blockbench marks one
 * @param doubleSided whether the face is seen from behind as well
 */
public record Quad(float[] positions, float[] uvs, float[] normal, int texture, int marker,
		boolean doubleSided) {

	public float x(int corner) {
		return positions[corner * 3];
	}

	public float y(int corner) {
		return positions[corner * 3 + 1];
	}

	public float z(int corner) {
		return positions[corner * 3 + 2];
	}

	public float u(int corner) {
		return uvs[corner * 2];
	}

	public float v(int corner) {
		return uvs[corner * 2 + 1];
	}
}
