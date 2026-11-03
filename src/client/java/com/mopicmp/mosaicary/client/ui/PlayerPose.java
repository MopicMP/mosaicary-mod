package com.mopicmp.mosaicary.client.ui;

import java.util.NoSuchElementException;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;
//#if MC>=12111
import net.minecraft.client.model.Model;
//#endif
//#if MC>=260100
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#endif

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

	// How much deeper than the game's the overlapping parts are drawn (see model).
	private static final float LEG = 1.005f;
	private static final float ARM = 1.0025f;
	private static final float JACKET = 1.01f;

	/**
	 * The figure: its parts, posed in place, and from 1.21.11 on the game's model that draws them.
	 * Before 1.21.2 a model is not built around its parts, and the parts are drawn as they are.
	 */
	public static final class Figure {

		private final ModelPart root;
		//#if MC>=12111
		private final Model<?> model;

		private Figure(Model<?> model) {
			this.model = model;
			this.root = model.root();
		}
		//#else
		//$$ private Figure(ModelPart root) {
		//$$ 	this.root = root;
		//$$ }
		//#endif
	}

	/**
	 * The game's player model, wide or slim, as it is without mods, but for the depth of some parts.
	 *
	 * The game's legs overlap by a fifth of a pixel, and the outer layer of the legs, the arms and
	 * the body overlap where they meet, all with their fronts in one plane. Seen from close by,
	 * as a preview is, the graphics card cannot tell which is nearer and the overlap flickers as
	 * the figure turns. Each of them is made a hair deeper than the next, too little to see.
	 */
	public static Figure model(boolean slim) {
		ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
		//#if MC>=12111
		deeper(root.getChild("left_leg"), LEG);
		deeper(root.getChild("left_arm"), ARM);
		deeper(root.getChild("right_arm"), ARM);
		deeper(root.getChild("body").getChild("jacket"), JACKET);
		//#else
		//$$ // The ears and the cape are parts of this model too, which the game draws apart, if at all.
		//$$ // The depths are set as the figure is drawn (cover).
		//$$ root.getChild("ear").visible = false;
		//$$ root.getChild("cloak").visible = false;
		//#endif
		//#if MC>=260100
		return new Figure(new Model.Simple(root, RenderTypes::entityTranslucent));
		//#elseif MC>=12111
		//$$ return new Figure(new PlayerModel(root, slim));
		//#else
		//$$ return new Figure(root);
		//#endif
	}

	/**
	 * Draws the figure into the box from (x0, y0) to (x1, y1) with the game's own skin renderer,
	 * the one behind the player figure on the skin customisation screen. Before 1.21.6 the game has
	 * none, and the figure is drawn here the way that renderer does: turned about its middle by
	 * {@code rotationX}, about its height by {@code rotationY}, feet at the bottom of the box.
	 */
	public static void draw(GuiGraphicsExtractor graphics, Figure figure, Identifier texture, float scale,
			float rotationX, float rotationY, float pivotY, int x0, int y0, int x1, int y1) {
		//#if MC>=260100
		graphics.skin((Model.Simple) figure.model, texture, scale, rotationX, rotationY, pivotY, x0, y0, x1, y1);
		//#elseif MC>=12111
		//$$ graphics.submitSkinRenderState((PlayerModel) figure.model, texture, scale, rotationX, rotationY, pivotY, x0, y0, x1, y1);
		//#else
		//$$ graphics.enableScissor(x0, y0, x1, y1);
		//$$ // The figure gets a depth of its own in its box, as 1.21.11's renderer draws it into a picture of
		//$$ // its own: the screen drawn so far lies at depth 0, and a figure turned towards or away from the
		//$$ // viewer went behind it, cut off along a slant. Cleared again after, so what comes later is on top.
		//$$ graphics.flush();
		//$$ clearDepth();
		//$$ com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
		//$$ pose.pushPose();
		//$$ float pivot = scale * -pivotY;
		//$$ pose.translate((x0 + x1) / 2f, y0 + pivot, 50);
		//$$ pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotationX));
		//$$ pose.translate(0, y1 - y0 - pivot, 0);
		//$$ pose.scale(scale, scale, -scale);
		//$$ pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-rotationY));
		//$$ pose.translate(0, -1.601f, 0);
		//$$ com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
		//$$ cover(figure.root);
		//$$ figure.root.render(pose, graphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(texture)),
		//$$ 	0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		//$$ graphics.flush();
		//$$ clearDepth();
		//$$ com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
		//$$ pose.popPose();
		//$$ graphics.disableScissor();
		//#endif
	}

	//#if MC>=12111
	private static void deeper(ModelPart part, float depth) {
		PartPose pose = part.getInitialPose();
		part.setInitialPose(new PartPose(pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot(), pose.xScale(), pose.yScale(), depth));
		part.resetPose();
	}
	//#else
	//$$ /** Each outer layer and the part it covers. */
	//$$ private static final String[][] LAYERS = { { "hat", "head" }, { "jacket", "body" },
	//$$ 	{ "left_sleeve", "left_arm" }, { "right_sleeve", "right_arm" }, { "left_pants", "left_leg" }, { "right_pants", "right_leg" } };
	//$$
	//$$ /**
	//$$  * Before 1.21.2 an outer layer lies beside the part it covers rather than inside it, and the game
	//$$  * moves it into place just before drawing; and a pose has no size, so setting a part back to its
	//$$  * pose makes it its own size again. Both are done here, as the figure is drawn: the layers follow
	//$$  * their parts, then the depths.
	//$$  */
	//$$ /** The depth buffer cleared inside the scissor box set at the time. */
	//$$ private static void clearDepth() {
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
	//$$ }
	//$$
	//$$ private static void cover(ModelPart root) {
	//$$ 	for (String[] layer : LAYERS) root.getChild(layer[0]).loadPose(root.getChild(layer[1]).storePose());
	//$$ 	for (String leg : new String[] { "left_leg", "left_pants" }) root.getChild(leg).zScale = LEG;
	//$$ 	for (String arm : new String[] { "left_arm", "right_arm", "left_sleeve", "right_sleeve" }) root.getChild(arm).zScale = ARM;
	//$$ 	root.getChild("jacket").zScale = JACKET;
	//$$ }
	//#endif

	public static void rest(Figure figure) {
		figure.root.getAllParts().forEach(ModelPart::resetPose);
	}

	/** Standing still: the arms sway a little, out of step with each other. */
	public static void idle(Figure figure, float seconds) {
		ModelPart right = part(figure, "right_arm");
		ModelPart left = part(figure, "left_arm");
		if (right == null || left == null) return;
		float sway = (float) Math.cos(seconds * 1.8) * 0.05f + 0.05f;
		right.zRot += sway;
		left.zRot -= sway;
		float swing = (float) Math.sin(seconds * 1.35) * 0.04f;
		right.xRot += swing;
		left.xRot -= swing;
	}

	/** The game's walk: arms and legs swinging in turn. */
	public static void walk(Figure figure, float seconds) {
		ModelPart rightArm = part(figure, "right_arm");
		ModelPart leftArm = part(figure, "left_arm");
		ModelPart rightLeg = part(figure, "right_leg");
		ModelPart leftLeg = part(figure, "left_leg");
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
	public static void look(Figure figure, float yaw, float pitch) {
		ModelPart head = part(figure, "head");
		if (head == null) return;
		head.yRot = clamp(yaw, 1.22f);
		head.xRot = clamp(pitch, 0.7f);
	}

	private static ModelPart part(Figure figure, String name) {
		try {
			return figure.root.getChild(name);
		} catch (NoSuchElementException missing) {
			return null;
		}
	}

	private static float clamp(float value, float most) {
		return Math.max(-most, Math.min(most, value));
	}
}
