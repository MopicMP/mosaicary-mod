package com.mopicmp.mosaicary.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Humanoid parts by name, animation roles, the action priority and the motion built from them. */
class RigTest {

	private static String humanoid(String head, String body, String rightArm, String leftArm, String rightLeg,
			String leftLeg, String animations) {
		StringBuilder groups = new StringBuilder();
		StringBuilder outliner = new StringBuilder();
		StringBuilder elements = new StringBuilder();
		String[] names = { head, body, rightArm, leftArm, rightLeg, leftLeg };
		for (int i = 0; i < names.length; i++) {
			String comma = i > 0 ? "," : "";
			groups.append(comma).append("{\"name\":\"").append(names[i]).append("\",\"uuid\":\"g").append(i)
				.append("\",\"origin\":[0,").append(i).append(",0]}");
			outliner.append(comma).append("{\"uuid\":\"g").append(i).append("\",\"children\":[\"c").append(i).append("\"]}");
			elements.append(comma).append("{\"type\":\"cube\",\"uuid\":\"c").append(i)
				.append("\",\"from\":[0,0,0],\"to\":[1,1,1],\"faces\":{\"up\":{\"uv\":[0,0,1,1],\"texture\":0}}}");
		}
		return "{\"meta\":{\"format_version\":\"5.0\",\"model_format\":\"free\"},\"elements\":[" + elements + "],\"groups\":["
			+ groups + "],\"outliner\":[" + outliner + "],\"textures\":[{\"uuid\":\"t\"}],\"animations\":[" + animations + "]}";
	}

	private static String clip(String name, String bone) {
		return clip(name, 30, bone);
	}

	/** An animation that turns the given bones about X by {@code degrees}, held. */
	private static String clip(String name, int degrees, String... bones) {
		StringBuilder animators = new StringBuilder();
		for (String bone : bones) {
			if (!animators.isEmpty()) animators.append(',');
			animators.append("\"").append(bone).append("\":{\"name\":\"x\",\"type\":\"bone\",\"keyframes\":[{\"channel\":\"rotation\",")
				.append("\"time\":0,\"data_points\":[{\"x\":").append(degrees).append(",\"y\":0,\"z\":0}]}]}");
		}
		return "{\"name\":\"" + name + "\",\"loop\":\"loop\",\"length\":1,\"animators\":{" + animators + "}}";
	}

