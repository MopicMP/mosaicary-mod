//#if FORGE>=1
//$$ package com.mopicmp.mosaicary.client.forge;
//$$
//$$ import com.mopicmp.mosaicary.Mosaicary;
//$$ import com.mopicmp.mosaicary.client.MosaicaryClient;
//$$ import com.mopicmp.mosaicary.client.platform.ClientPlatform;
//$$ import com.mopicmp.mosaicary.client.ui.SettingsScreen;
//$$
//$$ import net.minecraftforge.api.distmarker.Dist;
//#if MC>=11900
//$$ import net.minecraftforge.client.ConfigScreenHandler;
//#endif
//$$ import net.minecraftforge.eventbus.api.IEventBus;
//$$ import net.minecraftforge.fml.ModLoadingContext;
//$$ import net.minecraftforge.fml.common.Mod;
//$$ import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//$$ import net.minecraftforge.fml.loading.FMLEnvironment;
//$$
//$$ /**
//$$  * The mod's entry point on Forge: the common half everywhere, the client half on a client, and
//$$  * the settings screen behind the mod list's "Config" button. As MosaicaryNeoForge on NeoForge,
//$$  * it lives in the client source set because it has to reach both halves.
//$$  */
//$$ @Mod(Mosaicary.ID)
//$$ public final class MosaicaryForge {
//$$
//$$ 	public MosaicaryForge() {
//$$ 		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
//#if MC<11800
//$$ 		// mods.toml has no displayTest before 1.18, so what IGNORE_ALL_VERSION says there is said
//$$ 		// here: the server tells no version, and the client takes the server with or without the mod.
//$$ 		ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.fml.ExtensionPoint.DISPLAYTEST,
//$$ 			() -> org.apache.commons.lang3.tuple.Pair.of(
//$$ 				() -> net.minecraftforge.fml.network.FMLNetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
//#endif
//$$ 		new Mosaicary().onInitialize();
//$$ 		if (FMLEnvironment.dist == Dist.CLIENT) Client.init(modBus);
//$$ 	}
//$$
//$$ 	/** Apart, so that a server never loads the client's classes. */
//$$ 	private static final class Client {
//$$ 		static void init(IEventBus modBus) {
//$$ 			ClientPlatform.listen(modBus);
//$$ 			new MosaicaryClient().onInitializeClient();
//#if MC>=11900
//$$ 			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
//$$ 				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new SettingsScreen(parent)));
//#elseif MC>=11700
//$$ 			// The settings button in the list of mods is named after the old config screens before 1.19.
//$$ 			ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigGuiHandler.ConfigGuiFactory.class,
//$$ 				() -> new net.minecraftforge.client.ConfigGuiHandler.ConfigGuiFactory((minecraft, parent) -> new SettingsScreen(parent)));
//#else
//$$ 			// Before 1.17 the extension is a fixed point of Forge's own, and its value the screen's maker.
//$$ 			ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.fml.ExtensionPoint.CONFIGGUIFACTORY,
//$$ 				() -> (minecraft, parent) -> new SettingsScreen(parent));
//#endif
//$$ 		}
//$$ 	}
//$$ }
//#endif
