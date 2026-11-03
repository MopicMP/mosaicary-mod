package com.mopicmp.mosaicary.client.platform;

import java.util.function.Consumer;
//#if MC>=260100
import java.util.function.Supplier;
//#elseif MC>=12111
//$$ import java.util.function.Function;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//#endif

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC>=12111
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//#endif
import net.minecraft.resources.Identifier;

//#if FABRIC>=1
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
//#if MC>=12111
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
//#endif
//#elseif NEOFORGE>=1
//$$ import java.util.ArrayList;
//$$ import java.util.List;
//$$
//$$ import com.mopicmp.mosaicary.platform.Platform;
//$$
//$$ import net.minecraft.client.multiplayer.ClientPacketListener;
//$$ import net.neoforged.bus.api.IEventBus;
//$$ import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
//$$ import net.neoforged.neoforge.client.event.ClientTickEvent;
//$$ import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
//$$ import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
//#if MC>=12111
//$$ import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
//#endif
//$$ import net.neoforged.neoforge.client.event.ScreenEvent;
//#if MC>=12111
//$$ import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
//$$ import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//#else
//$$ import net.neoforged.neoforge.event.GameShuttingDownEvent;
//$$ import net.neoforged.neoforge.network.PacketDistributor;
//#endif
//$$ import net.neoforged.neoforge.common.NeoForge;
//#if MC<260100
//$$ import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//$$ import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
//#endif
//#endif

/**
 * What the client half asks of the loader: the game's tick and its end, screens being laid out,
 * joining and leaving a world, keys, client commands, a picture-in-picture renderer for drawing
 * models inside screens, and the client's side of the mod's packets.
 *
 * Like Platform, the only place where loaders differ; everything else calls this. Keys, their
 * category and renderers are registered on NeoForge in events of the mod's bus, so there they are
 * kept until those events come; {@link #listen} hooks them up.
 */
public final class ClientPlatform {

	private ClientPlatform() { }

	/** Something to do once a screen has been laid out, at its first opening and at every resize. */
	public interface ScreenInit {
		void after(Minecraft minecraft, Screen screen, int width, int height);
	}

	/** Client commands, written once for any loader's source type. */
	public interface Commands {
		<S> void register(CommandDispatcher<S> dispatcher);
	}

