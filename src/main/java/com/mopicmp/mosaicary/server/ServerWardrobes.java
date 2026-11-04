package com.mopicmp.mosaicary.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.net.MosaicaryPayloads;
import com.mopicmp.mosaicary.platform.Platform;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

//#if MC>=11903
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//#else
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
//#endif
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The optional server part: a wardrobe kept per player in the world's folder, and a skin
 * changed in game shown to everybody at once.
 *
 * <h2>The wardrobe</h2>
 *
 * Each player's wardrobe is a file under {@code <world>/mosaicary/wardrobes/}. A client
 * sends what it holds; the server brings the two together by the same rule every copy
 * uses ({@link Wardrobe#merge}) and sends back the result. A player gets the same wardrobe
 * on this server from any computer, without an account on the site.
 *
 * <h2>A changed skin</h2>
 *
 * A server learns a player's skin from Mojang when they join, and every other player
 * would see the new one only after they join again. Told that the skin changed, the
 * server asks Mojang's session server for the player's profile itself, until it carries
 * the new texture, and puts that property on the player: what the client sent is only the
 * hash to wait for, never the property, so a client cannot dress itself in anything
 * Mojang did not sign. Then the player is taken out of and put back into the player list
 * and the view of everyone who sees them, which is how their client fetches the skin
 * again, with or without the mod.
 *
 * On a server in offline mode a player's id is made up from their name, so the session
 * server is asked about the account the client names instead; the property is still
 * Mojang's, signed by Mojang.
 */
public final class ServerWardrobes {

	/** How often one player may send a wardrobe, and announce a skin. */
	private static final long WARDROBE_GAP_MS = 2_000;
	private static final long SKIN_GAP_MS = 10_000;

	/** How long to wait for Mojang to hand out the new skin, and how often to ask. */
	private static final long SKIN_WAIT_MS = 90_000;
	private static final long SKIN_ASK_MS = 4_000;

	private static final Map<UUID, Long> lastWardrobe = new HashMap<>();
	private static final Map<UUID, Long> lastSkin = new HashMap<>();
	/** The players told that this server keeps wardrobes, each once while they are on. */
	private static final Set<UUID> greeted = new HashSet<>();
	private static ExecutorService work;
	private static HttpClient http;

	private ServerWardrobes() { }

	public static void init() {
		Platform.onServerStarted(server -> start());
		Platform.onServerStopping(server -> stop());
		Platform.onPlayerJoin(player -> {
			if (Platform.canSend(player, MosaicaryPayloads.Hello.TYPE)) greet(player);
		});
		// A client that names the mod's channel only after joining is greeted then.
		Platform.onCanSendLater(MosaicaryPayloads.Hello.TYPE, ServerWardrobes::greet);
		Platform.onPlayerLeave(player -> {
			lastWardrobe.remove(player.getUUID());
			lastSkin.remove(player.getUUID());
			greeted.remove(player.getUUID());
		});
		Platform.onServerReceive(MosaicaryPayloads.WardrobeState.TYPE,
			(player, payload) -> wardrobe(player.level().getServer(), player, payload.json()));
		Platform.onServerReceive(MosaicaryPayloads.SkinChanged.TYPE,
			(player, payload) -> skinChanged(player.level().getServer(), player, payload.account(), payload.texture()));
	}

	private static void greet(ServerPlayer player) {
		if (greeted.add(player.getUUID())) Platform.send(player, new MosaicaryPayloads.Hello(MosaicaryPayloads.VERSION));
	}

	private static synchronized void start() {
		if (work != null) return;
		work = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "Mosaicary skins");
			thread.setDaemon(true);
			return thread;
		});
		http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.executor(Platform.requests(work, "Mosaicary skins")).build();
	}

	private static synchronized void stop() {
		if (work == null) return;
		work.shutdownNow();
		// An HttpClient can be shut down itself from Java 21, the game's Java since 1.20.5; before,
		// it stops once its executor has and nothing holds it.
		//#if MC>=12005
		http.shutdownNow();
		//#endif
		work = null;
		http = null;
		lastWardrobe.clear();
		lastSkin.clear();
	}

	private static Path folder(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("mosaicary").resolve("wardrobes");
	}

	// ------------------------------------------------------------------ the wardrobe

	/** On the server thread, where the loaders hand play packets. */
	private static void wardrobe(MinecraftServer server, ServerPlayer player, String json) {
		long now = System.currentTimeMillis();
		Long last = lastWardrobe.get(player.getUUID());
		if (last != null && now - last < WARDROBE_GAP_MS) return;
		lastWardrobe.put(player.getUUID(), now);
		Wardrobe kept = new Wardrobe(folder(server), player.getUUID().toString());
		kept.mergeIn(Wardrobe.fromJson(json), now);
		kept.save();
		Platform.send(player, new MosaicaryPayloads.WardrobeState(kept.travellingJson()));
	}

	// ------------------------------------------------------------------ a changed skin

	private static void skinChanged(MinecraftServer server, ServerPlayer player, UUID account, String texture) {
		long now = System.currentTimeMillis();
		Long last = lastSkin.get(player.getUUID());
		if (last != null && now - last < SKIN_GAP_MS) return;
		lastSkin.put(player.getUUID(), now);
		if (texture == null || !texture.matches("[0-9a-f]{32,128}") || http == null) return;
		// An online server knows the player's real account; only an offline one has to take the client's word for it.
		UUID asked = server.usesAuthentication() ? player.getUUID() : account;
		UUID who = player.getUUID();
		waitForTexture(asked, texture, now + SKIN_WAIT_MS).whenComplete((property, problem) -> server.execute(() -> {
			if (problem != null || property == null) {
				Mosaicary.LOGGER.info("Mojang did not hand out {}'s new skin in time; it shows when they join again", who);
				return;
			}
			ServerPlayer still = server.getPlayerList().getPlayer(who);
			if (still == null || still.hasDisconnected()) return;
			showAgain(server, still, property);
		}));
	}

	/** Asks the session server for the profile until its skin is the one expected, or the time runs out. */
	private static CompletableFuture<Property> waitForTexture(UUID account, String texture, long until) {
		String address = "https://sessionserver.mojang.com/session/minecraft/profile/" + account.toString().replace("-", "")
			+ "?unsigned=false";
		HttpRequest request = HttpRequest.newBuilder(URI.create(address)).timeout(Duration.ofSeconds(15)).GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).handle((response, problem) -> {
			if (problem == null && response.statusCode() == 200) return texturesWith(response.body(), texture);
			return null;
		}).thenCompose(found -> {
			if (found != null) return CompletableFuture.completedFuture(found);
			if (System.currentTimeMillis() + SKIN_ASK_MS > until || http == null) return CompletableFuture.completedFuture(null);
			return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(SKIN_ASK_MS, TimeUnit.MILLISECONDS))
				.thenCompose(nothing -> waitForTexture(account, texture, until));
		});
	}

	/** The signed {@code textures} property of a session-server profile, when its skin is the texture expected. */
	static Property texturesWith(String body, String texture) {
		try {
			// Gson reads a string by a static method from 2.8.6 on; 1.16.5 has 2.8.0.
			//#if MC>=11700
			JsonObject profile = JsonParser.parseString(body).getAsJsonObject();
			//#else
			//$$ JsonObject profile = new JsonParser().parse(body).getAsJsonObject();
			//#endif
			for (JsonElement one : profile.getAsJsonArray("properties")) {
				JsonObject property = one.getAsJsonObject();
				if (!"textures".equals(property.get("name").getAsString()) || !property.has("signature")) continue;
				String value = property.get("value").getAsString();
				String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
				//#if MC>=11700
				JsonObject skin = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
				//#else
				//$$ JsonObject skin = new JsonParser().parse(decoded).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
				//#endif
				String url = skin == null ? "" : skin.get("url").getAsString().toLowerCase(Locale.ROOT);
				if (url.endsWith("/texture/" + texture)) return new Property("textures", value, property.get("signature").getAsString());
			}
		} catch (RuntimeException unreadable) {
			// Not a profile; asked again.
		}
		return null;
	}

	/**
	 * Puts the new property on the player and shows them to everyone again: out of the
	 * player list and back in, and out of each watcher's view and back in.
	 */
	private static void showAgain(MinecraftServer server, ServerPlayer player, Property textures) {
		GameProfile old = player.getGameProfile();
		// A profile and its properties cannot be changed in place from 1.21.11 on; before, they can.
		//#if MC>=12111
		Multimap<String, Property> properties = LinkedHashMultimap.create(old.properties());
		properties.removeAll("textures");
		properties.put("textures", textures);
		GameProfile now = new GameProfile(old.id(), old.name(), new PropertyMap(properties));
		//#else
		//$$ GameProfile now = new GameProfile(old.getId(), old.getName());
		//$$ now.getProperties().putAll(old.getProperties());
		//$$ now.getProperties().removeAll("textures");
		//$$ now.getProperties().put("textures", textures);
		//#endif
		((ProfileHolder) player).mosaicary$setGameProfile(now);

		// One packet with an action before 1.19.3, two packets since.
		//#if MC>=11903
		server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
		server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
		//#else
		//$$ server.getPlayerList().broadcastAll(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, player));
		//$$ server.getPlayerList().broadcastAll(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, player));
		//#endif
		Object tracked = ((TrackedEntities) player.level().getChunkSource().chunkMap).mosaicary$entities().get(player.getId());
		if (tracked instanceof Retrack watched) watched.mosaicary$showAgain();
		Mosaicary.LOGGER.info("Showing {}'s new skin to everyone", player.getName().getString());
	}

	/** Implemented by the player mixin: replaces the profile the player was made with. */
	public interface ProfileHolder {
		void mosaicary$setGameProfile(GameProfile profile);
	}

	/** Implemented by the chunk map mixin: the entities it tracks, by id. */
	public interface TrackedEntities {
		it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> mosaicary$entities();
	}

	/** Implemented by the tracked entity mixin: out of every watcher's view and straight back in. */
	public interface Retrack {
		void mosaicary$showAgain();
	}
}
