package com.mopicmp.mosaicary.client.skins;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.site.SiteClient;
import com.mopicmp.mosaicary.client.wear.WearRelay;
import com.mopicmp.mosaicary.net.MosaicaryPayloads;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * Keeps "My skins" in step with the copies kept elsewhere. Three of them, and any number
 * can be on at once; the copy on this computer is always there.
 *
 * <ul>
 * <li><b>The site, by the Minecraft account.</b> Signed in the way the wear relay signs in
 *     (the profile key the game holds for chat), so no account on the site is needed.
 *     Switched on in the settings.</li>
 * <li><b>The site, by an account on mosaicary.com.</b> The game signs in to the account
 * with a short code, as the Blockbench plugin does, and keeps the key it is given. The
 *     same wardrobe is then on the site's own pages and in every game signed in to it.</li>
 * <li><b>The game server</b>, when it has the mod: it greets the client on joining, and
 *     keeps the wardrobe in the world's folder.</li>
 * </ul>
 *
 * Every copy is brought in step the same way: what is here is sent, the copy brings the
 * two together and answers with the result, and the result is taken in here. A change here
 * is sent three seconds later, so a burst of changes goes as one.
 *
 * Everything runs on the client thread; answers hop back to it.
 */
public final class WardrobeSync {

	public enum Where { PLAYER, ACCOUNT, SERVER }

	/** How a copy stands: last brought in step, busy, or what went wrong. */
	private static final class Standing {
		long at;
		boolean busy;
		String problem;
	}

	private static final long CHANGE_DELAY_MS = 3_000;
	private static final long AGAIN_MS = 5 * 60_000;
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final String DEVICE = "Minecraft (Mosaicary)";

	private final Skins skins;
	private final WearRelay relay;
	private final Settings settings;
	private final Runnable saveSettings;
	private final SiteClient site;
	private final Map<Where, Standing> standing = new EnumMap<>(Where.class);
	private long dueAt = -1;
	private long lastAll;
	private boolean serverKeeps;
	private Wardrobe listening;

	public WardrobeSync(Skins skins, WearRelay relay, Settings settings, Runnable saveSettings) {
		this.skins = skins;
		this.relay = relay;
		this.settings = settings;
		this.saveSettings = saveSettings;
		this.site = skins.site();
		for (Where where : Where.values()) standing.put(where, new Standing());
		skins.onWardrobe(this::listenTo);
	}

	private void listenTo(Wardrobe wardrobe) {
		if (listening == wardrobe) return;
		listening = wardrobe;
		wardrobe.listen(this::changedHere);
	}

	private boolean taking;
	private boolean restored;

	private void changedHere() {
		// Taking in a copy's answer is a change too; it is not sent straight back.
		if (taking) return;
		dueAt = Util.getMillis() + CHANGE_DELAY_MS;
	}

	/** Called every client tick. */
	public void tick() {
		pollLink();
		if (!restored && on(Where.ACCOUNT)) {
			// As soon as the game runs, not when the wardrobe is first opened: what the player wears
			// may be known only to the site, and everybody else already sees it.
			restored = true;
			skins.restoreWorn();
			syncAll();
		}
		long now = Util.getMillis();
		if (dueAt >= 0 && now >= dueAt) {
			dueAt = -1;
			syncAll();
		} else if (now - lastAll >= AGAIN_MS && (on(Where.PLAYER) || on(Where.ACCOUNT) || serverKeeps)) {
			syncAll();
		}
	}

	/** Brings every copy that is on in step now: on opening the wardrobe, on joining, after a change. */
	public void syncAll() {
		lastAll = Util.getMillis();
		skins.wardrobe();
		if (on(Where.PLAYER)) syncPlayer();
		if (on(Where.ACCOUNT)) syncAccount();
		if (serverKeeps) syncServer();
	}

	/** Whether a copy is switched on (the server's is on whenever the server keeps wardrobes). */
	public boolean on(Where where) {
		return switch (where) {
			case PLAYER -> settings.wardrobeOnSite;
			case ACCOUNT -> settings.siteKey != null && !settings.siteKey.isBlank();
			case SERVER -> serverKeeps;
		};
	}

	private String body() {
		return "{\"entries\":" + skins.wardrobe().travellingJson(true) + "}";
	}

