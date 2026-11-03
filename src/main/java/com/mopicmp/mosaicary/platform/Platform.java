package com.mopicmp.mosaicary.platform;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

//#if FABRIC>=1
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
//#elseif NEOFORGE>=1
//$$ import java.util.HashMap;
//$$ import java.util.LinkedHashMap;
//$$ import java.util.LinkedHashSet;
//$$ import java.util.Map;
//$$ import java.util.Set;
//$$
//$$ import com.mopicmp.mosaicary.Mosaicary;
//$$
//$$ import net.minecraft.resources.Identifier;
//$$ import net.neoforged.bus.api.IEventBus;
//$$ import net.neoforged.fml.ModList;
//$$ import net.neoforged.fml.loading.FMLPaths;
//$$ import net.neoforged.neoforge.common.NeoForge;
//$$ import net.neoforged.neoforge.event.entity.player.PlayerEvent;
//$$ import net.neoforged.neoforge.event.server.ServerStartedEvent;
//$$ import net.neoforged.neoforge.event.server.ServerStoppingEvent;
//$$ import net.neoforged.neoforge.network.PacketDistributor;
//$$ import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//$$ import net.neoforged.neoforge.network.handling.IPayloadContext;
//$$ import net.neoforged.neoforge.network.handling.IPayloadHandler;
//$$ import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//#endif

/**
 * What the mod asks of the loader on both sides: where the game keeps its files, which other
 * mods are there, the server's lifecycle and players, and the mod's own packets.
 *
 * The rest of the mod calls only this, so the same code runs on every loader; what each loader
 * offers for these is written once here, under the preprocessor's conditions. The client's half
 * is ClientPlatform.
 *
 * <h2>Packets</h2>
 *
 * Every packet is declared with the side that reads it. Fabric takes a declaration and a reader
 * separately, at any time; NeoForge wants both in one registration event of the mod's bus, so
 * there the readers are kept and the event registers them all. The packets are optional: a
 * server or a client without the mod joins as usual and the packets are simply not sent.
 */
public final class Platform {

	private Platform() { }

	/** The game's folder: where the mod keeps its cache and where other mods keep theirs. */
	public static Path gameDir() {
		//#if FABRIC>=1
		return FabricLoader.getInstance().getGameDir();
		//#elseif NEOFORGE>=1
		//$$ return FMLPaths.GAMEDIR.get();
		//#endif
	}

	/** The folder for settings files. */
	public static Path configDir() {
		//#if FABRIC>=1
		return FabricLoader.getInstance().getConfigDir();
		//#elseif NEOFORGE>=1
		//$$ return FMLPaths.CONFIGDIR.get();
		//#endif
	}

	/** Whether a mod with this id is loaded. */
	public static boolean isLoaded(String modId) {
		//#if FABRIC>=1
		return FabricLoader.getInstance().isModLoaded(modId);
		//#elseif NEOFORGE>=1
		//$$ return ModList.get().isLoaded(modId);
		//#endif
	}

	/** A mod's version as its metadata gives it, or "dev" when it cannot be read. */
	public static String version(String modId) {
		//#if FABRIC>=1
		return FabricLoader.getInstance().getModContainer(modId)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		//#elseif NEOFORGE>=1
		//$$ return ModList.get().getModContainerById(modId)
		//$$ 	.map(mod -> mod.getModInfo().getVersion().toString()).orElse("dev");
		//#endif
	}

	// ------------------------------------------------------------------ the server

