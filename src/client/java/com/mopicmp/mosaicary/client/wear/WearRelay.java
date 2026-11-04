package com.mopicmp.mosaicary.client.wear;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.Environment;
import com.mojang.authlib.EnvironmentParser;
import com.mojang.authlib.services.MinecraftServicesEnvironment;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.model.Roles;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.site.SiteClient;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
//#if MC>=11900
import net.minecraft.util.Signer;
//#endif
import net.minecraft.util.Util;
//#if MC>=11903
import net.minecraft.world.entity.player.ProfileKeyPair;
//#endif

/**
 * The client of the site's wear relay: tells mosaicary.com which model the local
 * player wears, and asks it what the players around wear.
 *
 * <h2>When it runs</h2>
 *
 * Only with sharing switched on, in a world, and when nothing below rules it out:
 * an account whose UUID is not version 4 (an offline account cannot prove itself);
 * a session service that is not the game's own one talking to Mojang (a replaced one,
 * as some launchers install, signs keys Mojang never issued); a server in offline
 * mode, whose UUIDs are made up from names; a game with no profile key, which is the
 * only proof the site takes. Each reason is logged once and shown on the settings
 * screen.
 *
 * <h2>Proof of the account</h2>
 *
 * {@code begin} gives a fresh challenge; the client signs five lines with the
 * profile private key, the key pair the game holds to sign chat, and sends that with
 * the public key and Mojang's certificate over it to {@code verify}. The answer is a
 * token, used until 30 seconds before it ends. A request refused with 401 signs in
 * again once.
 *
 * <h2>How often</h2>
 *
 * A player who comes into view is asked about within two seconds; everyone in view
 * is asked again every 15 seconds, so a change of model shows up soon. Asking goes
 * in batches every two seconds: one to three players with single requests, four to
 * twenty with one bulk request. The local player's model is sent at most every ten
 * seconds. Five failures within 30 seconds pause the relay for five minutes.
 *
 * Everything here runs on the client thread; answers hop back to it.
 */
public final class WearRelay implements WornModels.Listener {

	/** What the relay is doing, or why it is not; the settings screen has words for each. */
	public enum Status {
		/** Sharing is switched off. */
		OFF,
		/** Not in a world. */
		NO_WORLD,
		/** Talking to the site. */
		SITE,
		/** Mojang's proof is not to be had, but the game is signed in to an account on the site. */
		ACCOUNT,
		/** The account's UUID is not version 4. */
		OFFLINE_ACCOUNT,
		/** The game's session service is not its own one talking to Mojang. */
		CHANGED_SESSION,
		/** The server runs in offline mode. */
		OFFLINE_SERVER,
		/** The game holds no profile key to prove the account with. */
		NO_KEY,
		/** The site did not accept the proof. */
		REFUSED,
		/** Too many failures in a row; waiting before trying again. */
		PAUSED
	}

	/** The first line of what is signed, so the signature means "sign in to the wear relay" and nothing else. */
	static final String SIGN_PREFIX = "mosaicary-wear-v1";

	private static final long BATCH_MS = 2_000;
	private static final long REFRESH_MS = 15_000;
	private static final long UPLOAD_GAP_MS = 10_000;
	private static final long ERROR_WINDOW_MS = 30_000;
	private static final int ERRORS_TO_PAUSE = 5;
	private static final long PAUSE_MS = 5 * 60_000;
	private static final long TOKEN_MARGIN_MS = 30_000;
	/** How long a player out of view keeps their model before it is forgotten. */
	private static final long FORGET_MS = 60_000;
	/** How long a model card fetched for another player is trusted. */
	private static final long CARD_KEEP_MS = 10 * 60_000;
	private static final int SINGLE_MOST = 3;
	private static final int BULK_MOST = 20;
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	/** A refusal from the site, with its status and code. */
	static final class Refused extends RuntimeException {
		final int status;
		final String code;

		Refused(int status, String code) {
			super(status + " " + code);
			this.status = status;
			this.code = code;
		}
	}

	/** Not a failure of the site: this game cannot sign in at all. */
	static final class NoKey extends RuntimeException {
		NoKey() {
			super("no profile key");
		}
	}

	private record KnownCard(ModelCard card, long at) { }

	/** The worn models, or null where the game version has no model catalogue. */
	private final WornModels models;
	private final Settings settings;
	private final SiteClient site;

	private Status status = Status.NO_WORLD;
	private final Set<Status> logged = new HashSet<>();
	private Boolean sessionIsMojang;
	private String lastSharing;

