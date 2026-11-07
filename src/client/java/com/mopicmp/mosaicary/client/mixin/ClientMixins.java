package com.mopicmp.mosaicary.client.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Adds the client's mixins that are built for some game versions only: those of the worn models
 * where the model catalogue is built (from 1.21.11 on), and the order of drawing screens where the
 * game draws them otherwise (1.21.1 and 1.20.1; before 1.20 the loaders' events do it); and those
 * joining in Female Gender Mod's drawing, built for its lines the jar is compiled against and added
 * only when it and 3D Skin Layers are installed, as they name its classes. A mixin named in the configuration file but
 * missing from the jar stops the game. The mixins every version has are in the file.
 */
public final class ClientMixins implements IMixinConfigPlugin {

	@Override
	public List<String> getMixins() {
		if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) return null;
		List<String> mixins = new ArrayList<>();
		//#if MC>=12111
		mixins.addAll(List.of("AvatarRendererMixin", "EntityRenderStateMixin", "GuiGraphicsExtractorAccessor", "LivingEntityRendererMixin"));
		//#elseif MC>=12000
		//$$ mixins.add("ScreenOrderMixin");
		//#endif
		// Only with 3D Skin Layers as well: they draw the outer layer of the breasts with its cubes.
		//#if GENDER>=1
		//#if MC>=260100
		if (loaded("female_gender_mod") && loaded("skinlayers3d")) {
		//#else
		//$$ if (loaded("wildfire_gender") && loaded("skinlayers3d")) {
		//#endif
			mixins.add("GenderLayerMixin");
			// From 1.21.2 on the jacket is cut where 3D Skin Layers hangs it on the model; before,
			// where it keeps it for the player (GenderBreasts.cutTorso).
			//#if MC>=12111
			mixins.add("PlayerModelMixin");
			//#endif
		}
		//#endif
		return mixins;
	}

	/** Whether a mod is installed; asked as the mixins are being set up, before the game's own mod list. */
	private static boolean loaded(String modId) {
		//#if FABRIC>=1
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
		//#elseif NEOFORGE>=1
		//$$ return net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId) != null;
		//#else
		//$$ return net.minecraftforge.fml.loading.LoadingModList.get().getModFileById(modId) != null;
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