	/** At the end of every client tick. */
	public static void onTick(Consumer<Minecraft> action) {
		//#if FABRIC>=1
		ClientTickEvents.END_CLIENT_TICK.register(action::accept);
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> action.accept(Minecraft.getInstance()));
		//#endif
	}

	/** When the game is closing. */
	public static void onStopping(Consumer<Minecraft> action) {
		//#if FABRIC>=1
		ClientLifecycleEvents.CLIENT_STOPPING.register(action::accept);
		//#elseif NEOFORGE>=1
		//#if MC>=12111
		//$$ NeoForge.EVENT_BUS.addListener((ClientStoppingEvent event) -> action.accept(event.getClient()));
		//#else
		//$$ // Fired once as the game closes, on the client only for the client itself.
		//$$ NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> action.accept(Minecraft.getInstance()));
		//#endif
		//#endif
	}

	public static void onScreenInit(ScreenInit action) {
		//#if FABRIC>=1
		ScreenEvents.AFTER_INIT.register(action::after);
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
		//$$ 	Screen screen = event.getScreen();
		//$$ 	action.after(Minecraft.getInstance(), screen, screen.width, screen.height);
		//$$ });
		//#endif
	}

	/** On entering a world or a server, once the connection is up. */
	public static void onJoin(Consumer<Minecraft> action) {
		//#if FABRIC>=1
		ClientPlayConnectionEvents.JOIN.register((listener, sender, minecraft) -> action.accept(minecraft));
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> action.accept(Minecraft.getInstance()));
		//#endif
	}

	/** On leaving a world or a server. */
	public static void onLeave(Consumer<Minecraft> action) {
		//#if FABRIC>=1
		ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> action.accept(minecraft));
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> action.accept(Minecraft.getInstance()));
		//#endif
	}

	/** The mod's own section in the controls screen; before 1.21.9 a section is its title's translation key. */
	//#if MC>=12111
	public static KeyMapping.Category keyCategory(Identifier id) {
		//#if FABRIC>=1
		return KeyMapping.Category.register(id);
		//#elseif NEOFORGE>=1
		//$$ KeyMapping.Category category = new KeyMapping.Category(id);
		//$$ categories.add(category);
		//$$ return category;
		//#endif
	}
	//#else
	//$$ public static String keyCategory(ResourceLocation id) {
	//$$ 	return "key.category." + id.getNamespace() + "." + id.getPath();
	//$$ }
	//#endif

	/** Adds a key to the game's controls; returns the same key. */
	public static KeyMapping key(KeyMapping key) {
		//#if FABRIC>=1
		return KeyMappingHelper.registerKeyMapping(key);
		//#elseif NEOFORGE>=1
		//$$ keys.add(key);
		//$$ return key;
		//#endif
	}

	public static void commands(Commands commands) {
		//#if FABRIC>=1
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> commands.register(dispatcher));
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> commands.register(event.getDispatcher()));
		//#endif
	}

	//#if MC>=12111
	/**
	 * A renderer the game uses for picture-in-picture states of this class inside screens. Before
	 * 26.1 a renderer is made with the buffers it draws into. Only the model catalogue's previews
	 * use it, which are built from 1.21.11 on.
	 */
	public static <S extends PictureInPictureRenderState> void pictureInPicture(Class<S> state,
	//#if MC>=260100
			Supplier<PictureInPictureRenderer<S>> renderer) {
	//#else
	//$$ 		Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<S>> renderer) {
	//#endif
		//#if FABRIC>=1
		//#if MC>=260100
		PictureInPictureRendererRegistry.register(context -> renderer.get());
		//#else
		//$$ SpecialGuiElementRegistry.register(context -> renderer.apply(context.vertexConsumers()));
		//#endif
		//#elseif NEOFORGE>=1
		//$$ renderers.add(event -> event.register(state, renderer));
		//#endif
	}
	//#endif

	// ------------------------------------------------------------------ packets

	/** What the client does with a packet of this type from the server. */
	public static <T extends CustomPacketPayload> void onReceive(CustomPacketPayload.Type<T> type, Consumer<T> reader) {
		//#if FABRIC>=1
		ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> reader.accept(payload));
		//#elseif NEOFORGE>=1
		//$$ Platform.clientReader(type, reader);
		//#endif
	}

	/** Whether the server reads packets of this type, that is, has the mod. */
	public static boolean canSend(CustomPacketPayload.Type<?> type) {
		//#if FABRIC>=1
		return ClientPlayNetworking.canSend(type);
		//#elseif NEOFORGE>=1
		//$$ ClientPacketListener connection = Minecraft.getInstance().getConnection();
		//$$ return connection != null && connection.hasChannel(type);
		//#endif
	}

	public static void send(CustomPacketPayload payload) {
		//#if FABRIC>=1
		ClientPlayNetworking.send(payload);
		//#elseif NEOFORGE>=1
		//#if MC>=12111
		//$$ ClientPacketDistributor.sendToServer(payload);
		//#else
		//$$ PacketDistributor.sendToServer(payload);
		//#endif
		//#endif
	}

	//#if NEOFORGE>=1
	//$$ private static final List<KeyMapping> keys = new ArrayList<>();
	//#if MC>=12111
	//$$ private static final List<KeyMapping.Category> categories = new ArrayList<>();
	//$$ private static final List<Consumer<RegisterPictureInPictureRenderersEvent>> renderers = new ArrayList<>();
	//#endif
	//$$
	//$$ /** Hooks the keys and renderers kept above onto the mod's event bus; called once by the entry point. */
	//$$ public static void listen(IEventBus modBus) {
	//$$ 	modBus.addListener((RegisterKeyMappingsEvent event) -> {
	//#if MC>=12111
	//$$ 		categories.forEach(event::registerCategory);
	//#endif
	//$$ 		keys.forEach(event::register);
	//$$ 	});
	//#if MC>=12111
	//$$ 	modBus.addListener((RegisterPictureInPictureRenderersEvent event) -> renderers.forEach(one -> one.accept(event)));
	//#endif
	//#if MC<260100
	//$$ 	// Before 26.1 NeoForge tells a server without NeoForge the mod's channels only while the
	//$$ 	// connection is set up, and a Fabric server counts only those told once the player is in:
	//$$ 	// without them it never greets this client. They are told again on joining.
	//$$ 	NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
	//$$ 		ClientPacketListener connection = event.getPlayer().connection;
	//$$ 		if (connection.getConnectionType().isOther()) {
	//$$ 			connection.send(new ServerboundCustomPayloadPacket(new MinecraftRegisterPayload(Platform.clientboundIds())));
	//$$ 		}
	//$$ 	});
	//#endif
	//$$ }
	//#endif
}