	/** Takes in a copy's answer: {@code {"entries": [...]}} or a bare array. */
	private void takeIn(Where where, String json) {
		Standing standing = this.standing.get(where);
		standing.busy = false;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			String entries = parsed.isJsonObject() && parsed.getAsJsonObject().has("entries")
				? parsed.getAsJsonObject().get("entries").toString() : parsed.toString();
			taking = true;
			try {
				skins.wardrobe().mergeIn(Wardrobe.fromJson(entries), System.currentTimeMillis());
			} finally {
				taking = false;
			}
			standing.at = System.currentTimeMillis();
			standing.problem = null;
			if (where != Where.SERVER && parsed.isJsonObject() && parsed.getAsJsonObject().get("files") instanceof JsonArray kept) sendFiles(where, kept);
			if (where == Where.ACCOUNT) skins.restoreWorn();
		} catch (RuntimeException unreadable) {
			standing.problem = "odd";
		}
	}

	/** What has been sent in this game and not refused, so that a failed file is tried again and a kept one is not. */
	private final java.util.Set<String> sentFiles = new java.util.HashSet<>();

	/**
	 * Sends the player's own files the site does not keep yet, so that the wardrobe comes back
	 * whole in another copy of the game. Each is read off the client thread; a refusal is not
	 * the wardrobe's end, and the next time they are brought in step it is tried again.
	 */
	private void sendFiles(Where where, JsonArray kept) {
		java.util.Set<String> there = new java.util.HashSet<>();
		for (JsonElement one : kept) if (one.isJsonPrimitive()) there.add(one.getAsString());
		Wardrobe wardrobe = skins.wardrobe();
		for (Wardrobe.Entry entry : wardrobe.shown()) {
			if (entry.file == null || there.contains(entry.file)) continue;
			String mark = where + ":" + entry.file;
			java.nio.file.Path file = wardrobe.fileOf(entry);
			if (!java.nio.file.Files.isRegularFile(file) || !sentFiles.add(mark)) continue;
			String path = "/api/wardrobe/files/" + entry.file;
			java.util.concurrent.CompletableFuture.supplyAsync(() -> {
				try {
					JsonObject body = new JsonObject();
					body.addProperty("png", java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(file)));
					return body.toString();
				} catch (java.io.IOException unreadable) {
					return null;
				}
			}, site.work()).thenAccept(json -> Minecraft.getInstance().execute(() -> {
				if (json == null) return;
				java.util.function.Consumer<SiteClient.Answer> done = answer -> {
					if (!answer.ok()) {
						sentFiles.remove(mark);
						Mosaicary.LOGGER.info("The site did not keep a wardrobe file: {}", answer.status());
					}
				};
				if (where == Where.PLAYER) {
					relay.signedIn("PUT", path, json, done, () -> sentFiles.remove(mark));
				} else {
					site.call("PUT", path, json, settings.siteKey, TIMEOUT).whenComplete((answer, problem) -> Minecraft.getInstance().execute(() -> {
						if (problem != null) sentFiles.remove(mark);
						else done.accept(answer);
					}));
				}
			}));
		}
	}

	private void failed(Where where, String problem) {
		Standing standing = this.standing.get(where);
		standing.busy = false;
		standing.problem = problem;
	}

	// ------------------------------------------------------------------ the site, by the Minecraft account

	private void syncPlayer() {
		Standing standing = this.standing.get(Where.PLAYER);
		if (standing.busy) return;
		if (!relay.canSignIn()) {
			standing.problem = "account";
			return;
		}
		standing.busy = true;
		relay.signedIn("PUT", "/api/wardrobe", body(), answer -> {
			if (answer.ok()) takeIn(Where.PLAYER, answer.body());
			else failed(Where.PLAYER, answer.status() == 404 ? "site_old" : "site");
		}, () -> failed(Where.PLAYER, "sign_in"));
	}

	/** Switches the copy kept on the site by the Minecraft account on or off; off deletes it there. */
	public void setOnSite(boolean on) {
		if (settings.wardrobeOnSite == on) return;
		settings.wardrobeOnSite = on;
		saveSettings.run();
		if (on) {
			syncPlayer();
		} else if (relay.canSignIn()) {
			relay.signedIn("DELETE", "/api/wardrobe", null, answer -> { }, () -> { });
		}
	}

	// ------------------------------------------------------------------ the site, by an account there

	private void syncAccount() {
		Standing standing = this.standing.get(Where.ACCOUNT);
		if (standing.busy) return;
		standing.busy = true;
		site.call("PUT", "/api/wardrobe", body(), settings.siteKey, TIMEOUT).whenComplete((answer, problem) ->
			Minecraft.getInstance().execute(() -> {
				if (problem != null) {
					failed(Where.ACCOUNT, "offline");
					return;
				}
				if (answer.status() == 401) {
					// The key was taken back on the site: the game is signed out.
					Mosaicary.LOGGER.info("The site no longer takes the account key; signed out");
					settings.siteKey = null;
					settings.siteAccount = null;
					saveSettings.run();
					failed(Where.ACCOUNT, "signed_out");
					return;
				}
				if (answer.ok()) takeIn(Where.ACCOUNT, answer.body());
				else failed(Where.ACCOUNT, answer.status() == 404 ? "site_old" : "site");
			}));
	}

	/** Where signing in by code stands, for the settings screen. */
	public record Link(String code, String url, String state) { }

	private Link link;
	private String linkSecret;
	private long linkNext;
	private long linkInterval = 3_000;

	public Link link() {
		return link;
	}

	/**
	 * Starts signing the game in to an account on the site: asks for a code, opens the page
	 * where it is confirmed, and asks every few seconds whether it was.
	 */
	public void startLink() {
		JsonObject body = new JsonObject();
		body.addProperty("device", DEVICE);
		link = new Link("", "", "starting");
		site.call("POST", "/api/link", body.toString(), null, TIMEOUT).whenComplete((answer, problem) ->
			Minecraft.getInstance().execute(() -> {
				if (problem != null || !answer.ok()) {
					link = new Link("", "", "failed");
					return;
				}
				try {
					JsonObject started = JsonParser.parseString(answer.body()).getAsJsonObject();
					linkSecret = started.get("secret").getAsString();
					linkInterval = Math.max(2, started.has("interval") ? started.get("interval").getAsInt() : 3) * 1000L;
					linkNext = Util.getMillis() + linkInterval;
					link = new Link(started.get("code").getAsString(), started.get("url").getAsString(), "waiting");
					com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(link.url()));
				} catch (RuntimeException odd) {
					link = new Link("", "", "failed");
				}
			}));
	}

	/** Asks whether the code was agreed to; called every tick of the settings screen. */
	public void pollLink() {
		if (link == null || !"waiting".equals(link.state()) || linkSecret == null || Util.getMillis() < linkNext) return;
		linkNext = Long.MAX_VALUE;
		JsonObject body = new JsonObject();
		body.addProperty("secret", linkSecret);
		site.call("POST", "/api/link/poll", body.toString(), null, TIMEOUT).whenComplete((answer, problem) ->
			Minecraft.getInstance().execute(() -> {
				linkNext = Util.getMillis() + linkInterval;
				if (problem != null || !answer.ok()) return;
				try {
					JsonObject said = JsonParser.parseString(answer.body()).getAsJsonObject();
					String state = said.get("state").getAsString();
					if (state.equals("taken")) {
						settings.siteKey = said.get("key").getAsString();
						settings.siteAccount = said.has("handle") ? said.get("handle").getAsString() : "";
						saveSettings.run();
						link = new Link(link.code(), link.url(), "taken");
						linkSecret = null;
						syncAccount();
					} else if (!state.equals("waiting")) {
						link = new Link(link.code(), link.url(), state);
						linkSecret = null;
					}
				} catch (RuntimeException odd) {
					// Asked again on the next turn.
				}
			}));
	}

	/** Forgets the account key here. The key itself is withdrawn on the site, in the account's list of keys. */
	public void signOut() {
		settings.siteKey = null;
		settings.siteAccount = null;
		saveSettings.run();
		link = null;
		standing.get(Where.ACCOUNT).problem = null;
	}

	// ------------------------------------------------------------------ the game server

	/** The server greeted the client: it keeps wardrobes. */
	public void serverGreeted() {
		serverKeeps = true;
		syncServer();
	}

	public void leftServer() {
		serverKeeps = false;
		standing.get(Where.SERVER).busy = false;
	}

	public boolean serverKeeps() {
		return serverKeeps;
	}

	private void syncServer() {
		if (!serverKeeps || !ClientPlayNetworking.canSend(MosaicaryPayloads.WardrobeState.TYPE)) return;
		String json = skins.wardrobe().travellingJson();
		if (json.length() > MosaicaryPayloads.WARDROBE_MOST) return;
		standing.get(Where.SERVER).busy = true;
		ClientPlayNetworking.send(new MosaicaryPayloads.WardrobeState(json));
	}

	/** The server's answer: the wardrobe brought together. */
	public void fromServer(String json) {
		takeIn(Where.SERVER, json);
	}

	// ------------------------------------------------------------------ words for the screen

	/** One line for the wardrobe's card: where the wardrobe is kept. */
	public Component summary() {
		int elsewhere = 0;
		for (Where where : Where.values()) if (on(where)) elsewhere++;
		if (elsewhere == 0) return Component.translatable("mosaicary.skins.mine.here");
		boolean trouble = false;
		for (Where where : Where.values()) trouble |= on(where) && standing.get(where).problem != null;
		return Component.translatable(trouble ? "mosaicary.skins.sync.trouble" : "mosaicary.skins.sync.count", elsewhere + 1);
	}

	/** The tooltip of that line: each copy and how it stands. */
	public Component details() {
		MutableComponent out = Component.translatable("mosaicary.skins.sync.here");
		for (Where where : Where.values()) {
			String name = where.name().toLowerCase(java.util.Locale.ROOT);
			Standing standing = this.standing.get(where);
			String state = !on(where) ? "off" : standing.busy ? "busy" : standing.problem != null ? "problem." + standing.problem
				: standing.at > 0 ? "done" : "waiting";
			Object who = where == Where.ACCOUNT && settings.siteAccount != null ? settings.siteAccount : "";
			out.append("\n").append(Component.translatable("mosaicary.skins.sync." + name, who))
				.append(": ").append(Component.translatable("mosaicary.skins.sync.state." + state));
		}
		return out;
	}
}
