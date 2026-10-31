package com.mopicmp.mosaicary.client.ui;

import java.util.NoSuchElementException;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Poses for the game's own player model drawn in a screen: the wardrobe's preview and the
 * figure beside the main menu. The motions are the game's own walk ({@code HumanoidModel}),
 * and the slight idle sway of the arms every player has standing still.
 *
 * The model is posed in place: {@link #rest} first, then any of the others. The skin
 * renderer draws the model as it is posed when the frame is drawn, so a pose set while a
 * screen is drawn is the one seen.
 *
 * The model is the game's own, built here from the game's shapes rather than asked of the
 * game: a mod that replaces models (Entity Model Features, with a pack such as Fresh Moves)
 * would hand its own instead, whose extra parts read corners of the skin that a plain skin
 * leaves empty and draw them black. A part that is not there is left alone all the same.
 */
public final class PlayerPose {

	private PlayerPose() { }

	/**
	 * The game's player model, wide or slim, as it is without mods, but for the depth of some parts.
	 *
	 * The game's legs overlap by a fifth of a pixel, and the outer layer of the legs, the arms and
	 * the body overlap where they meet, all with their fronts in one plane. Seen from close by,
	 * as a preview is, the graphics card cannot tell which is nearer and the overlap flickers as
	 * the figure turns. Each of them is made a hair deeper than the next, too little to see.
	 */
	public static Model.Simple model(boolean slim) {
		ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
		deeper(root.getChild("left_leg"), 1.005f);
		deeper(root.getChild("left_arm"), 1.0025f);
		deeper(root.getChild("right_arm"), 1.0025f);
		deeper(root.getChild("body").getChild("jacket"), 1.01f);
		return new Model.Simple(root, RenderTypes::entityTranslucent);
	}

	private static void deeper(ModelPart part, float depth) {
		PartPose pose = part.getInitialPose();
		part.setInitialPose(new PartPose(pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot(), pose.xScale(), pose.yScale(), depth));
		part.resetPose();
	}

	public static void rest(Model.Simple model) {
		model.resetPose();
	}

	/** Standing still: the arms sway a little, out of step with each other. */
	public static void idle(Model.Simple model, float seconds) {
		ModelPart right = part(model, "right_arm");
		ModelPart left = part(model, "left_arm");
		if (right == null || left == null) return;
		float sway = (float) Math.cos(seconds * 1.8) * 0.05f + 0.05f;
		right.zRot += sway;
		left.zRot -= sway;
		float swing = (float) Math.sin(seconds * 1.35) * 0.04f;
		right.xRot += swing;
		left.xRot -= swing;
	}

	/** The game's walk: arms and legs swinging in turn. */
	public static void walk(Model.Simple model, float seconds) {
		ModelPart rightArm = part(model, "right_arm");
		ModelPart leftArm = part(model, "left_arm");
		ModelPart rightLeg = part(model, "right_leg");
		ModelPart leftLeg = part(model, "left_leg");
		if (rightArm == null || leftArm == null || rightLeg == null || leftLeg == null) return;
		float position = seconds * 9;
		float speed = 0.7f;
		rightArm.xRot = (float) Math.cos(position * 0.6662 + Math.PI) * speed;
		leftArm.xRot = (float) Math.cos(position * 0.6662) * speed;
		rightLeg.xRot = (float) Math.cos(position * 0.6662) * 1.4f * speed;
		leftLeg.xRot = (float) Math.cos(position * 0.6662 + Math.PI) * 1.4f * speed;
	}

	/**
	 * The head turned and tilted, in radians, as far as a neck turns: up to 70 degrees to the
	 * side and 40 up or down.
	 */
	public static void look(Model.Simple model, float yaw, float pitch) {
		ModelPart head = part(model, "head");
		if (head == null) return;
		head.yRot = clamp(yaw, 1.22f);
		head.xRot = clamp(pitch, 0.7f);
	}

	private static ModelPart part(Model.Simple model, String name) {
		try {
			return model.root().getChild(name);
		} catch (NoSuchElementException missing) {
			return null;
		}
	}

	private static float clamp(float value, float most) {
		return Math.max(-most, Math.min(most, value));
	}
}
