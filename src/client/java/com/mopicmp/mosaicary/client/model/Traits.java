package com.mopicmp.mosaicary.client.model;

import java.util.Map;

/**
 * How a Blockbench format behaves where it matters to reading and drawing it.
 *
 * Nine flags out of the forty a Blockbench format definition has, the same nine
 * the website's viewer uses (site/shared/bbmodel.ts), so that a model looks the
 * same in the game as on its page. A format not listed here is read the way
 * Blockbench reads one it does not know, which is as {@code free}.
 *
 * @param boneRig              groups are bones: they carry a pivot and rotation, and children move with them
 * @param singleTexture        every face shows the one default texture, whatever its own field says
 * @param singleTextureDefault faces without a texture of their own fall back to the default one
 * @param perTextureUv         each texture has its own UV size rather than the project's
 * @param perGroupTexture      a group can carry a texture for everything inside it
 * @param frontOnly            faces draw on one side only
 * @param animatedTextures     a texture taller than its UV space is frames stacked one above another
 * @param rotateCubes          cubes turn freely, and so can be told to grow back what turning takes off
 * @param boxUvFloat           box UV is laid out at a cube's true size rather than whole pixels
 */
public record Traits(boolean boneRig, boolean singleTexture, boolean singleTextureDefault,
		boolean perTextureUv, boolean perGroupTexture, boolean frontOnly, boolean animatedTextures,
		boolean rotateCubes, boolean boxUvFloat) {

	private static final Traits FREE = new Traits(true, false, false, true, false, false, true, true, false);

	private static final Map<String, Traits> KNOWN = Map.ofEntries(
		Map.entry("free", FREE),
		Map.entry("java_block", new Traits(false, false, false, false, false, true, true, true, false)),
		Map.entry("bedrock", new Traits(true, true, false, false, false, false, true, true, false)),
		Map.entry("bedrock_block", new Traits(true, false, true, false, false, false, true, true, false)),
		Map.entry("bedrock_old", new Traits(true, true, false, false, false, false, true, false, false)),
		Map.entry("modded_entity", new Traits(true, true, false, false, false, false, false, true, true)),
		// GeckoLib's own format, saved as geckolib_model now and as
		// animated_entity_model by its older versions.
		Map.entry("geckolib_model", new Traits(true, true, false, false, false, false, false, true, false)),
		Map.entry("animated_entity_model", new Traits(true, true, false, false, false, false, false, true, false)),
		Map.entry("optifine_entity", new Traits(true, false, true, true, true, false, false, false, false)),
		Map.entry("optifine_part", new Traits(true, false, true, true, true, false, false, false, false)),
		Map.entry("skin", new Traits(true, true, false, false, false, false, false, false, false)),
		Map.entry("image", new Traits(false, true, false, true, false, false, true, false, false)));

	public static Traits of(String format) {
		return KNOWN.getOrDefault(format, FREE);
	}
}
