package com.mopicmp.mosaicary.platform;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//#if MC>=12005
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#else
//$$ import com.mopicmp.mosaicary.net.Payload;
//#endif
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

//#if FABRIC>=1
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
//#if MC>=12005
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//#else
//$$ import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//$$ import net.minecraft.network.FriendlyByteBuf;
//#endif
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
//#elseif FORGE>=1
//$$ import java.util.HashMap;
//$$ import java.util.LinkedHashSet;
//$$ import java.util.Map;
//$$ import java.util.Set;
//$$
//$$ import io.netty.buffer.Unpooled;
//$$ import net.minecraft.network.Connection;
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
//$$ import net.minecraft.resources.ResourceLocation;
//$$ import net.minecraftforge.common.MinecraftForge;
//$$ import net.minecraftforge.event.entity.player.PlayerEvent;
//#if MC>=11700
//$$ import net.minecraftforge.event.server.ServerStartedEvent;
//$$ import net.minecraftforge.event.server.ServerStoppingEvent;
//#endif
//$$ import net.minecraftforge.fml.LogicalSide;
//$$ import net.minecraftforge.fml.ModList;
//$$ import net.minecraftforge.fml.loading.FMLPaths;
//#if MC>=11700
//$$ import net.minecraftforge.network.NetworkEvent;
//$$ import net.minecraftforge.network.NetworkRegistry;
//$$ import net.minecraftforge.network.event.EventNetworkChannel;
//#else
//$$ // Before 1.17 Forge's network and server events lie in its fml package.
//$$ import net.minecraftforge.fml.network.NetworkEvent;
//$$ import net.minecraftforge.fml.network.NetworkRegistry;
//$$ import net.minecraftforge.fml.network.event.EventNetworkChannel;
//#endif
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
 * there the readers are kept and the event registers them all. Before 1.20.5 a packet is a
 * channel name and bytes (Payload); Forge has a channel object per name, made while the mod is
 * constructed. The packets are optional: a server or a client without the mod joins as usual
 * and the packets are simply not sent.
 */
public final class Platform {

	private Platform() { }

	/**
	 * The executor an HttpClient is given, for one whose own work runs on {@code work}. From 1.17 on that
	 * is the game's Java, whose HttpClient holds a thread only to hand over an answer, so {@code work}
	 * itself. On Java 8 the client is JvmDowngrader's copy (build.gradle.kts), which holds a thread for the
	 * whole of each request: there every request gets a thread of its own, named after {@code name}, so
	 * that a slow one does not keep the others, nor {@code work}, waiting.
	 */
	public static java.util.concurrent.Executor requests(java.util.concurrent.Executor work, String name) {
		//#if MC>=11700
		return work;
		//#else
		//$$ java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
		//$$ return java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
		//$$ 	Thread thread = new Thread(runnable, name + " request " + count.incrementAndGet());
		//$$ 	thread.setDaemon(true);
		//$$ 	return thread;
		//$$ });
		//#endif
	}

	/** The game's folder: where the mod keeps its cache and where other mods keep theirs. */
	public static Path gameDir() {
		//#if FABRIC>=1
		return FabricLoader.getInstance().getGameDir();
		//#else
		//$$ return FMLPaths.GAMEDIR.get();
		//#endif
	}

	/** The folder for settings files. */
	public static Path configDir() {
		//#if FABRIC>=1
		return FabricLoader.getInstance().getConfigDir();
		//#else
		//$$ return FMLPaths.CONFIGDIR.get();
		//#endif
	}

	/** Whether a mod with this id is loaded. */
	public static boolean isLoaded(String modId) {
		//#if FABRIC>=1
		return FabricLoader.getInstance().isModLoaded(modId);
		//#else
		//$$ return ModList.get().isLoaded(modId);
		//#endif
	}