	// Signing in.
	private String token;
	private long tokenUntil;
	private UUID tokenFor;
	private boolean signingIn;
	private final List<Consumer<String>> waitingForToken = new ArrayList<>();
	private final List<Runnable> failedWaiting = new ArrayList<>();
	private long noKeyUntil;
	private long refusedUntil;

	// Sending the local player's model.
	private boolean uploadWanted;
	private boolean uploading;
	private long lastUpload = -UPLOAD_GAP_MS;

	// Asking about the others.
	private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
	private final Map<UUID, Long> askedAt = new HashMap<>();
	private final Map<UUID, Long> seenAt = new HashMap<>();
	private final Set<UUID> shownByRelay = new HashSet<>();
	private int inFlight;
	private long nextBatch;
	private final ArrayDeque<Long> errors = new ArrayDeque<>();
	private long pausedUntil;
	private final Map<String, KnownCard> cards = new HashMap<>();
	private final Map<String, List<Consumer<ModelCard>>> cardWaiters = new HashMap<>();

	public WearRelay(SiteClient site, WornModels models, Settings settings) {
		this.models = models;
		this.settings = settings;
		this.site = site;
		this.lastSharing = settings.sharing;
		if (models != null) models.listen(this);
	}

	public Status status() {
		return status;
	}

	/**
	 * The status for the settings screen: outside a world, a reason the account
	 * cannot take part anywhere is shown rather than "not in a world", so it is known
	 * before joining.
	 */
	public Status shown() {
		if (!Settings.YES.equals(settings.sharing)) return Status.OFF;
		if (status == Status.NO_WORLD || status == Status.OFF) {
			Status account = accountStatus(Minecraft.getInstance());
			return account != null ? account : Status.NO_WORLD;
		}
		return status;
	}

	@Override
	public void changed(ModelCard worn, Map<String, String> roles) {
		uploadWanted = true;
	}

	// ------------------------------------------------------------------ the tick

	/** Called every client tick. */
	public void tick(Minecraft minecraft) {
		long now = Util.getMillis();
		if (Settings.YES.equals(lastSharing) && !Settings.YES.equals(settings.sharing) && token != null) {
			// Switched off: what the site keeps about this player goes, if that is
			// possible without signing in again.
			deleteWith(token, null);
		}
		lastSharing = settings.sharing;

		updateStatus(minecraft, now);
		if (!asksAboutOthers(status)) return;

		if (uploadWanted && !uploading && now - lastUpload >= UPLOAD_GAP_MS) {
			if (status == Status.SITE) upload(now);
			else if (status == Status.ACCOUNT) uploadByAccount(now);
		}

		UUID local = minecraft.player == null ? null : minecraft.player.getUUID();
		for (AbstractClientPlayer player : minecraft.level.players()) {
			UUID id = player.getUUID();
			if (id.equals(local) || (id.version() != 4 && id.version() != 3)) continue;
			seenAt.put(id, now);
			Long asked = askedAt.get(id);
			if (asked == null || now - asked >= REFRESH_MS) queue.add(id);
		}
		for (Iterator<Map.Entry<UUID, Long>> it = seenAt.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> entry = it.next();
			if (now - entry.getValue() < FORGET_MS) continue;
			it.remove();
			askedAt.remove(entry.getKey());
			queue.remove(entry.getKey());
			if (shownByRelay.remove(entry.getKey())) takeOff(entry.getKey());
		}
		if (now >= nextBatch && inFlight == 0 && !queue.isEmpty()) {
			nextBatch = now + BATCH_MS;
			sendBatch(now);
		}
	}

	/** Moves to the status the game is in now, doing what each change of path needs. */
	private void updateStatus(Minecraft minecraft, long now) {
		Status next = statusNow(minecraft, now);
		if (next == status) return;
		if (asksAboutOthers(status) && !asksAboutOthers(next)) forgetShown();
		if (next == Status.SITE || next == Status.ACCOUNT) uploadWanted = true;
		status = next;
		if (logged.add(next) && next != Status.SITE && next != Status.NO_WORLD) {
			Mosaicary.LOGGER.info("Sharing worn models: {}", describe(next));
		}
	}

	private Status statusNow(Minecraft minecraft, long now) {
		if (!Settings.YES.equals(settings.sharing)) return Status.OFF;
		ClientPacketListener connection = minecraft.getConnection();
		if (minecraft.level == null || connection == null) return Status.NO_WORLD;
		if (now < pausedUntil) return Status.PAUSED;
		Status account = accountStatus(minecraft);
		Status noProof = account != null ? account
			: !minecraft.isLocalServer() && !onlineMode(connection) ? Status.OFFLINE_SERVER
			: now < noKeyUntil ? Status.NO_KEY
			: now < refusedUntil ? Status.REFUSED
			: null;
		if (noProof == null) return Status.SITE;
		return signedInOnSite() ? Status.ACCOUNT : noProof;
	}

