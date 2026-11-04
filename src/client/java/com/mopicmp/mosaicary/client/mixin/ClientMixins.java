package com.mopicmp.mosaicary.client.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Adds the client's mixins that are built for some game versions only: those of the worn models
 * where the model catalogue is built (from 1.21.11 on), and the order of drawing screens where the
 * game draws them otherwise (1.21.1 and 1.20.1; before 1.20 the loaders' events do it). A mixin
 * named in the configuration file but missing from the jar stops the game. The mixins every
 * version has are in the file.
 */
public final class ClientMixins implements IMixinConfigPlugin {

	@Override
	public List<String> getMixins() {
		if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) return null;
		//#if MC>=12111
		return List.of("AvatarRendererMixin", "EntityRenderStateMixin", "GuiGraphicsExtractorAccessor", "LivingEntityRendererMixin");
		//#elseif MC>=12000
		//$$ return List.of("ScreenOrderMixin");
		//#else
		//$$ return List.of();
		//#endif
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