	/** A mod's version as its metadata gives it, or "dev" when it cannot be read. */
	public static String version(String modId) {
		//#if FABRIC>=1
		return FabricLoader.getInstance().getModContainer(modId)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		//#else
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
		//#elseif MC>=11700
		//$$ MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> action.accept(event.getServer()));
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.fml.event.server.FMLServerStartedEvent event) -> action.accept(event.getServer()));
		//#endif
	}

	public static void onServerStopping(Consumer<MinecraftServer> action) {
		//#if FABRIC>=1
		ServerLifecycleEvents.SERVER_STOPPING.register(action::accept);
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> action.accept(event.getServer()));
		//#elseif MC>=11700
		//$$ MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> action.accept(event.getServer()));
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.fml.event.server.FMLServerStoppingEvent event) -> action.accept(event.getServer()));
		//#endif
	}

	/** A player has joined the server; packets can be sent to them from here on. */
	public static void onPlayerJoin(Consumer<ServerPlayer> action) {
		//#if FABRIC>=1
		// The connection's player is a getter from 1.17 on, and before a field.
		//#if MC>=11700
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> action.accept(handler.getPlayer()));
		//#else
		//$$ ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> action.accept(handler.player));
		//#endif
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
		//$$ 	if (event.getEntity() instanceof ServerPlayer player) action.accept(player);
		//$$ });
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
		//$$ 	if (event.getEntity() instanceof ServerPlayer player) action.accept(player);
		//$$ });
		//#endif
	}

	/**
	 * A player's client says, after joining, that it takes packets of this type. A Fabric server
	 * learns a client's channels as they come, and a client of another loader may name them only
	 * once it is in; so does a Forge server from a client without Forge. A NeoForge server agrees
	 * on them before the player joins, so there it never happens later.
	 */
	//#if MC>=12005
	public static void onCanSendLater(CustomPacketPayload.Type<?> type, Consumer<ServerPlayer> action) {
	//#else
	//$$ public static void onCanSendLater(Payload.Type<?> type, Consumer<ServerPlayer> action) {
	//#endif
		//#if FABRIC>=1
		ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
			//#if MC>=11700
			if (channels.contains(type.id())) action.accept(handler.getPlayer());
			//#else
			//$$ if (channels.contains(type.id())) action.accept(handler.player);
			//#endif
		});
		//#elseif FORGE>=1
		//$$ later.put(type.id(), action);
		//#endif
	}

	public static void onPlayerLeave(Consumer<ServerPlayer> action) {
		//#if FABRIC>=1
		//#if MC>=11700
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> action.accept(handler.getPlayer()));
		//#else
		//$$ ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> action.accept(handler.player));
		//#endif
		//#elseif NEOFORGE>=1
		//$$ NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
		//$$ 	if (event.getEntity() instanceof ServerPlayer player) action.accept(player);
		//$$ });
		//#else
		//$$ MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
		//$$ 	if (event.getEntity() instanceof ServerPlayer player) action.accept(player);
		//$$ });
		//#endif
	}

	// ------------------------------------------------------------------ packets

	//#if MC>=12005
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
	//#else
	//$$ /** A packet the server sends and the client reads. On Fabric a channel is only a name, and its reader declares it. */
	//$$ public static void toClient(Payload.Type<?> type) {
	//#if FORGE>=1
	//$$ 	channel(type);
	//$$ 	clientbound.add(type.id());
	//#endif
	//$$ }
	//$$
	//$$ /** A packet the client sends and the server reads. */
	//$$ public static void toServer(Payload.Type<?> type) {
	//#if FORGE>=1
	//$$ 	channel(type);
	//#endif
	//$$ }
	//$$
	//$$ /** What the server does with a packet of this type, on the server's thread. */
	//$$ public static <T extends Payload> void onServerReceive(Payload.Type<T> type, BiConsumer<ServerPlayer, T> handler) {
	//#if FABRIC>=1
	//$$ 	// Read where it arrives, on the network thread, since the bytes are gone after.
	//$$ 	ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, listener, buf, sender) -> {
	//$$ 		T payload = type.reader().apply(buf);
	//$$ 		server.execute(() -> handler.accept(player, payload));
	//$$ 	});
	//#else
	//$$ 	serverReaders.put(type.id(), handler);
	//#endif
	//$$ }
	//$$
	//$$ /** Whether this player's client reads packets of this type, that is, has the mod. */
	//$$ public static boolean canSend(ServerPlayer player, Payload.Type<?> type) {
	//#if FABRIC>=1
	//$$ 	return ServerPlayNetworking.canSend(player, type.id());
	//#else
	//$$ 	return isRemotePresent(type, player.connection.connection);
	//#endif
	//$$ }
	//$$
	//$$ public static void send(ServerPlayer player, Payload payload) {
	//#if FABRIC>=1
	//$$ 	FriendlyByteBuf buf = PacketByteBufs.create();
	//$$ 	payload.write(buf);
	//$$ 	ServerPlayNetworking.send(player, payload.type().id(), buf);
	//#else
	//$$ 	player.connection.send(new ClientboundCustomPayloadPacket(payload.type().id(), bytesOf(payload)));
	//#endif
	//$$ }
	//#endif

	//#if FORGE>=1
	//$$ /** The mod's packets' version on Forge's channels; a remote side without the mod is let in too. */
	//$$ private static final String PROTOCOL = "1";
	//$$
	//$$ private static final Map<ResourceLocation, EventNetworkChannel> channels = new HashMap<>();
	//$$ private static final Map<ResourceLocation, BiConsumer<ServerPlayer, ?>> serverReaders = new HashMap<>();
	//$$ private static final Map<ResourceLocation, Consumer<?>> clientReaders = new HashMap<>();
	//$$ private static final Map<ResourceLocation, Consumer<ServerPlayer>> later = new HashMap<>();
	//$$ private static final Set<ResourceLocation> clientbound = new LinkedHashSet<>();
	//$$
	//$$ /**
	//$$  * A channel of Forge's that carries a packet's bytes as they are, under the packet's own name,
	//$$  * which is how Fabric sends it too. Made while the mod is constructed, as Forge requires.
	//$$  */
	//$$ private static void channel(Payload.Type<?> type) {
	//$$ 	channels.computeIfAbsent(type.id(), id -> {
	//$$ 		EventNetworkChannel channel = NetworkRegistry.newEventChannel(id, () -> PROTOCOL,
	//$$ 			NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
	//$$ 		channel.addListener((NetworkEvent event) -> read(type, event));
	//$$ 		return channel;
	//$$ 	});
	//$$ }
	//$$
	//$$ /**
	//$$  * Whether the other end of this connection takes packets of this type: agreed on when it has
	//$$  * Forge, named in its `minecraft:register` when it has another loader.
	//$$  */
	//$$ public static boolean isRemotePresent(Payload.Type<?> type, Connection connection) {
	//$$ 	EventNetworkChannel channel = channels.get(type.id());
	//#if MC>=11700
	//$$ 	return channel != null && channel.isRemotePresent(connection);
	//#else
	//$$ 	if (channel == null) return false;
	//$$ 	if (channel.isRemotePresent(connection)) return true;
	//$$ 	// Before 1.17 Forge asks only what the other side's Forge agreed on, and a side with another
	//$$ 	// loader names its channels in minecraft:register; ChannelListMixin keeps those names on the
	//$$ 	// connection's list, the attribute Forge stores it under (FMLNetworkConstants.FML_MC_REGISTRY).
	//$$ 	Object named = connection.channel().attr(io.netty.util.AttributeKey.valueOf("minecraft:netregistry")).get();
	//$$ 	return named instanceof RemoteChannels && ((RemoteChannels) named).mosaicary$remote().contains(type.id());
	//#endif
	//$$ }
	//#if MC<11700
	//$$
	//$$ /** The channels the other end of a connection named in its minecraft:register, which ChannelListMixin keeps. */
	//$$ public interface RemoteChannels {
	//$$ 	Set<ResourceLocation> mosaicary$remote();
	//$$ }
	//#endif
	//$$
	//$$ /** The names of the packets the client reads. */
	//$$ public static Set<ResourceLocation> clientboundIds() {
	//$$ 	return clientbound;
	//$$ }
	//$$
	//$$ /** A packet's bytes, ready for the game's custom payload packet either way. */
	//$$ public static FriendlyByteBuf bytesOf(Payload payload) {
	//$$ 	FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
	//$$ 	payload.write(buf);
	//$$ 	return buf;
	//$$ }
	//$$
	//$$ /** The client's reader of a packet type; called by ClientPlatform. */
	//$$ public static <T extends Payload> void clientReader(Payload.Type<T> type, Consumer<T> reader) {
	//$$ 	clientReaders.put(type.id(), reader);
	//$$ }
	//$$
	//$$ /**
	//$$  * Everything that comes on a channel: a packet, read at once on the network thread and handed
	//$$  * to the receiving side's main thread, or the other end naming its channels anew.
	//$$  */
	//$$ @SuppressWarnings("unchecked")
	//$$ private static <T extends Payload> void read(Payload.Type<T> type, NetworkEvent event) {
	//$$ 	NetworkEvent.Context context = event.getSource().get();
	//$$ 	ServerPlayer player = context.getSender();
	//$$ 	if (event instanceof NetworkEvent.ChannelRegistrationChangeEvent) {
	//$$ 		Consumer<ServerPlayer> action = later.get(type.id());
	//$$ 		if (action != null && player != null && isRemotePresent(type, player.connection.connection)) {
	//$$ 			context.enqueueWork(() -> action.accept(player));
	//$$ 		}
	//$$ 		return;
	//$$ 	}
	//$$ 	FriendlyByteBuf buf = event.getPayload();
	//$$ 	if (buf == null) return;
	//$$ 	T payload = type.reader().apply(buf);
	//$$ 	context.setPacketHandled(true);
	//$$ 	if (context.getDirection().getReceptionSide() == LogicalSide.SERVER) {
	//$$ 		BiConsumer<ServerPlayer, T> reader = (BiConsumer<ServerPlayer, T>) serverReaders.get(type.id());
	//$$ 		if (reader != null && player != null) context.enqueueWork(() -> reader.accept(player, payload));
	//$$ 	} else {
	//$$ 		Consumer<T> reader = (Consumer<T>) clientReaders.get(type.id());
	//$$ 		if (reader != null) context.enqueueWork(() -> reader.accept(payload));
	//$$ 	}
	//$$ }
	//#endif

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
