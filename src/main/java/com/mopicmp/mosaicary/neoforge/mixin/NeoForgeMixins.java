//#if NEOFORGE>=1
//$$ package com.mopicmp.mosaicary.neoforge.mixin;
//$$
//$$ import java.util.List;
//$$ import java.util.Set;
//$$
//$$ import org.objectweb.asm.tree.ClassNode;
//$$ import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
//$$ import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
//$$
//$$ /**
//$$  * Adds {@link NetworkRegistryMixin} where it is built: from 1.21.11 on. 1.21.1 has no step that
//$$  * looks for the player's spawn while the connection is set up, so the channel list NeoForge takes
//$$  * on the network thread does not meet the server's thread there. A mixin named in the
//$$  * configuration file but missing from the jar stops the game.
//$$  */
//$$ public final class NeoForgeMixins implements IMixinConfigPlugin {
//$$
//$$ 	@Override
//$$ 	public List<String> getMixins() {
//#if MC>=12111
//$$ 		return List.of("NetworkRegistryMixin");
//#else
//$$ 		return null;
//#endif
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public void onLoad(String mixinPackage) {
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public String getRefMapperConfig() {
//$$ 		return null;
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
//$$ 		return true;
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
//$$ 	}
//$$ }
//#endif
