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
//#if MC>=12005
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#else
//$$ import com.mopicmp.mosaicary.net.Payload;
//#endif
//#if MC>=12111
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//#endif
import net.minecraft.resources.Identifier;

//#if FABRIC>=1
//#if MC>=11900
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//#endif
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
//#if MC<12005
//$$ import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//$$ import net.minecraft.network.FriendlyByteBuf;
//#endif
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
//#elseif FORGE>=1
//$$ import java.util.ArrayList;
//$$ import java.util.List;
//$$
//$$ import com.mopicmp.mosaicary.platform.Platform;
//$$
//$$ import net.minecraft.client.multiplayer.ClientPacketListener;
//$$ import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
//$$ import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
//#if MC>=11700
//$$ import net.minecraftforge.client.event.RegisterClientCommandsEvent;
//#endif
//#if MC>=11900
//$$ import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#endif
//#if MC>=11700
//$$ import net.minecraftforge.client.event.ScreenEvent;
//#else
//$$ import net.minecraftforge.client.event.GuiScreenEvent;
//#endif
//$$ import net.minecraftforge.common.MinecraftForge;
//#if MC>=11900
//$$ import net.minecraftforge.event.GameShuttingDownEvent;
//#endif
//$$ import net.minecraftforge.event.TickEvent;
//$$ import net.minecraftforge.eventbus.api.IEventBus;
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
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
		//$$ 	if (event.phase == TickEvent.Phase.END) action.accept(Minecraft.getInstance());
		//$$ });
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
		//#elseif MC>=11900
		//$$ MinecraftForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> action.accept(Minecraft.getInstance()));
		//#else
		//$$ // Forge has no event for it before 1.19; the end of the game's process stands in.
		//$$ Runtime.getRuntime().addShutdownHook(new Thread(() -> action.accept(Minecraft.getInstance()), "Mosaicary stopping"));
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
		//#elseif MC>=11900
		//$$ MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
		//$$ 	Screen screen = event.getScreen();
		//$$ 	action.after(Minecraft.getInstance(), screen, screen.width, screen.height);
		//$$ });
		//#elseif MC>=11700
		//$$ MinecraftForge.EVENT_BUS.addListener((ScreenEvent.InitScreenEvent.Post event) -> {
		//$$ 	Screen screen = event.getScreen();
		//$$ 	action.after(Minecraft.getInstance(), screen, screen.width, screen.height);
		//$$ });
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Post event) -> {
		//$$ 	Screen screen = event.getGui();
		//$$ 	action.after(Minecraft.getInstance(), screen, screen.width, screen.height);
		//$$ });
		//#endif
	}

	//#if MC<12000
	//$$ /** Something to do with a screen as it is drawn, before 1.20, where a screen draws with a pose stack. */
	//$$ public interface ScreenRender {
	//$$ 	void render(Screen screen, com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partialTick);
	//$$ }
	//$$
	//$$ /** Just before every screen is drawn, and just after. */
	//$$ public static void onScreenRender(ScreenRender before, ScreenRender after) {
	//#if FABRIC>=1
	//$$ 	// Fabric's are a screen's own events, made anew each time it is laid out.
	//$$ 	ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
	//$$ 		ScreenEvents.beforeRender(screen).register(before::render);
	//$$ 		ScreenEvents.afterRender(screen).register(after::render);
	//$$ 	});
	//#elseif MC>=11900
	//$$ 	MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event) -> before.render(event.getScreen(),
	//$$ 		event.getPoseStack(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));
	//$$ 	MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> after.render(event.getScreen(),
	//$$ 		event.getPoseStack(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));
	//#elseif MC>=11700
	//$$ 	MinecraftForge.EVENT_BUS.addListener((ScreenEvent.DrawScreenEvent.Pre event) -> before.render(event.getScreen(),
	//$$ 		event.getPoseStack(), event.getMouseX(), event.getMouseY(), event.getPartialTicks()));
	//$$ 	MinecraftForge.EVENT_BUS.addListener((ScreenEvent.DrawScreenEvent.Post event) -> after.render(event.getScreen(),
	//$$ 		event.getPoseStack(), event.getMouseX(), event.getMouseY(), event.getPartialTicks()));
	//#else
	//$$ 	MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.DrawScreenEvent.Pre event) -> before.render(event.getGui(),
	//$$ 		event.getMatrixStack(), event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks()));
	//$$ 	MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.DrawScreenEvent.Post event) -> after.render(event.getGui(),
	//$$ 		event.getMatrixStack(), event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks()));
	//#endif
	//$$ }
	//#endif

	/** On entering a world or a server, once the connection is up. */
	public static void onJoin(Consumer<Minecraft> action) {
		//#if FABRIC>=1
		ClientPlayConnectionEvents.JOIN.register((listener, sender, minecraft) -> action.accept(minecraft));
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> action.accept(Minecraft.getInstance()));
		//#elseif MC>=11900
		//$$ MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> action.accept(Minecraft.getInstance()));
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggedInEvent event) -> action.accept(Minecraft.getInstance()));
		//#endif
	}

	/** On leaving a world or a server. */
	public static void onLeave(Consumer<Minecraft> action) {
		//#if FABRIC>=1
		ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> action.accept(minecraft));
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> action.accept(Minecraft.getInstance()));
		//#elseif MC>=11900
		//$$ MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> action.accept(Minecraft.getInstance()));
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggedOutEvent event) -> action.accept(Minecraft.getInstance()));
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
		//#else
		//$$ keys.add(key);
		//$$ return key;
		//#endif
	}

	public static void commands(Commands commands) {
		//#if FABRIC>=1
		// Before 1.19 Fabric API's client commands are its first version: one dispatcher, kept for good.
		//#if MC>=11900
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> commands.register(dispatcher));
		//#else
		//$$ commands.register(net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.DISPATCHER);
		//#endif
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> commands.register(event.getDispatcher()));
		//#elseif MC>=11700
		//$$ MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> commands.register(event.getDispatcher()));
		//#else
		//$$ // Forge has no client commands before 1.17; the only one is a measuring tool for development.
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

	//#if MC>=12005
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
	//#else
	//$$ /** What the client does with a packet of this type from the server, on the client's thread. */
	//$$ public static <T extends Payload> void onReceive(Payload.Type<T> type, Consumer<T> reader) {
	//#if FABRIC>=1
	//$$ 	// Read where it arrives, on the network thread, since the bytes are gone after.
	//$$ 	ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, listener, buf, sender) -> {
	//$$ 		T payload = type.reader().apply(buf);
	//$$ 		client.execute(() -> reader.accept(payload));
	//$$ 	});
	//#else
	//$$ 	Platform.clientReader(type, reader);
	//#endif
	//$$ }
	//$$
	//$$ /** Whether the server reads packets of this type, that is, has the mod. */
	//$$ public static boolean canSend(Payload.Type<?> type) {
	//#if FABRIC>=1
	//$$ 	return ClientPlayNetworking.canSend(type.id());
	//#else
	//$$ 	ClientPacketListener connection = Minecraft.getInstance().getConnection();
	//$$ 	return connection != null && Platform.isRemotePresent(type, connection.getConnection());
	//#endif
	//$$ }
	//$$
	//$$ public static void send(Payload payload) {
	//#if FABRIC>=1
	//$$ 	FriendlyByteBuf buf = PacketByteBufs.create();
	//$$ 	payload.write(buf);
	//$$ 	ClientPlayNetworking.send(payload.type().id(), buf);
	//#else
	//$$ 	ClientPacketListener connection = Minecraft.getInstance().getConnection();
	//$$ 	if (connection != null) connection.send(new ServerboundCustomPayloadPacket(payload.type().id(), Platform.bytesOf(payload)));
	//#endif
	//$$ }
	//#endif

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
	//#elseif FORGE>=1
	//$$ private static final List<KeyMapping> keys = new ArrayList<>();
	//$$
	//$$ /** Hooks the keys kept above onto the mod's event bus; called once by the entry point. */
	//$$ public static void listen(IEventBus modBus) {
	//#if MC>=11900
	//$$ 	modBus.addListener((RegisterKeyMappingsEvent event) -> keys.forEach(event::register));
	//#else
	//$$ 	// Before 1.19 keys are added to the game's own list once the client is being set up.
	//#if MC>=11700
	//$$ 	modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
	//$$ 		keys.forEach(net.minecraftforge.client.ClientRegistry::registerKeyBinding));
	//#else
	//$$ 	modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
	//$$ 		keys.forEach(net.minecraftforge.fml.client.registry.ClientRegistry::registerKeyBinding));
	//#endif
	//#endif
	//$$ }
	//#endif
}
