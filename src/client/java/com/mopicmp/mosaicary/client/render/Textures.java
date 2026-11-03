package com.mopicmp.mosaicary.client.render;

import java.util.function.Supplier;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;

/** Textures made from a picture in memory. */
public final class Textures {

	private Textures() { }

	/** The picture as a texture; the label names it in the game's lists of textures, where it has them. */
	public static DynamicTexture of(Supplier<String> label, NativeImage image) {
		//#if MC>=12111
		return new DynamicTexture(label, image);
		//#else
		//$$ return new DynamicTexture(image);
		//#endif
	}
}
