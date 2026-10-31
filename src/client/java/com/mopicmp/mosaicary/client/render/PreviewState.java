package com.mopicmp.mosaicary.client.render;

import com.mopicmp.mosaicary.client.model.Posing;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * One frame of a model preview in a screen.
 *
 * @param mesh   the model to draw
 * @param spin   the angle around the vertical axis the model is seen from, in
 *               radians, measured the way the site measures a card's angle: 0
 *               looks at the model's south side, from +Z
 * @param tilt   how far above the model the eye is, in radians
 * @param center the point of the model at the middle of the box, in model pixels
 * @param scale  GUI units per model pixel
 * @param posing the frame of an animation, or null for the rest pose
 */
public record PreviewState(ModelMesh mesh, Posing posing, float spin, float tilt, float[] center, int x0, int y0,
		int x1, int y1, float scale, ScreenRectangle scissorArea, ScreenRectangle bounds)
		implements PictureInPictureRenderState {

	public static PreviewState of(ModelMesh mesh, Posing posing, float spin, float tilt, float[] center, int x0,
			int y0, int x1, int y1, float scale, ScreenRectangle scissorArea) {
		return new PreviewState(mesh, posing, spin, tilt, center, x0, y0, x1, y1, scale, scissorArea,
			PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}
}