	@Test
	void findsPartsUnderTheMeasuredSpellings() throws Exception {
		Rig snake = Rig.of(BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "")));
		Rig camel = Rig.of(BbParser.parse(humanoid("Head", "Torso", "RightArm", "LeftArm", "RightLeg", "LeftLeg", "")));
		Rig gecko = Rig.of(BbParser.parse(humanoid("bipedHead", "bipedBody", "bipedRightArm", "bipedLeftArm",
			"bipedRightLeg", "bipedLeftLeg", "")));
		Rig reversed = Rig.of(BbParser.parse(humanoid("h_head", "upper_body", "armRight", "armLeft", "legRight", "legLeft", "")));
		for (Rig rig : new Rig[] { snake, camel, gecko, reversed }) {
			for (Rig.Part part : Rig.Part.values()) assertNotNull(rig.bone(part), part.name());
		}
		assertEquals("RightArm", camel.bone(Rig.Part.RIGHT_ARM).name());
	}

	@Test
	void unsidedAndArmourBonesAreLeftAlone() throws Exception {
		Rig rig = Rig.of(BbParser.parse(humanoid("head", "body", "arm", "arm2", "armorRightLeg", "leg", "")));
		assertNull(rig.bone(Rig.Part.RIGHT_ARM));
		assertNull(rig.bone(Rig.Part.LEFT_ARM));
		assertNull(rig.bone(Rig.Part.RIGHT_LEG));
		assertNull(rig.bone(Rig.Part.LEFT_LEG));
	}

	@Test
	void rolesAreFilledInFromNames() throws Exception {
		String animations = String.join(",", clip("animation.model.idle", "g0"), clip("move.walk", "g4"),
			clip("Sprinting", "g4"), clip("jump", "g1"), clip("attack", "g2"), clip("sit", "g5"));
		BbModel model = BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", animations));
		Roles roles = Roles.auto(model);
		assertEquals("animation.model.idle", roles.get(Roles.Action.IDLE));
		assertEquals("move.walk", roles.get(Roles.Action.WALK));
		assertEquals("Sprinting", roles.get(Roles.Action.SPRINT));
		assertEquals("jump", roles.get(Roles.Action.JUMP));
		assertEquals("attack", roles.get(Roles.Action.SWING));
		assertEquals("sit", roles.get(Roles.Action.RIDE));
		assertEquals(Roles.VANILLA, roles.get(Roles.Action.CROUCH));
		assertEquals(Roles.VANILLA, roles.get(Roles.Action.SWIM));
	}

	@Test
	void playerChoicesOverrideAndOnlyDifferencesAreKept() throws Exception {
		BbModel model = BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg",
			String.join(",", clip("idle", "g0"), clip("dance", "g1"))));
		Roles base = Roles.auto(model);
		Map<String, String> chosen = new LinkedHashMap<>();
		chosen.put("idle", "dance");
		chosen.put("walk", Roles.NOTHING);
		chosen.put("swim", "no such animation");
		chosen.put("flying", "dance");
		Roles mine = base.with(chosen, model);
		assertEquals("dance", mine.get(Roles.Action.IDLE));
		assertEquals(Roles.NOTHING, mine.get(Roles.Action.WALK));
		// A choice of a missing animation or an unknown action is ignored.
		assertEquals(Roles.VANILLA, mine.get(Roles.Action.SWIM));
		assertEquals(Map.of("idle", "dance", "walk", Roles.NOTHING), mine.differencesFrom(base));
	}

	@Test
	void travellingJsonIsBoundedAndCleaned() {
		Map<String, String> roles = new LinkedHashMap<>();
		roles.put("idle", "stand");
		roles.put("walk", Roles.VANILLA);
		String json = Roles.toJson(roles);
		assertEquals("{\"idle\":\"stand\",\"walk\":\"@vanilla\"}", json);
		assertEquals(roles, Roles.fromJson(json));
		// Unknown actions and non-string values are dropped.
		assertEquals(Map.of("idle", "x"), Roles.fromJson("{\"idle\":\"x\",\"dance\":\"y\",\"walk\":3}"));
		// Never more than the site's 8 KB, whatever the names.
		Map<String, String> long_ = new LinkedHashMap<>();
		for (String key : Roles.KEYS) long_.put(key, "n".repeat(128));
		assertTrue(Roles.toJson(long_).getBytes(StandardCharsets.UTF_8).length <= Roles.MAX_BYTES);
		assertEquals(Map.of(), Roles.fromJson("{\"idle\":\"" + "x".repeat(3000) + "\"}"));
		assertEquals(Map.of(), Roles.fromJson("{\"idle\":\"" + "x".repeat(9000) + "\"}"));
		// Arm layers travel too; roles this version does not know are left out.
		assertEquals(Map.of("walk_left_arm", "wave"), Roles.fromJson("{\"walk_left_arm\":\"wave\",\"fp_hold_left\":\"x\"}"));
	}

	/** A state from the flags that matter for one test, the rest as standing on the ground. */
	private static Motion.State state(String... flags) {
		java.util.Set<String> on = java.util.Set.of(flags);
		return new Motion.State(on.contains("crouching"), on.contains("sprinting"), !on.contains("air"), on.contains("swimming"),
			on.contains("water"), on.contains("flying"), on.contains("sleeping"), on.contains("riding"), on.contains("spinning"),
			on.contains("back"), on.contains("dying"));
	}

	@Test
	void actionPriority() {
		assertEquals(Roles.Action.SWIM, Motion.action(state("sprinting", "air", "swimming", "water"), 1));
		assertEquals(Roles.Action.CRAWL, Motion.action(state("swimming"), 0));
		assertEquals(Roles.Action.CRAWL_WALK, Motion.action(state("swimming"), 0.5f));
		assertEquals(Roles.Action.CRAWL_BACK, Motion.action(state("swimming", "back"), 0.5f));
		assertEquals(Roles.Action.CROUCH_WALK, Motion.action(state("crouching"), 0.5f));
		assertEquals(Roles.Action.CROUCH_BACK, Motion.action(state("crouching", "back"), 0.5f));
		assertEquals(Roles.Action.CROUCH, Motion.action(state("crouching"), 0));
		assertEquals(Roles.Action.JUMP, Motion.action(state("sprinting", "air"), 1));
		assertEquals(Roles.Action.JUMP_BACK, Motion.action(state("air", "back"), 1));
		assertEquals(Roles.Action.SPRINT, Motion.action(state("sprinting"), 1));
		assertEquals(Roles.Action.WALK, Motion.action(Motion.State.STANDING, 0.5f));
		assertEquals(Roles.Action.WALK_BACK, Motion.action(state("back"), 0.5f));
		// Standing still is standing, whichever way the last step went.
		assertEquals(Roles.Action.IDLE, Motion.action(state("back"), 0));
		assertEquals(Roles.Action.SLEEP, Motion.action(state("crouching", "swimming", "flying", "sleeping", "riding"), 0));
		assertEquals(Roles.Action.RIDE, Motion.action(state("riding"), 0));
		assertEquals(Roles.Action.SPIN, Motion.action(state("spinning", "air", "flying"), 1));
		assertEquals(Roles.Action.DEATH, Motion.action(state("dying", "sleeping"), 0));
	}

	@Test
	void armLayersItemsAndHurtLayOverTheBody() throws Exception {
		// g1 body, g2 right arm, g3 left arm, g4 right leg.
		BbModel model = BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", String.join(",",
			clip("walk", 10, "g4", "g3"), clip("walk_left_arm", 40, "g3", "g1"), clip("hold_item_left", 70, "g3"),
			clip("hurt", 90, "g1"))));
		Rig rig = Rig.of(model);
		Roles roles = Roles.auto(model);
		assertEquals("walk_left_arm", roles.layer(Roles.Action.WALK, Roles.Side.LEFT));
		assertEquals(Roles.NOTHING, roles.layer(Roles.Action.WALK, Roles.Side.RIGHT));

		// Walking with empty hands: the layer takes the left arm and leaves the body to the walk.
		Posing.Frame walking = new Motion(Motion.State.STANDING, 0, 0, 0, 0.5f, 0, 0).frame(model, rig, roles, null);
		assertEquals(40, walking.of(rig.bone(Rig.Part.LEFT_ARM)).rotation[0], 1e-4);
		assertEquals(0, walking.of(rig.bone(Rig.Part.BODY)).rotation[0], 1e-4);
		assertEquals(10, walking.of(rig.bone(Rig.Part.RIGHT_LEG)).rotation[0], 1e-4);

		// An item in the left hand: its role instead of the layer.
		Motion.Arms holding = new Motion.Arms(Motion.Hand.HOLD, Motion.Hand.EMPTY, Roles.Side.RIGHT, 0, 0);
		Posing.Frame held = new Motion(Motion.State.STANDING, 0, 0, 0, 0.5f, 0, 0, holding, -1).frame(model, rig, roles, null);
		assertEquals(70, held.of(rig.bone(Rig.Part.LEFT_ARM)).rotation[0], 1e-4);

		// Hurt, over everything, for the length of its animation.
		Posing.Frame hurt = new Motion(Motion.State.STANDING, 0, 0, 0, 0.5f, 0, 0, Motion.Arms.EMPTY, 0.2).frame(model, rig, roles, null);
		assertEquals(90, hurt.of(rig.bone(Rig.Part.BODY)).rotation[0], 1e-4);
		Posing.Frame later = new Motion(Motion.State.STANDING, 0, 0, 0, 0.5f, 0, 0, Motion.Arms.EMPTY, 3).frame(model, rig, roles, null);
		assertEquals(0, later.of(rig.bone(Rig.Part.BODY)).rotation[0], 1e-4);
	}

	@Test
	void rolesFallBackToTheirVariant() throws Exception {
		BbModel model = BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg",
			String.join(",", clip("walk", "g4"), clip("walk_left_arm", "g3"), clip("attack", "g2"), clip("animation.boss.death", "g1"))));
		Roles roles = Roles.auto(model);
		assertEquals("walk", roles.get(Roles.Action.WALK_BACK));
		assertEquals("walk", roles.get(Roles.Action.SPRINT));
		assertEquals("walk_left_arm", roles.layer(Roles.Action.WALK_BACK, Roles.Side.LEFT));
		assertEquals("attack", roles.get(Roles.Action.THRUST_RIGHT));
		assertEquals("animation.boss.death", roles.get(Roles.Action.DEATH));
		assertEquals(Roles.VANILLA, roles.get(Roles.Action.HOLD_LEFT));
		assertEquals(Roles.VANILLA, roles.get(Roles.Action.CROUCH_BACK));
	}

	@Test
	void theMageFillsItsRolesByItself() throws Exception {
		String where = System.getProperty("mosaicary.liveModels");
		assumeTrue(where != null, "no live models");
		Path mage = Path.of(where).getParent().resolve("seed/characters/files/09-telekinesis-mage.bbmodel");
		assumeTrue(Files.exists(mage), "no starter models");
		BbModel model = BbParser.parse(Files.readAllBytes(mage));
		Roles roles = Roles.auto(model);
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("idle", "stand");
		expected.put("walk", "walk_forward");
		expected.put("walk_back", "walk_back");
		expected.put("sprint", "walk_forward");
		expected.put("crouch", "sneak");
		expected.put("crouch_walk", "sneak_forward");
		expected.put("crouch_back", "sneak_back");
		expected.put("jump", "air_forward");
		expected.put("jump_back", "air_back");
		expected.put("swim", "swim");
		expected.put("crawl", "crawl");
		expected.put("crawl_walk", "crawl_forward");
		expected.put("crawl_back", "crawl_back");
		expected.put("elytra", "elytra");
		expected.put("spin", "spin_attack");
		expected.put("sleep", "sleep");
		expected.put("ride", "sit");
		for (String side : new String[] { "left", "right" }) {
			expected.put("hold_" + side, "hold_item_" + side);
			expected.put("charge_" + side, "charge_item_" + side);
			expected.put("thrust_" + side, "thrust_item_" + side);
			expected.put("use_self_" + side, "use_on_self_" + side);
			expected.put("walk_" + side + "_arm", "walk_forward_" + side + "_arm");
			expected.put("walk_back_" + side + "_arm", "walk_back_" + side + "_arm");
			expected.put("jump_" + side + "_arm", "air_forward_" + side + "_arm");
			expected.put("jump_back_" + side + "_arm", "air_back_" + side + "_arm");
			expected.put("crouch_" + side + "_arm", "sneak_" + side + "_arm");
			expected.put("ride_" + side + "_arm", "sit_" + side + "_arm");
		}
		for (Map.Entry<String, String> entry : expected.entrySet()) {
			assertEquals(entry.getValue(), roles.get(entry.getKey()), entry.getKey());
		}
		// Every animation but the first-person ones and the unnamed one has a role.
		java.util.Set<String> used = new java.util.HashSet<>(roles.all().values());
		for (Animation clip : model.animations()) {
			if (clip.name().startsWith("viewmodel_") || clip.name().equals("animation")) continue;
			assertTrue(used.contains(clip.name()), clip.name());
		}
		// Its rig names the parts the CustomLeftArm way.
		Rig rig = Rig.of(model);
		assertEquals("CustomLeftArm", rig.bone(Rig.Part.LEFT_ARM).name());
		assertEquals("CustomHead", rig.bone(Rig.Part.HEAD).name());
	}

	@Test
	void vanillaSwingAndHeadLookInBlockbenchSigns() throws Exception {
		BbModel model = BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", ""));
		Rig rig = Rig.of(model);
		// Walk cycle at nought, full speed: the game turns the right arm to cos(pi) = -1
		// radian and the right leg to +1.4; Blockbench's X runs the other way.
		Posing.Frame frame = new Motion(Motion.State.STANDING, 30, 10, 0, 1, 0, 0).frame(model, rig, Roles.auto(model), null);
		assertEquals(Math.toDegrees(1), frame.of(rig.bone(Rig.Part.RIGHT_ARM)).rotation[0], 1e-3);
		assertEquals(-Math.toDegrees(1.4), frame.of(rig.bone(Rig.Part.RIGHT_LEG)).rotation[0], 1e-3);
		// The head looks where the player looks: pitch and yaw, both turned round.
		assertEquals(-10, frame.of(rig.bone(Rig.Part.HEAD)).rotation[0], 1e-6);
		assertEquals(-30, frame.of(rig.bone(Rig.Part.HEAD)).rotation[1], 1e-6);
	}

	@Test
	void swingOverArmsKeepsTheAction() throws Exception {
		// The idle turns the body (g1); the swing turns only the right arm (g2): laid over.
		BbModel model = BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg",
			String.join(",", clip("idle", "g1"), clip("attack", "g2"))));
		Rig rig = Rig.of(model);
		Posing.Frame frame = new Motion(Motion.State.STANDING, 0, 0, 0, 0, 0.5f, 0).frame(model, rig, Roles.auto(model), null);
		assertEquals(30, frame.of(rig.bone(Rig.Part.BODY)).rotation[0], 1e-4);
		assertEquals(30, frame.of(rig.bone(Rig.Part.RIGHT_ARM)).rotation[0], 1e-4);

		// A swing that moves the body takes the whole figure while it lasts.
		BbModel whole = BbParser.parse(humanoid("head", "body", "right_arm", "left_arm", "right_leg", "left_leg",
			String.join(",", clip("idle", "g5"), clip("attack", "g1"))));
		Rig wholeRig = Rig.of(whole);
		Posing.Frame taken = new Motion(Motion.State.STANDING, 0, 0, 0, 0, 0.5f, 0).frame(whole, wholeRig, Roles.auto(whole), null);
		assertEquals(0, taken.of(wholeRig.bone(Rig.Part.LEFT_LEG)).rotation[0], 1e-4);
		assertEquals(30, taken.of(wholeRig.bone(Rig.Part.BODY)).rotation[0], 1e-4);
	}

	@Test
	void liveRiggedModelRoles() throws Exception {
		String where = System.getProperty("mosaicary.liveModels");
		assumeTrue(where != null && Files.exists(Path.of(where, "rS16fadhG31s.bbmodel")), "no live models");
		BbModel model = BbParser.parse(Files.readAllBytes(Path.of(where, "rS16fadhG31s.bbmodel")));
		Roles roles = Roles.auto(model);
		assertEquals("Idle", roles.get(Roles.Action.IDLE));
		assertEquals("walking", roles.get(Roles.Action.WALK));
		assertEquals("run", roles.get(Roles.Action.SPRINT));
		assertEquals("jump", roles.get(Roles.Action.JUMP));
		assertEquals("Swim", roles.get(Roles.Action.SWIM));
		assertEquals("Sleep", roles.get(Roles.Action.SLEEP));
	}
}
