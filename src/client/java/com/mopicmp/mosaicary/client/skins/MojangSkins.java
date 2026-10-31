package com.mopicmp.mosaicary.client.skins;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Puts a skin on the player's Minecraft account, the way the official launcher does.
 *
 * The PNG goes up as a file ({@code multipart/form-data}, fields {@code variant} and
 * {@code file}) with the access token the game already holds for this session. Mojang's
 * other way, a JSON body naming an address, has been refused by their firewall since
 * October 2026 (a 403 page of HTML from Azure, whatever the network); sending the file works,
 * and is what Prism and Modrinth do too.
 *
 * The answer is the profile with its skins; the active one's address on
 * {@code textures.minecraft.net} ends in the texture's hash, which is the skin's name from
 * then on: anybody can fetch the picture by it, and every server hands it out.
 *
 * Nothing here touches the game; the caller hops back to the client thread.
 */
public final class MojangSkins implements AutoCloseable {

	static final String SKINS = "https://api.minecraftservices.com/minecraft/profile/skins";
	private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";

	/** Why Mojang did not take the skin, as a code the interface has words for. */
	public enum Failure {
		/** The game holds no token Mojang takes: an offline account, or a session that ran out. */
		SIGN_IN,
		/** Too many changes in a short time. */
		TOO_MANY,
		/** Mojang refused the picture itself. */
		REFUSED,
		/** A firewall or a page of HTML instead of an answer. */
		BLOCKED,
		/** No answer at all. */
		NO_ANSWER,
		/** An answer, but not one that says which skin is on. */
		ODD_ANSWER
	}

	public static final class Refused extends RuntimeException {
		public final Failure failure;
		public final int status;

		public Refused(Failure failure, int status, String said) {
			super(said);
			this.failure = failure;
			this.status = status;
		}
	}

	/**
	 * The skin now on the account.
	 *
	 * @param url     its address on textures.minecraft.net
	 * @param texture the hash at the end of that address
	 * @param arms    {@code classic} or {@code slim}
	 */
	public record Applied(String url, String texture, String arms) { }

	private final HttpClient http;
	private final ExecutorService work;

	public MojangSkins() {
		this.work = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "Mosaicary skin upload");
			thread.setDaemon(true);
			return thread;
		});
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).executor(work).build();
	}

	/**
	 * Sends the skin.
	 *
	 * @param token the game's access token for this session
	 * @param png   the skin file, 64 x 64 or 64 x 32
	 * @param arms  {@code classic} or {@code slim}
	 */
	public CompletableFuture<Applied> upload(String token, byte[] png, String arms) {
		if (token == null || token.isBlank() || token.length() < 20) {
			return CompletableFuture.failedFuture(new Refused(Failure.SIGN_IN, 0, "no access token"));
		}
		String variant = "slim".equals(arms) ? "slim" : "classic";
		String boundary = "mosaicary-" + UUID.randomUUID().toString().replace("-", "");
		HttpRequest request = HttpRequest.newBuilder(URI.create(SKINS))
			.timeout(Duration.ofSeconds(30))
			.header("Authorization", "Bearer " + token)
			.header("Accept", "application/json")
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, variant, png)))
			.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).handle((response, problem) -> {
			if (problem != null) throw new Refused(Failure.NO_ANSWER, 0, String.valueOf(unwrap(problem)));
			return answer(response.statusCode(), response.body() == null ? "" : response.body(), variant);
		});
	}

	/**
	 * The skin on the account right now. The account service answers at once, unlike the session
	 * service the game asks for its own profile when it starts: that one keeps an old answer for
	 * a while, and gives none at all to a game asking too soon after another.
	 */
	public CompletableFuture<Applied> current(String token) {
		if (token == null || token.isBlank() || token.length() < 20) {
			return CompletableFuture.failedFuture(new Refused(Failure.SIGN_IN, 0, "no access token"));
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE))
			.timeout(Duration.ofSeconds(15))
			.header("Authorization", "Bearer " + token)
			.header("Accept", "application/json")
			.GET()
			.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).handle((response, problem) -> {
			if (problem != null) throw new Refused(Failure.NO_ANSWER, 0, String.valueOf(unwrap(problem)));
			return answer(response.statusCode(), response.body() == null ? "" : response.body(), "classic");
		});
	}

	/** The form Mojang reads: the variant as a field, the PNG as a file. */
	static byte[] multipart(String boundary, String variant, byte[] png) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + 400);
		String head = "--" + boundary + "\r\n"
			+ "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
			+ variant + "\r\n"
			+ "--" + boundary + "\r\n"
			+ "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
			+ "Content-Type: image/png\r\n\r\n";
		out.writeBytes(head.getBytes(StandardCharsets.UTF_8));
		out.writeBytes(png);
		out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	/** Reads Mojang's answer, or throws the refusal it amounts to. */
	static Applied answer(int status, String body, String variant) {
		String trimmed = body.stripLeading();
		boolean html = trimmed.startsWith("<");
		if (status == 401) throw new Refused(Failure.SIGN_IN, status, "Mojang did not take the session's token");
		if (status == 429) throw new Refused(Failure.TOO_MANY, status, "too many skin changes");
		if (html) throw new Refused(Failure.BLOCKED, status, "an HTML page instead of an answer");
		if (status == 400) throw new Refused(Failure.REFUSED, status, shortly(body));
		if (status == 403) throw new Refused(Failure.BLOCKED, status, shortly(body));
		if (status < 200 || status >= 300) throw new Refused(Failure.ODD_ANSWER, status, shortly(body));
		try {
			JsonObject profile = JsonParser.parseString(body).getAsJsonObject();
			JsonElement skins = profile.get("skins");
			if (skins != null && skins.isJsonArray()) {
				for (JsonElement one : skins.getAsJsonArray()) {
					if (!one.isJsonObject()) continue;
					JsonObject skin = one.getAsJsonObject();
					String state = skin.has("state") ? skin.get("state").getAsString() : "";
					if (!state.equalsIgnoreCase("ACTIVE")) continue;
					String url = skin.has("url") ? skin.get("url").getAsString() : "";
					String hash = textureOf(url);
					if (hash == null) continue;
					String arms = skin.has("variant") && skin.get("variant").getAsString().equalsIgnoreCase("SLIM") ? "slim" : "classic";
					return new Applied(url, hash, arms);
				}
			}
		} catch (RuntimeException unreadable) {
			throw new Refused(Failure.ODD_ANSWER, status, "the answer is not a profile");
		}
		throw new Refused(Failure.ODD_ANSWER, status, "no active skin in the answer");
	}

	/** The hash at the end of a textures.minecraft.net address, or null when the address is not one. */
	public static String textureOf(String url) {
		if (url == null) return null;
		String lower = url.toLowerCase(Locale.ROOT);
		int at = lower.indexOf("textures.minecraft.net/texture/");
		if (at < 0) return null;
		String hash = lower.substring(at + "textures.minecraft.net/texture/".length());
		return hash.matches("[0-9a-f]{32,128}") ? hash : null;
	}

	/** Where a texture's picture is, by its hash. */
	public static String textureUrl(String hash) {
		return "https://textures.minecraft.net/texture/" + hash;
	}

	private static String shortly(String body) {
		String flat = body.replaceAll("\\s+", " ").strip();
		return flat.length() > 200 ? flat.substring(0, 200) + "..." : flat;
	}

	private static Throwable unwrap(Throwable problem) {
		Throwable cause = problem;
		while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
		return cause;
	}

	@Override
	public void close() {
		work.shutdownNow();
		http.shutdownNow();
	}
}