	/**
	 * Whether the players around are asked about: nothing proves anything for that, so also where
	 * this game has nothing to say for itself. Who is shown what was decided when it was sent.
	 */
	private static boolean asksAboutOthers(Status status) {
		return status != Status.OFF && status != Status.NO_WORLD && status != Status.PAUSED;
	}

	/** Whether this game can prove its account at all, wherever it is; null when it can. */
	private Status accountStatus(Minecraft minecraft) {
		if (minecraft.getUser().getProfileId().version() != 4) return Status.OFFLINE_ACCOUNT;
		if (sessionIsMojang == null) sessionIsMojang = sessionIsMojang(minecraft);
		if (!sessionIsMojang) return Status.CHANGED_SESSION;
		return null;
	}

	/**
	 * Whether the server checks who joins with Mojang. Servers say so since 26.1; before, the
	 * client is not told, and the server is taken to check.
	 */
	private static boolean onlineMode(ClientPacketListener connection) {
		//#if MC>=260100
		return connection.onlineMode();
		//#else
		//$$ return true;
		//#endif
	}

	/**
	 * The game's own session service, pointed at Mojang's services: the exact class
	 * the game makes, and no environment chosen by the system properties other than
	 * the production one.
	 */
	private static boolean sessionIsMojang(Minecraft minecraft) {
		if (minecraft.services().sessionService().getClass() != MinecraftServicesSessionService.class) return false;
		Optional<Environment> chosen = EnvironmentParser.getEnvironmentFromProperties();
		// Before 1.17 authlib's production environment is the enum's value itself; one read from the
		// properties is another object, so the hosts are compared.
		//#if MC>=11700
		return chosen.isEmpty() || chosen.get().equals(MinecraftServicesEnvironment.PROD.getEnvironment());
		//#else
		//$$ return chosen.isEmpty() || chosen.get().getSessionHost().equals(com.mojang.authlib.yggdrasil.YggdrasilEnvironment.PROD.getSessionHost());
		//#endif
	}

	/** Forgets every model shown on others. */
	private void forgetShown() {
		for (UUID id : shownByRelay) takeOff(id);
		shownByRelay.clear();
		queue.clear();
		askedAt.clear();
		seenAt.clear();
	}

	/** Leaving a world: nobody around any more. */
	public void leftWorld() {
		forgetShown();
		status = Status.NO_WORLD;
	}

	static String describe(Status status) {
		return switch (status) {
			case OFF -> "sharing is off";
			case NO_WORLD -> "not in a world";
			case SITE -> "on";
			case ACCOUNT -> "on, through the account on the site";
			case OFFLINE_ACCOUNT -> "not available: the account's UUID is not version 4";
			case CHANGED_SESSION -> "not available: the game's session service is not the one that talks to Mojang";
			case OFFLINE_SERVER -> "not available: the server runs in offline mode";
			case NO_KEY -> "not available: the game holds no profile key";
			case REFUSED -> "not available: the site did not accept the account's proof";
			case PAUSED -> "paused for five minutes after repeated failures";
		};
	}

	// ------------------------------------------------------------------ failures

	private void failed(Throwable problem, String what) {
		Throwable cause = problem;
		while (cause.getCause() != null && !(cause instanceof Refused) && !(cause instanceof NoKey)) cause = cause.getCause();
		long now = Util.getMillis();
		if (cause instanceof NoKey) {
			noKeyUntil = now + PAUSE_MS;
			return;
		}
		if (cause instanceof Refused refused && refused.status == 401 && refused.code.startsWith("wear_")
				&& !refused.code.equals("wear_token_gone")) {
			// The proof itself was refused: asking again changes nothing for a while.
			refusedUntil = now + PAUSE_MS;
			Mosaicary.LOGGER.warn("The site refused the account's proof: {}", refused.code);
			return;
		}
		Mosaicary.LOGGER.debug("Wear relay: {} failed: {}", what,
			cause instanceof Refused ? cause.getMessage() : SiteClient.describe(cause));
		errors.addLast(now);
		while (!errors.isEmpty() && now - errors.peekFirst() > ERROR_WINDOW_MS) errors.removeFirst();
		if (errors.size() >= ERRORS_TO_PAUSE) {
			errors.clear();
			pausedUntil = now + PAUSE_MS;
			Mosaicary.LOGGER.info("Wear relay: {} failures within {} s, pausing for {} minutes", ERRORS_TO_PAUSE,
				ERROR_WINDOW_MS / 1000, PAUSE_MS / 60_000);
		}
	}

