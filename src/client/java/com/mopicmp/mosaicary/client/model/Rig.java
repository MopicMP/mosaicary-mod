package com.mopicmp.mosaicary.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which bones of a model are a humanoid's parts.
 *
 * Found by name. The spellings come from a measurement over the seed sample (370
 * humanoid-looking files out of 2170): {@code head}, {@code body}/{@code torso}/
 * {@code upper_body}, {@code right_arm}/{@code RightArm}/{@code armRight}, GeckoLib's
 * {@code bipedLeftLeg}, {@code h_head}, the {@code CustomLeftArm} family of rigs made
 * for player animations, and so on. Names are compared without case
 * and without anything that is not a letter, so {@code Right_Arm},
 * {@code rightArm} and {@code right arm} are one name. Unsided names
 * ({@code arm}, {@code leg2}) are ambiguous and left alone, as are the
 * {@code armor*} attachment bones. Such a model has one bone per part at most.
 *
 * A Figura avatar follows Figura's own rule instead ({@link Figura#part}): every
 * group whose name starts with a part's keyword follows that part, so a part may
 * have several bones; a group inside another that already follows the same part
 * is left to move with it.
 *
 * @param bones the humanoid parts found, each with its bones, the first being the main one
 */
public record Rig(Map<Part, List<Bone>> bones) {

	public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

	private static final Map<Part, List<String>> PART_NAMES = Map.of(
		Part.HEAD, List.of("head", "bipedhead", "hhead", "customhead"),
		Part.BODY, List.of("body", "torso", "upperbody", "bipedbody", "hbody", "chest", "custombody"),
		Part.RIGHT_ARM, List.of("rightarm", "bipedrightarm", "armright", "hrightarm", "customrightarm"),
		Part.LEFT_ARM, List.of("leftarm", "bipedleftarm", "armleft", "hleftarm", "customleftarm"),
		Part.RIGHT_LEG, List.of("rightleg", "bipedrightleg", "legright", "hrightleg", "customrightleg"),
		Part.LEFT_LEG, List.of("leftleg", "bipedleftleg", "legleft", "hleftleg", "customleftleg"));

	public static Rig of(BbModel model) {
		if ("figura".equals(model.format())) return ofFigura(model);
		Map<Part, List<Bone>> found = new EnumMap<>(Part.class);
		for (Part part : Part.values()) {
			for (String wanted : PART_NAMES.get(part)) {
				for (Bone bone : model.bones()) {
					if (letters(bone.name()).equals(wanted)) {
						found.put(part, List.of(bone));
						break;
					}
				}
				if (found.containsKey(part)) break;
			}
		}
		return new Rig(found);
	}

	private static Rig ofFigura(BbModel model) {
		Map<Part, List<Bone>> found = new EnumMap<>(Part.class);
		for (Bone bone : model.bones()) {
			Part part = Figura.part(bone.name());
			if (part == null) continue;
			boolean nested = false;
			for (Bone up = bone.parent(); up != null && !nested; up = up.parent()) nested = Figura.part(up.name()) == part;
			if (!nested) found.computeIfAbsent(part, p -> new ArrayList<>()).add(bone);
		}
		found.replaceAll((part, list) -> List.copyOf(list));
		return new Rig(found);
	}

	/** A name without case and without anything that is not a letter. */
	static String letters(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
	}

	/** The part's main bone, or null. */
	public Bone bone(Part part) {
		List<Bone> list = bones.get(part);
		return list == null || list.isEmpty() ? null : list.get(0);
	}

	/** Every bone that follows the part. */
	public List<Bone> all(Part part) {
		return bones.getOrDefault(part, List.of());
	}

	/** Whether {@code ancestor} is {@code bone} or one of its parents. */
	public static boolean contains(Bone ancestor, Bone bone) {
		for (Bone at = bone; at != null; at = at.parent()) if (at == ancestor) return true;
		return false;
	}

	/** Whether a bone is one of the arms or hangs from one. */
	public boolean onArm(Bone bone) {
		return onArm(Roles.Side.LEFT, bone) || onArm(Roles.Side.RIGHT, bone);
	}

	/** Whether a bone is the model's left (or right) arm or hangs from it. */
	public boolean onArm(Roles.Side side, Bone bone) {
		for (Bone one : all(side == Roles.Side.LEFT ? Part.LEFT_ARM : Part.RIGHT_ARM)) if (contains(one, bone)) return true;
		return false;
	}
}
