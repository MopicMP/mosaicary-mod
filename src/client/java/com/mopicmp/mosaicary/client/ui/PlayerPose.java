package com.mopicmp.mosaicary.client.ui;

import java.util.NoSuchElementException;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
//#if MC>=11700
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
//#endif
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

	//#if MC<11904
	//$$ /** How far in front of the figure its see-through faces are put in order from, before 1.19.4 (see draw). */
	//$$ private static final float SORTED_FROM = 100_000;
	//#endif

	// How much deeper than the game's the overlapping parts are drawn (see model).
	private static final float LEG = 1.005f;
	private static final float ARM = 1.0025f;
	private static final float JACKET = 1.01f;

	/**
	 * The figure: its parts, posed in place, and from 1.21.11 on the game's model that draws them.
	 * Before 1.21.2 a model is not built around its parts, and the parts are drawn as they are.
	 */
	public static final class Figure {

		//#if MC>=11700
		private final ModelPart root;
		//#endif
		//#if MC>=12111
		private final Model<?> model;

		private Figure(Model<?> model) {
			this.model = model;
			this.root = model.root();
		}
		//#elseif MC>=11700
		//$$ private Figure(ModelPart root) {
		//$$ 	this.root = root;
		//#if MC<11900
		//$$ 	root.getAllParts().forEach(part -> initial.put(part, part.storePose()));
		//#endif
		//$$ }
		//#else
		//$$ /** The parts by the names the newer versions give them: before 1.17 a model has fields, not a tree. */
		//$$ private final java.util.Map<String, ModelPart> parts = new java.util.LinkedHashMap<>();
		//$$
		//$$ private Figure(net.minecraft.client.model.PlayerModel<?> model) {
		//$$ 	parts.put("head", model.head);
		//$$ 	parts.put("hat", model.hat);
		//$$ 	parts.put("body", model.body);
		//$$ 	parts.put("jacket", model.jacket);
		//$$ 	parts.put("right_arm", model.rightArm);
		//$$ 	parts.put("left_arm", model.leftArm);
		//$$ 	parts.put("right_sleeve", model.rightSleeve);
		//$$ 	parts.put("left_sleeve", model.leftSleeve);
		//$$ 	parts.put("right_leg", model.rightLeg);
		//$$ 	parts.put("left_leg", model.leftLeg);
		//$$ 	parts.put("right_pants", model.rightPants);
		//$$ 	parts.put("left_pants", model.leftPants);
		//$$ 	for (ModelPart part : parts.values()) initial.put(part, poseOf(part));
		//$$ }
		//#endif
		//#if MC<11900 && MC>=11700
		//$$ /** Each part's pose as built: before 1.19 a part does not keep it itself. */
		//$$ private final java.util.Map<ModelPart, net.minecraft.client.model.geom.PartPose> initial = new java.util.IdentityHashMap<>();
		//#elseif MC<11700
		//$$ /** Each part's place and turn as built (poseOf): before 1.17 there is no pose to keep them in. */
		//$$ private final java.util.Map<ModelPart, float[]> initial = new java.util.IdentityHashMap<>();
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
		//#if MC>=11700
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
		//#else
		//$$ // Before 1.17 the game's model is built by its constructor; the ears and the cape are drawn apart
		//$$ // by the game and left out here.
		//$$ return new Figure(new PlayerModel<>(0, slim));
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
		//#if MC<11904
		//$$ // Before 1.19.4 the game puts see-through faces in order by how far each is from the origin of the
		//$$ // positions it is given, which for a figure on a screen is the screen's corner: the order changes
		//$$ // with the turn more than with the depth, and some see-through pixels hide what lies behind them
		//$$ // at some angles and not at others. The positions are shifted so that the origin lies far in
		//$$ // front of the figure, and the view shifts them back: the far faces then come first.
		//#if MC>=11700
		//$$ com.mojang.blaze3d.vertex.PoseStack view = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
		//$$ view.pushPose();
		//$$ view.translate((x0 + x1) / 2f, y0 + pivot, SORTED_FROM);
		//$$ com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
		//#else
		//$$ com.mojang.blaze3d.systems.RenderSystem.pushMatrix();
		//$$ com.mojang.blaze3d.systems.RenderSystem.translatef((x0 + x1) / 2f, y0 + pivot, SORTED_FROM);
		//#endif
		//$$ pose.translate(-(x0 + x1) / 2f, -(y0 + pivot), -SORTED_FROM);
		//#endif
		//$$ pose.translate((x0 + x1) / 2f, y0 + pivot, 50);
		//$$ // The tilt moves the positions alone, as 1.21.11's renderer tilts its view rather than the model:
		//$$ // the normals keep the light they have untilted, and the figure seen from above or below is not
		//$$ // lit any differently from the front.
		//#if MC>=11903
		//$$ pose.last().pose().rotate(com.mojang.math.Axis.XP.rotationDegrees(rotationX));
		//#else
		//$$ pose.last().pose().multiply(com.mojang.math.Vector3f.XP.rotationDegrees(rotationX));
		//#endif
		//$$ pose.translate(0, y1 - y0 - pivot, 0);
		//#if MC>=12002
		//$$ pose.scale(scale, scale, -scale);
		//#else
		//$$ // In 1.20.1 a scale with one side negative breaks the normals (an inverse cube root of a negative
		//$$ // number), and the faces jump between dark and light as the figure turns. The game's inventory
		//$$ // scales the positions alone, and its lights are set for normals left as they are.
		//#if MC>=11903
		//$$ pose.mulPoseMatrix(new org.joml.Matrix4f().scaling(scale, scale, -scale));
		//#elseif MC>=11700
		//$$ pose.mulPoseMatrix(com.mojang.math.Matrix4f.createScaleMatrix(scale, scale, -scale));
		//#else
		//$$ pose.last().pose().multiply(com.mojang.math.Matrix4f.createScaleMatrix(scale, scale, -scale));
		//#endif
		//#endif
		//#if MC>=11903
		//$$ pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-rotationY));
		//#else
		//$$ pose.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(-rotationY));
		//#endif
		//$$ pose.translate(0, -1.601f, 0);
		//#if MC>=11700
		//$$ com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
		//$$ cover(figure.root);
		//#else
		//$$ // Before 1.17 the lights are the fixed pipeline's, and the game has no setting for a player in a
		//$$ // screen: they are set to the two it lights one with from 1.17 on (Lighting.INVENTORY_DIFFUSE_LIGHT_0
		//$$ // and _1), in the view's own space, as a shader takes them.
		//$$ com.mojang.math.Matrix4f same = new com.mojang.math.Matrix4f();
		//$$ same.setIdentity();
		//$$ com.mojang.math.Vector3f first = new com.mojang.math.Vector3f(0.2f, -1.0f, -1.0f);
		//$$ first.normalize();
		//$$ com.mojang.math.Vector3f second = new com.mojang.math.Vector3f(-0.2f, -1.0f, 0.0f);
		//$$ second.normalize();
		//$$ com.mojang.blaze3d.systems.RenderSystem.setupLevelDiffuseLighting(first, second, same);
		//$$ cover(figure);
		//#endif
		//#if MC>=11900
		//$$ figure.root.render(pose, graphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(texture)),
		//$$ 	0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		//#elseif MC>=11700
		//$$ drawDeeper(figure.root, pose, com.mopicmp.mosaicary.client.platform.Gfx.bufferSource(graphics).getBuffer(
		//$$ 	net.minecraft.client.renderer.RenderType.entityTranslucent(texture)), 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		//#else
		//$$ drawDeeper(figure, pose, com.mopicmp.mosaicary.client.platform.Gfx.bufferSource(graphics).getBuffer(
		//$$ 	net.minecraft.client.renderer.RenderType.entityTranslucent(texture)), 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		//#endif
		//$$ graphics.flush();
		//$$ clearDepth();
		//$$ com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
		//#if MC<11904
		//#if MC>=11700
		//$$ view.popPose();
		//$$ com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
		//#else
		//$$ com.mojang.blaze3d.systems.RenderSystem.popMatrix();
		//#endif
		//#endif
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
	//#if MC>=11700
	//$$ private static void cover(ModelPart root) {
	//$$ 	for (String[] layer : LAYERS) root.getChild(layer[0]).loadPose(root.getChild(layer[1]).storePose());
	//#if MC>=11900
	//$$ 	for (String leg : new String[] { "left_leg", "left_pants" }) root.getChild(leg).zScale = LEG;
	//$$ 	for (String arm : new String[] { "left_arm", "right_arm", "left_sleeve", "right_sleeve" }) root.getChild(arm).zScale = ARM;
	//$$ 	root.getChild("jacket").zScale = JACKET;
	//#endif
	//$$ }
	//#else
	//$$ private static void cover(Figure figure) {
	//$$ 	for (String[] layer : LAYERS) figure.parts.get(layer[0]).copyFrom(figure.parts.get(layer[1]));
	//$$ }
	//#endif
	//#endif
	//#if MC<11900
	//$$ /** The parts drawn a hair deeper (see model), and how much. */
	//$$ private static final java.util.Map<String, Float> DEEPER = java.util.Map.of("left_leg", LEG, "left_pants", LEG,
	//$$ 	"left_arm", ARM, "right_arm", ARM, "left_sleeve", ARM, "right_sleeve", ARM, "jacket", JACKET);
	//$$
	//$$ /**
	//$$  * The figure drawn with some parts a hair deeper (see model). Before 1.19 a part has no size of its
	//$$  * own, so those are left out of the rest and drawn after it, each in a pose stretched along its depth.
	//$$  */
	//#if MC>=11700
	//$$ private static void drawDeeper(ModelPart root, com.mojang.blaze3d.vertex.PoseStack pose,
	//$$ 		com.mojang.blaze3d.vertex.VertexConsumer out, int light, int overlay) {
	//$$ 	java.util.Map<String, Boolean> shown = new java.util.HashMap<>();
	//$$ 	for (String name : DEEPER.keySet()) {
	//$$ 		ModelPart part = root.getChild(name);
	//$$ 		shown.put(name, part.visible);
	//$$ 		part.visible = false;
	//$$ 	}
	//$$ 	root.render(pose, out, light, overlay);
	//$$ 	DEEPER.forEach((name, depth) -> {
	//$$ 		ModelPart part = root.getChild(name);
	//$$ 		part.visible = shown.get(name);
	//$$ 		if (!part.visible) return;
	//$$ 		pose.pushPose();
	//$$ 		root.translateAndRotate(pose);
	//$$ 		part.translateAndRotate(pose);
	//$$ 		pose.scale(1, 1, depth);
	//$$ 		net.minecraft.client.model.geom.PartPose kept = part.storePose();
	//$$ 		part.loadPose(net.minecraft.client.model.geom.PartPose.ZERO);
	//$$ 		part.render(pose, out, light, overlay);
	//$$ 		part.loadPose(kept);
	//$$ 		pose.popPose();
	//$$ 	});
	//$$ }
	//#else
	//$$ private static void drawDeeper(Figure figure, com.mojang.blaze3d.vertex.PoseStack pose,
	//$$ 		com.mojang.blaze3d.vertex.VertexConsumer out, int light, int overlay) {
	//$$ 	for (java.util.Map.Entry<String, ModelPart> one : figure.parts.entrySet()) {
	//$$ 		ModelPart part = one.getValue();
	//$$ 		Float depth = DEEPER.get(one.getKey());
	//$$ 		if (depth == null) {
	//$$ 			part.render(pose, out, light, overlay);
	//$$ 			continue;
	//$$ 		}
	//$$ 		if (!part.visible) continue;
	//$$ 		pose.pushPose();
	//$$ 		part.translateAndRotate(pose);
	//$$ 		pose.scale(1, 1, depth);
	//$$ 		float[] kept = poseOf(part);
	//$$ 		setPose(part, new float[6]);
	//$$ 		part.render(pose, out, light, overlay);
	//$$ 		setPose(part, kept);
	//$$ 		pose.popPose();
	//$$ 	}
	//$$ }
	//$$
	//$$ /** A part's place and turn, which before 1.17 it has no pose object for. */
	//$$ private static float[] poseOf(ModelPart part) {
	//$$ 	return new float[] { part.x, part.y, part.z, part.xRot, part.yRot, part.zRot };
	//$$ }
	//$$
	//$$ private static void setPose(ModelPart part, float[] pose) {
	//$$ 	part.x = pose[0];
	//$$ 	part.y = pose[1];
	//$$ 	part.z = pose[2];
	//$$ 	part.xRot = pose[3];
	//$$ 	part.yRot = pose[4];
	//$$ 	part.zRot = pose[5];
	//$$ }
	//#endif
	//#endif

	public static void rest(Figure figure) {
		//#if MC>=11900
		figure.root.getAllParts().forEach(ModelPart::resetPose);
		//#elseif MC>=11700
		//$$ figure.initial.forEach(ModelPart::loadPose);
		//#else
		//$$ figure.initial.forEach(PlayerPose::setPose);
		//#endif
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
		head.yRot = headYaw(yaw);
		head.xRot = clamp(pitch, 0.7f);
	}

	/** How far a neck turns the head to either side, in radians: 70 degrees. */
	public static final float NECK = 1.22f;

	/** The turn {@link #look} gives the head for a wanted one: as far as a neck turns. */
	public static float headYaw(float yaw) {
		return clamp(yaw, NECK);
	}

	private static ModelPart part(Figure figure, String name) {
		//#if MC>=11700
		try {
			return figure.root.getChild(name);
		} catch (NoSuchElementException missing) {
			return null;
		}
		//#else
		//$$ return figure.parts.get(name);
		//#endif
	}

	private static float clamp(float value, float most) {
		return Math.max(-most, Math.min(most, value));
	}
}