	private static Refused refusal(SiteClient.Answer answer) {
		String code = "";
		try {
			JsonElement parsed = JsonParser.parseString(answer.body());
			if (parsed.isJsonObject() && parsed.getAsJsonObject().has("no")) code = parsed.getAsJsonObject().get("no").getAsString();
		} catch (RuntimeException unreadable) {
			// A refusal without a readable body still has its status.
		}
		return new Refused(answer.status(), code);
	}

	private static void runOnClient(Runnable task) {
		Minecraft.getInstance().execute(task);
	}

	// ------------------------------------------------------------------ signing in

	/** Runs with a valid token, signing in first when needed; on failure runs the other one instead. */
	private void withToken(Consumer<String> then, Runnable otherwise) {
		UUID me = Minecraft.getInstance().getUser().getProfileId();
		if (token != null && me.equals(tokenFor) && System.currentTimeMillis() < tokenUntil - TOKEN_MARGIN_MS) {
			then.accept(token);
			return;
		}
		token = null;
		waitingForToken.add(then);
		if (otherwise != null) failedWaiting.add(otherwise);
		if (signingIn) return;
		signingIn = true;
		signIn(me).whenComplete((signed, problem) -> runOnClient(() -> {
			signingIn = false;
			List<Consumer<String>> waiting = new ArrayList<>(waitingForToken);
			List<Runnable> failing = new ArrayList<>(failedWaiting);
			waitingForToken.clear();
			failedWaiting.clear();
			if (problem != null) {
				failed(problem, "signing in");
				failing.forEach(Runnable::run);
				return;
			}
			token = signed.token;
			tokenUntil = signed.until;
			tokenFor = me;
			waiting.forEach(one -> one.accept(signed.token));
		}));
	}

	private record Signed(String token, long until) { }

	/** The player's chat key as Mojang certified it, and signing with its private half. */
	private record ChatKey(java.security.PublicKey publicKey, Instant expiresAt, byte[] keySignature,
			java.util.function.Function<byte[], byte[]> sign) { }

	/**
	 * The chat key, asked of the game. From 1.19.3 on the game hands out the pair; in 1.19.2 the
	 * certified public half and a signer; before 1.19 it has none, and it is asked of Mojang instead.
	 */
	private static CompletableFuture<ChatKey> chatKey() {
		//#if MC>=11903
		return Minecraft.getInstance().getProfileKeyPairManager().prepareKeyPair().thenApply(found -> {
			if (found.isEmpty()) throw new NoKey();
			ProfileKeyPair pair = found.get();
			net.minecraft.world.entity.player.ProfilePublicKey.Data data = pair.publicKey().data();
			Signer signer = Signer.from(pair.privateKey(), "SHA256withRSA");
			return new ChatKey(data.key(), data.expiresAt(), data.keySignature(), signer::sign);
		});
		//#elseif MC>=11900
		//$$ net.minecraft.client.multiplayer.ProfileKeyPairManager keys = Minecraft.getInstance().getProfileKeyPairManager();
		//$$ return keys.preparePublicKey().thenApply(found -> {
		//$$ 	Signer signer = keys.signer();
		//$$ 	if (found.isEmpty() || signer == null) throw new NoKey();
		//$$ 	net.minecraft.world.entity.player.ProfilePublicKey.Data data = found.get();
		//$$ 	return new ChatKey(data.key(), data.expiresAt(), data.keySignature(), signer::sign);
		//$$ });
		//#else
		//$$ return mojangChatKey(Minecraft.getInstance().getUser().getAccessToken());
		//#endif
	}

