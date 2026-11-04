package com.mopicmp.mosaicary.client.skins;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.GsonBuilder;
import com.mopicmp.mosaicary.Mosaicary;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Measures how many skin changes in a row Mojang takes before it says "too many": the
 * number a quick-select ring of skins would live or die by. Nobody publishes it.
 *
 * Run by hand only, with {@code /mosaicary-measure-skins <count> <seconds>}: it puts the
 * player's own current skin back on the account {@code count} times, {@code seconds} apart,
 * switching the arms between classic and slim each time so that every change is a real
 * change, and puts it back as it was at the end. Every answer is written down with its time
 * to {@code mosaicary/measure-skins-<time>.json} in the game folder.
 *
 * It changes the player's real account, which is why nothing starts it but the command.
 */
public final class SkinRateProbe {

	/** One upload: when, which arms, what Mojang answered, how long it took. */
	public record Try(int number, String at, String arms, String outcome, int status, long millis) { }

	private SkinRateProbe() { }

	/**
	 * Runs the measurement off the client thread; {@code said} gets a line for the chat after
	 * every try and the path of the result at the end.
	 */
	public static void run(int count, int seconds, Consumer<String> said) {
		Minecraft minecraft = Minecraft.getInstance();
		String token = minecraft.getUser().getAccessToken();
		String texture = Skins.serverTexture(minecraft.getUser().getProfileId());
		var connection = minecraft.getConnection();
		var info = connection == null ? null : connection.getPlayerInfo(minecraft.getUser().getProfileId());
		if (!Skins.canUpload() || texture == null || info == null) {
			said.accept("Cannot measure here: needs a Mojang account in a world, with a skin of its own on the account");
			return;
		}
		//#if MC>=12002
		String original = info.getSkin().model() == PlayerModelType.SLIM ? "slim" : "classic";
		//#else
		//$$ String original = "slim".equals(info.getModelName()) ? "slim" : "classic";
		//#endif
		Path out = com.mopicmp.mosaicary.platform.Platform.gameDir().resolve("mosaicary")
			.resolve("measure-skins-" + Instant.now().toString().replace(':', '-') + ".json");
		CompletableFuture.runAsync(() -> {
			List<Try> tries = new ArrayList<>();
			try (MojangSkins mojang = new MojangSkins()) {
				byte[] png = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(MojangSkins.textureUrl(texture)))
					.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofByteArray()).body();
				for (int i = 1; i <= count + 1; i++) {
					// The last one puts the skin back with its own arms.
					boolean last = i == count + 1;
					String arms = last ? original : (i % 2 == 1) == original.equals("classic") ? "slim" : "classic";
					long started = System.currentTimeMillis();
					String outcome;
					int status = 200;
					try {
						mojang.upload(token, png, arms).join();
						outcome = "ok";
					} catch (Exception failed) {
						Throwable cause = failed.getCause() != null ? failed.getCause() : failed;
						if (cause instanceof MojangSkins.Refused refused) {
							outcome = refused.failure.name().toLowerCase(java.util.Locale.ROOT);
							status = refused.status;
						} else {
							outcome = "error: " + cause;
							status = 0;
						}
					}
					Try one = new Try(i, Instant.ofEpochMilli(started).toString(), arms, outcome, status,
						System.currentTimeMillis() - started);
					tries.add(one);
					said.accept((last ? "Back as it was: " : "Change " + i + " of " + count + ": ") + outcome);
					if (!last) Thread.sleep(seconds * 1000L);
				}
			} catch (IOException | InterruptedException broken) {
				said.accept("The measurement stopped: " + broken);
			}
			try {
				Files.createDirectories(out.getParent());
				Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(tries), StandardCharsets.UTF_8);
				said.accept("Written to " + out);
			} catch (IOException failed) {
				Mosaicary.LOGGER.warn("Could not write the skin measurement: {}", failed.toString());
			}
		});
	}
}
