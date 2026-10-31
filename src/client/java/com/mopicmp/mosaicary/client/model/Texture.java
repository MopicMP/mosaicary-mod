package com.mopicmp.mosaicary.client.model;

/**
 * One texture of a project.
 *
 * @param index      place in the project's texture list, which is how faces name it
 * @param uuid       the texture's own id, which some files name it by instead
 * @param name       file name as the author saved it, for display only
 * @param image      the picture's bytes when it is embedded in the file, else null
 * @param mime       the picture's media type when embedded, such as image/png
 * @param width      pixel width, from the file's fields or the PNG header; 0 when unknown
 * @param height     pixel height, the same way
 * @param uvWidth    the UV space faces are laid out against, horizontally
 * @param uvHeight   the same, vertically
 * @param frames     how many animation frames are stacked in the picture; the first is drawn
 * @param doubleSided whether faces painted with it are seen from behind
 */
public record Texture(int index, String uuid, String name, byte[] image, String mime, int width,
		int height, float uvWidth, float uvHeight, int frames, boolean doubleSided) {

	public boolean embedded() {
		return image != null;
	}
}