	//#if MC<11900
	//$$ private static final java.net.URI CERTIFICATES = java.net.URI.create("https://api.minecraftservices.com/player/certificates");
	//$$
	//$$ /**
	//$$  * The chat key asked of Mojang with the account's access token, as the game itself asks for it from
	//$$  * 1.19 on (ProfileKeyPairManager): Mojang answers with a key pair and its signature of the public
	//$$  * half for this account. The token goes to Mojang alone. An account Mojang does not know (offline,
	//$$  * or a token that has run out) has no key, as with the game's.
	//$$  */
	//$$ private static CompletableFuture<ChatKey> mojangChatKey(String accessToken) {
	//$$ 	java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(CERTIFICATES)
	//$$ 		.timeout(TIMEOUT)
	//$$ 		.header("Authorization", "Bearer " + accessToken)
	//$$ 		.header("Content-Type", "application/json")
	//$$ 		.POST(java.net.http.HttpRequest.BodyPublishers.noBody())
	//$$ 		.build();
	//$$ 	return java.net.http.HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
	//$$ 		.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
	//$$ 		.thenApply(answer -> {
	//$$ 			if (answer.statusCode() != 200) throw new NoKey();
	//$$ 			try {
	//$$ 				JsonObject said = JsonParser.parseString(answer.body()).getAsJsonObject();
	//$$ 				JsonObject pair = said.getAsJsonObject("keyPair");
	//$$ 				java.security.KeyFactory rsa = java.security.KeyFactory.getInstance("RSA");
	//$$ 				java.security.PrivateKey privateKey = rsa.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
	//$$ 					pem(pair.get("privateKey").getAsString())));
	//$$ 				java.security.PublicKey publicKey = rsa.generatePublic(new java.security.spec.X509EncodedKeySpec(
	//$$ 					pem(pair.get("publicKey").getAsString())));
	//$$ 				byte[] keySignature = Base64.getDecoder().decode(said.get("publicKeySignatureV2").getAsString());
	//$$ 				Instant expiresAt = Instant.parse(said.get("expiresAt").getAsString());
	//$$ 				return new ChatKey(publicKey, expiresAt, keySignature, bytes -> signed(privateKey, bytes));
	//$$ 			} catch (RuntimeException | java.security.GeneralSecurityException odd) {
	//$$ 				throw new NoKey();
	//$$ 			}
	//$$ 		});
	//$$ }
	//$$
	//$$ /** The bytes inside a PEM block, whatever its header names them; Mojang calls both halves "RSA". */
	//$$ static byte[] pem(String text) {
	//$$ 	return Base64.getMimeDecoder().decode(text.replaceAll("-----[A-Z ]+-----", ""));
	//$$ }
	//$$
	//$$ static byte[] signed(java.security.PrivateKey key, byte[] bytes) {
	//$$ 	try {
	//$$ 		java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
	//$$ 		signature.initSign(key);
	//$$ 		signature.update(bytes);
	//$$ 		return signature.sign();
	//$$ 	} catch (java.security.GeneralSecurityException failed) {
	//$$ 		throw new IllegalStateException(failed);
	//$$ 	}
	//$$ }
	//#endif

	/** begin, sign, verify; off the client thread except for asking the game for its key. */
	private CompletableFuture<Signed> signIn(UUID me) {
		String uuid = me.toString();
		return chatKey().thenCompose(key -> {
			return site.call("POST", "/api/wear/begin", "{}", null, TIMEOUT).thenCompose(begun -> {
				if (!begun.ok()) throw refusal(begun);
				JsonObject challenge = JsonParser.parseString(begun.body()).getAsJsonObject();
				String serverId = challenge.get("serverId").getAsString();
				String issued = challenge.get("issued").getAsString();
				String sig = challenge.get("sig").getAsString();
				JsonObject proof = new JsonObject();
				proof.addProperty("serverId", serverId);
				proof.addProperty("issued", issued);
				proof.addProperty("sig", sig);
				proof.addProperty("uuid", uuid);
				proof.addProperty("publicKey", Base64.getEncoder().encodeToString(key.publicKey().getEncoded()));
				// A number: Mojang certifies the expiry in milliseconds.
				proof.addProperty("expiresAt", key.expiresAt().toEpochMilli());
				proof.addProperty("keySignature", Base64.getEncoder().encodeToString(key.keySignature()));
				proof.addProperty("signature", Base64.getEncoder().encodeToString(
					key.sign().apply(payload(serverId, issued, sig, uuid))));
				return site.call("POST", "/api/wear/verify", proof.toString(), null, TIMEOUT);
			});
		}).thenApply(verified -> {
			if (!verified.ok()) throw refusal(verified);
			JsonObject answer = JsonParser.parseString(verified.body()).getAsJsonObject();
			return new Signed(answer.get("token").getAsString(), Instant.parse(answer.get("expires").getAsString()).toEpochMilli());
		});
	}