	public static void onServerStarted(Consumer<MinecraftServer> action) {
		//#if FABRIC>=1
		ServerLifecycleEvents.SERVER_STARTED.register(action::accept);
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> action.accept(event.getServer()));
		//#endif
	}

	public static void onServerStopping(Consumer<MinecraftServer> action) {
		//#if FABRIC>=1
		ServerLifecycleEvents.SERVER_STOPPING.register(action::accept);
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> action.accept(event.getServer()));
		//#endif
	}

	/** A player has joined the server; packets can be sent to them from here on. */
	public static void onPlayerJoin(Consumer<ServerPlayer> action) {
		//#if FABRIC>=1
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> action.accept(handler.getPlayer()));
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
		//$$ 	if (event.getEntity() instanceof ServerPlayer player) action.accept(player);
		//$$ });
		//#endif
	}

	/**
	 * A player's client says, after joining, that it takes packets of this type. A Fabric server
	 * learns a client's channels as they come, and a client of another loader may name them only
	 * once it is in; a NeoForge server agrees on them before the player joins, so there it never
	 * happens later.
	 */
	public static void onCanSendLater(CustomPacketPayload.Type<?> type, Consumer<ServerPlayer> action) {
		//#if FABRIC>=1
		ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
			if (channels.contains(type.id())) action.accept(handler.getPlayer());
		});
		//#endif
	}

	public static void onPlayerLeave(Consumer<ServerPlayer> action) {
		//#if FABRIC>=1
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> action.accept(handler.getPlayer()));
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
		//$$ 	if (event.getEntity() instanceof ServerPlayer player) action.accept(player);
		//$$ });
		//#endif
	}

	// ------------------------------------------------------------------ packets

	/** A packet the server sends and the client reads. */
	public static <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		//#if FABRIC>=1
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
		//#elseif NEOFORGE>=1
		//$$ declare(type, codec, true, false);
		//#endif
	}

	/** A packet the client sends and the server reads. */
	public static <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		//#if FABRIC>=1
		PayloadTypeRegistry.serverboundPlay().register(type, codec);
		//#elseif NEOFORGE>=1
		//$$ declare(type, codec, false, true);
		//#endif
	}

	/** What the server does with a packet of this type, on the server's thread. */
	public static <T extends CustomPacketPayload> void onServerReceive(CustomPacketPayload.Type<T> type,
			BiConsumer<ServerPlayer, T> handler) {
		//#if FABRIC>=1
		ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(context.player(), payload));
		//#elseif NEOFORGE>=1
		//$$ serverReaders.put(type, handler);
		//#endif
	}

	/** Whether this player's client reads packets of this type, that is, has the mod. */
	public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		//#if FABRIC>=1
		return ServerPlayNetworking.canSend(player, type);
		//#elseif NEOFORGE>=1
		//$$ return player.connection.hasChannel(type);
		//#endif
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		//#if FABRIC>=1
		ServerPlayNetworking.send(player, payload);
		//#elseif NEOFORGE>=1
		//$$ PacketDistributor.sendToPlayer(player, payload);
		//#endif
	}

	//#if NEOFORGE>=1
	//$$ private record Declared<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
	//$$ 	StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean toClient, boolean toServer) { }
	//$$
	//$$ private static final Map<CustomPacketPayload.Type<?>, Declared<?>> declared = new LinkedHashMap<>();
	//$$ private static final Map<CustomPacketPayload.Type<?>, BiConsumer<ServerPlayer, ?>> serverReaders = new HashMap<>();
	//$$ private static final Map<CustomPacketPayload.Type<?>, Consumer<?>> clientReaders = new HashMap<>();
	//$$
	//$$ private static <T extends CustomPacketPayload> void declare(CustomPacketPayload.Type<T> type,
	//$$ 		StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean toClient, boolean toServer) {
	//$$ 	Declared<?> before = declared.get(type);
	//$$ 	boolean client = toClient || before != null && before.toClient();
	//$$ 	boolean server = toServer || before != null && before.toServer();
	//$$ 	declared.put(type, new Declared<>(type, codec, client, server));
	//$$ }
	//$$
	//$$ /** The names of the packets the client reads: what a client tells a server without NeoForge that it takes. */
	//$$ public static Set<Identifier> clientboundIds() {
	//$$ 	Set<Identifier> ids = new LinkedHashSet<>();
	//$$ 	for (Declared<?> one : declared.values()) if (one.toClient()) ids.add(one.type().id());
	//$$ 	return ids;
	//$$ }
	//$$
	//$$ /** The client's reader of a packet type; called by ClientPlatform, kept here for the one registration. */
	//$$ public static <T extends CustomPacketPayload> void clientReader(CustomPacketPayload.Type<T> type, Consumer<T> reader) {
	//$$ 	clientReaders.put(type, reader);
	//$$ }
	//$$
	//$$ /** Hooks the packets' registration onto the mod's event bus; called once by the mod's entry point. */
	//$$ public static void listen(IEventBus modBus) {
	//$$ 	modBus.addListener((RegisterPayloadHandlersEvent event) -> {
	//$$ 		PayloadRegistrar registrar = event.registrar(Mosaicary.ID).optional();
	//$$ 		for (Declared<?> one : declared.values()) register(registrar, one);
	//$$ 	});
	//$$ }
	//$$
	//$$ private static <T extends CustomPacketPayload> void register(PayloadRegistrar registrar, Declared<T> one) {
	//$$ 	IPayloadHandler<T> reader = Platform::read;
	//$$ 	// Both sides' readers given, in either order: the reader itself tells them apart. With one,
	//$$ 	// NeoForge takes it for the server's alone and refuses to start without the client's.
	//$$ 	// Before 21.11 there is a single reader for both sides.
	//#if MC>=12111
	//$$ 	if (one.toClient() && one.toServer()) registrar.playBidirectional(one.type(), one.codec(), reader, reader);
	//#else
	//$$ 	if (one.toClient() && one.toServer()) registrar.playBidirectional(one.type(), one.codec(), reader);
	//#endif
	//$$ 	else if (one.toClient()) registrar.playToClient(one.type(), one.codec(), reader);
	//$$ 	else registrar.playToServer(one.type(), one.codec(), reader);
	//$$ }
	//$$
	//$$ /** On the receiving side's main thread, which is where NeoForge hands packets by default. */
	//$$ @SuppressWarnings("unchecked")
	//$$ private static <T extends CustomPacketPayload> void read(T payload, IPayloadContext context) {
	//$$ 	if (context.player() instanceof ServerPlayer player) {
	//$$ 		BiConsumer<ServerPlayer, T> reader = (BiConsumer<ServerPlayer, T>) serverReaders.get(payload.type());
	//$$ 		if (reader != null) reader.accept(player, payload);
	//$$ 	} else {
	//$$ 		Consumer<T> reader = (Consumer<T>) clientReaders.get(payload.type());
	//$$ 		if (reader != null) reader.accept(payload);
	//$$ 	}
	//$$ }
	//#endif
}
