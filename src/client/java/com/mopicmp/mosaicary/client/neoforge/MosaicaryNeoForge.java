//#if NEOFORGE>=1
//$$ package com.mopicmp.mosaicary.client.neoforge;
//$$
//$$ import com.mopicmp.mosaicary.Mosaicary;
//$$ import com.mopicmp.mosaicary.client.MosaicaryClient;
//$$ import com.mopicmp.mosaicary.client.platform.ClientPlatform;
//$$ import com.mopicmp.mosaicary.client.ui.SettingsScreen;
//$$ import com.mopicmp.mosaicary.platform.Platform;
//$$
//$$ import net.neoforged.api.distmarker.Dist;
//$$ import net.neoforged.bus.api.IEventBus;
//$$ import net.neoforged.fml.ModContainer;
//$$ import net.neoforged.fml.common.Mod;
//$$ import net.neoforged.fml.loading.FMLEnvironment;
//$$ import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
//$$
//$$ /**
//$$  * The mod's entry point on NeoForge: the common half everywhere, the client half on a client,
//$$  * and the settings screen behind the mod list's "Config" button.
//$$  *
//$$  * It lives in the client source set because it has to reach both halves; on Fabric the two
//$$  * entry points are Mosaicary and MosaicaryClient themselves, named in fabric.mod.json.
//$$  */
//$$ @Mod(Mosaicary.ID)
//$$ public final class MosaicaryNeoForge {
//$$
//$$ 	public MosaicaryNeoForge(IEventBus modBus, ModContainer container) {
//$$ 		Platform.listen(modBus);
//$$ 		new Mosaicary().onInitialize();
//#if MC>=12111
//$$ 		if (FMLEnvironment.getDist() == Dist.CLIENT) Client.init(modBus, container);
//#else
//$$ 		if (FMLEnvironment.dist == Dist.CLIENT) Client.init(modBus, container);
//#endif
//$$ 	}
//$$
//$$ 	/** Apart, so that a server never loads the client's classes. */
//$$ 	private static final class Client {
//$$ 		static void init(IEventBus modBus, ModContainer container) {
//$$ 			ClientPlatform.listen(modBus);
//$$ 			new MosaicaryClient().onInitializeClient();
//$$ 			container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
//$$ 		}
//$$ 	}
//$$ }
//#endif