	/** The bytes signed with the profile key: five lines joined by a line feed, as UTF-8. */
	static byte[] payload(String serverId, String issued, String sig, String uuid) {
		return String.join("\n", SIGN_PREFIX, serverId, issued, sig, uuid).getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * A request that needs the token. A 401 means the token went stale on the site's
	 * side: the token is dropped, a new one is fetched, and the request made once more.
	 */
	private void authorised(String method, String path, String json, Consumer<SiteClient.Answer> then, Runnable otherwise,
			boolean again) {
		withToken(bearer -> site.call(method, path, json, bearer, TIMEOUT).whenComplete((answer, problem) -> runOnClient(() -> {
			if (problem != null) {
				failed(problem, method + " " + path);
				if (otherwise != null) otherwise.run();
				return;
			}
			if (answer.status() == 401 && !again) {
				token = null;
				authorised(method, path, json, then, otherwise, true);
				return;
			}
			then.accept(answer);
		})), otherwise);
	}

	/** Whether this game can prove its account to the site at all; the wardrobe kept by the account needs it too. */
	public boolean canSignIn() {
		return accountStatus(Minecraft.getInstance()) == null;
	}

	/**
	 * A request signed in as the Minecraft account, for other parts of the mod that keep
	 * things on the site under it (the wardrobe). Signs in the same way, with the same
	 * token, whether or not sharing is on: the proof is the account, not the sharing.
	 */
	public void signedIn(String method, String path, String json, Consumer<SiteClient.Answer> then, Runnable otherwise) {
		if (!canSignIn()) {
			otherwise.run();
			return;
		}
		authorised(method, path, json, then, otherwise, false);
	}

	// ------------------------------------------------------------------ the local player's skin

	private String skinTexture;
	private String skinArms;
	private Skins skins;

	/** The skins, to show other players' new skins; set once both exist. */
	public void skins(Skins skins) {
		this.skins = skins;
	}

	/**
	 * A skin was just put on the account: the others with the mod are told its texture,
	 * so they see it before the server hands it out after the player joins again.
	 */
	public void skinChanged(String texture, String arms) {
		skinTexture = texture;
		skinArms = arms;
		uploadWanted = true;
	}

	// ------------------------------------------------------------------ a skin worn through an account on the site

	private JsonObject skinOnSite;
	private boolean nameTaken;

	/** A skin was put on through the account on the site: what the others with the mod are to be told. */
	public void skinOnSite(Wardrobe.Entry entry) {
		JsonObject skin = new JsonObject();
		if (entry.id != null) skin.addProperty("model", entry.id);
		else if (entry.texture != null) skin.addProperty("texture", entry.texture);
		else if (entry.file != null) skin.addProperty("file", entry.file);
		else return;
		skin.addProperty("arms", entry.slim() ? "slim" : "classic");
		skinOnSite = skin;
		uploadWanted = true;
	}

	/** Whether the site said that another account wears skins under this player's name. */
	public boolean nameTaken() {
		return nameTaken;
	}

	private void uploadByAccount(long now) {
		uploadWanted = false;
		if (skinOnSite == null) return;
		uploading = true;
		lastUpload = now;
		JsonObject body = new JsonObject();
		body.addProperty("uuid", Skins.offlineId(Minecraft.getInstance().getUser().getName()).toString());
		body.add("skin", skinOnSite);
		site.call("PUT", "/api/wear/name", body.toString(), settings.siteKey, TIMEOUT).whenComplete((answer, problem) -> runOnClient(() -> {
			uploading = false;
			if (problem != null) {
				failed(problem, "sending the skin");
				uploadWanted = true;
				return;
			}
			nameTaken = answer.status() == 409;
			if (!answer.ok() && !nameTaken) failed(refusal(answer), "sending the skin");
		}));
	}

	// ------------------------------------------------------------------ the local player's model

	private void upload(long now) {
		uploadWanted = false;
		uploading = true;
		lastUpload = now;
		ModelCard worn = com.mopicmp.mosaicary.client.MosaicaryClient.MODELS && models != null ? models.worn() : null;
		JsonObject body = new JsonObject();
		// A model from the player's own files is not on the site: nothing to show others.
		boolean onSite = worn != null && !worn.isLocal();
		if (onSite) {
			body.addProperty("model", worn.id());
			body.add("roles", JsonParser.parseString(Roles.toJson(models.rolesToSend(worn))));
			// The size picked for this model, over its own size; the site keeps it to a thousandth.
			body.addProperty("scale", models.scaleFor(worn));
		} else {
			body.add("model", JsonNull.INSTANCE);
		}
		if (skinTexture != null) {
			JsonObject skin = new JsonObject();
			skin.addProperty("texture", skinTexture);
			skin.addProperty("arms", skinArms);
			body.add("skin", skin);
		}
		authorised("PUT", "/api/wear", body.toString(), answer -> {
			uploading = false;
			if (!answer.ok()) {
				Refused refused = refusal(answer);
				if (refused.status == 404) {
					Mosaicary.LOGGER.info("The site does not show {} to others: {}", worn == null ? "-" : worn.id(), refused.code);
				} else {
					failed(refused, "sending the worn model");
					uploadWanted = true;
				}
			}
		}, () -> {
			uploading = false;
			uploadWanted = true;
		}, false);
	}

	// ------------------------------------------------------------------ the others

	private void sendBatch(long now) {
		List<UUID> batch = new ArrayList<>();
		for (Iterator<UUID> it = queue.iterator(); it.hasNext() && batch.size() < BULK_MOST;) {
			batch.add(it.next());
			it.remove();
		}
		for (UUID id : batch) askedAt.put(id, now);
		if (batch.size() <= SINGLE_MOST) {
			for (UUID id : batch) {
				inFlight++;
				site.call("GET", "/api/wear/" + id, null, null, TIMEOUT).whenComplete((answer, problem) -> runOnClient(() -> {
					inFlight--;
					if (problem != null) {
						failed(problem, "asking about a player");
						return;
					}
					if (answer.status() == 204) {
						apply(id, null);
					} else if (answer.ok()) {
						JsonObject entry = objectOf(answer);
						if (entry == null) failed(new Refused(answer.status(), "unreadable"), "asking about a player");
						else apply(id, entry);
					} else {
						failed(refusal(answer), "asking about a player");
					}
				}));
			}
			return;
		}
		StringBuilder list = new StringBuilder();
		for (UUID id : batch) list.append(list.isEmpty() ? "" : ",").append(id);
		inFlight++;
		site.call("GET", "/api/wear?u=" + list, null, null, TIMEOUT).whenComplete((answer, problem) -> runOnClient(() -> {
			inFlight--;
			if (problem != null) {
				failed(problem, "asking about players");
				return;
			}
			if (!answer.ok()) {
				failed(refusal(answer), "asking about players");
				return;
			}
			JsonObject found = objectOf(answer);
			if (found == null) {
				failed(new Refused(answer.status(), "unreadable"), "asking about players");
				return;
			}
			for (UUID id : batch) {
				JsonElement entry = found.get(id.toString());
				apply(id, entry != null && entry.isJsonObject() ? entry.getAsJsonObject() : null);
			}
		}));
	}

	private static JsonObject objectOf(SiteClient.Answer answer) {
		try {
			JsonElement parsed = JsonParser.parseString(answer.body());
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException unreadable) {
			return null;
		}
	}

	/** What the site says one player wears; null for nothing. */
	private void apply(UUID player, JsonObject entry) {
		if (!asksAboutOthers(status)) return;
		String model = null;
		boolean adult = false;
		Map<String, String> roles = Map.of();
		double scale = 1;
		if (entry != null) {
			try {
				if (entry.has("model") && entry.get("model").isJsonPrimitive()) model = entry.get("model").getAsString();
				adult = entry.has("adult") && entry.get("adult").isJsonPrimitive() && entry.get("adult").getAsBoolean();
				if (entry.has("roles")) roles = Roles.fromJson(entry.get("roles").toString());
				if (entry.get("scale") instanceof com.google.gson.JsonPrimitive number && number.isNumber()) {
					scale = WornModels.cleanScale(number.getAsDouble());
				}
			} catch (RuntimeException odd) {
				model = null;
			}
		}
		show(player, model, adult, roles, scale, status);
		showSkin(player, entry);
	}

	/**
	 * The skin a player put on with the mod, when it is not the one the server handed out:
	 * shown over the server's until they join again and the server has it too.
	 */
	private void showSkin(UUID player, JsonObject entry) {
		if (skins == null) return;
		String texture = null;
		String catalogue = null;
		String file = null;
		String arms = "classic";
		if (entry != null && entry.get("skin") instanceof JsonObject skin) {
			try {
				if (skin.get("texture") != null && skin.get("texture").isJsonPrimitive()) texture = skin.get("texture").getAsString();
				if (skin.get("model") != null && skin.get("model").isJsonPrimitive()) catalogue = skin.get("model").getAsString();
				if (skin.get("file") != null && skin.get("file").isJsonPrimitive()) file = skin.get("file").getAsString();
				if (skin.get("arms") != null && "slim".equals(skin.get("arms").getAsString())) arms = "slim";
			} catch (RuntimeException odd) {
				texture = null;
				catalogue = null;
				file = null;
			}
		}
		if (catalogue != null && catalogue.matches("[0-9A-Za-z]{12}")) {
			skins.showOther(player, Wardrobe.Entry.ofSite(catalogue, "", arms, null, 0));
		} else if (file != null && file.matches("[0-9a-f]{40}")) {
			skins.showOther(player, Wardrobe.Entry.ofFile(file, "", arms, 0));
		} else {
			if (texture != null && (!texture.matches("[0-9a-f]{32,128}") || texture.equals(Skins.serverTexture(player)))) texture = null;
			skins.showOther(player, texture, arms);
		}
	}

	/**
	 * Shows a model on another player once its card is known, or clears it. The card
	 * says whether the model is for adults, whatever the relay's entry says.
	 */
	private void show(UUID player, String model, boolean adult, Map<String, String> roles, double scale, Status path) {
		// With the model shelf hidden, models are neither worn nor drawn on others.
		if (!com.mopicmp.mosaicary.client.MosaicaryClient.MODELS || models == null) model = null;
		if (model == null || adult) {
			if (shownByRelay.remove(player)) takeOff(player);
			return;
		}
		cardFor(model, card -> {
			if (status != path || (path == Status.SITE && !seenAt.containsKey(player))) return;
			if (card == null || card.adult()) {
				if (shownByRelay.remove(player)) takeOff(player);
				return;
			}
			models.setOther(player, card, roles, scale);
			shownByRelay.add(player);
		});
	}

	/** Takes off the model the relay showed on another player. */
	private void takeOff(UUID player) {
		if (models != null) models.setOther(player, null, null, 1);
	}

	/** A model's card: from the catalogue's list when it is there, else from the site, kept for ten minutes. */
	private void cardFor(String id, Consumer<ModelCard> then) {
		for (ModelCard listed : models.cards()) {
			if (listed.id().equals(id)) {
				then.accept(listed);
				return;
			}
		}
		long now = Util.getMillis();
		KnownCard known = cards.get(id);
		if (known != null && now - known.at() < CARD_KEEP_MS) {
			then.accept(known.card());
			return;
		}
		List<Consumer<ModelCard>> waiting = cardWaiters.get(id);
		if (waiting != null) {
			waiting.add(then);
			return;
		}
		waiting = new ArrayList<>();
		waiting.add(then);
		cardWaiters.put(id, waiting);
		site.detail(id).whenComplete((card, problem) -> runOnClient(() -> {
			List<Consumer<ModelCard>> told = cardWaiters.remove(id);
			if (problem != null && SiteClient.unwrap(problem).failure() != SiteClient.Failure.NOT_FOUND) {
				failed(problem, "fetching a model card");
				return;
			}
			cards.put(id, new KnownCard(card, Util.getMillis()));
			if (told != null) told.forEach(one -> one.accept(card));
		}));
	}

	// ------------------------------------------------------------------ deleting

	/** The outcome of "Delete my data". */
	public enum Deleted { DONE, NOT_AVAILABLE, FAILED }

	/**
	 * Deletes what the site keeps about this player and switches sharing off, so
	 * nothing is sent again until the player switches it back on. Works outside a
	 * world too: the proof needs only the profile key.
	 */
	public void deleteMyData(Consumer<Deleted> told) {
		Minecraft minecraft = Minecraft.getInstance();
		settings.sharing = Settings.NO;
		lastSharing = Settings.NO;
		boolean proof = accountStatus(minecraft) == null;
		if (signedInOnSite()) {
			// What is kept under the account on the site first, then under the Minecraft account, if it can prove itself.
			site.call("DELETE", "/api/wear/name", null, settings.siteKey, TIMEOUT).whenComplete((answer, problem) -> runOnClient(() -> {
				if (problem != null || !answer.ok()) {
					told.accept(Deleted.FAILED);
					return;
				}
				skinOnSite = null;
				if (proof) deleteByProof(told);
				else told.accept(Deleted.DONE);
			}));
			return;
		}
		if (!proof) {
			told.accept(Deleted.NOT_AVAILABLE);
			return;
		}
		deleteByProof(told);
	}

	private boolean signedInOnSite() {
		return settings.siteKey != null && !settings.siteKey.isBlank();
	}

	private void deleteByProof(Consumer<Deleted> told) {
		authorised("DELETE", "/api/wear", null, answer -> {
			if (answer.ok()) {
				token = null;
				told.accept(Deleted.DONE);
			} else {
				failed(refusal(answer), "deleting the player's data");
				told.accept(Deleted.FAILED);
			}
		}, () -> told.accept(noKeyUntil > Util.getMillis() ? Deleted.NOT_AVAILABLE : Deleted.FAILED), false);
	}

	/** Whether "Delete my data" can work in this game at all. */
	public boolean canDelete() {
		return accountStatus(Minecraft.getInstance()) == null || signedInOnSite();
	}

	private void deleteWith(String bearer, Consumer<Deleted> told) {
		site.call("DELETE", "/api/wear", null, bearer, TIMEOUT).whenComplete((answer, problem) -> runOnClient(() -> {
			token = null;
			if (problem != null) Mosaicary.LOGGER.debug("Wear relay: deleting after sharing went off failed: {}", SiteClient.describe(problem));
			if (told != null) told.accept(problem == null && answer.ok() ? Deleted.DONE : Deleted.FAILED);
		}));
	}
}
