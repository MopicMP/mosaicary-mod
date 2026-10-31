package com.mopicmp.mosaicary.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.ui.SettingsScreen;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;
import com.mopicmp.mosaicary.client.wear.WearRelay;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * A game Mojang will not take a skin from, signed in to an account on the site, against a
 * running copy of the site ({@code -PtestSite}, {@code -PtestSiteKey}): a file of its own is put
 * on with a real click, kept on the site, sent to the other players, and comes back whole in a
 * copy of the game that has nothing on disk. Skips itself without such a site.
 */
public final class AccountGameTest implements FabricClientGameTest {

	private static String get(String address) {
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> answer = client.send(HttpRequest.newBuilder(URI.create(address)).build(), HttpResponse.BodyHandlers.ofString());
			return answer.statusCode() + " " + answer.body();
		} catch (java.io.IOException | InterruptedException failed) {
			throw new IllegalStateException(failed);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String site = System.getProperty("mosaicary.testSite");
		String key = System.getProperty("mosaicary.testSiteKey");
		if (site == null || key == null) return;
		context.waitForScreen(TitleScreen.class);

		String before = context.computeOnClient(minecraft -> {
			Settings settings = MosaicaryClient.instance().settings();
			String live = MosaicaryClient.instance().skins().site().base();
			MosaicaryClient.instance().skins().site().base(site);
			settings.site = site;
			settings.siteKey = key;
			settings.siteAccount = "Stand one";
			settings.sharing = Settings.YES;
			MosaicaryClient.instance().saveSettings();
			System.out.println("account test: this game's UUID is version " + minecraft.getUser().getProfileId().version()
				+ ", Mojang takes skins from it: " + Skins.canUpload() + ", can wear: " + MosaicaryClient.instance().skins().canWear());
			return live;
		});
		if (!context.computeOnClient(minecraft -> MosaicaryClient.instance().skins().canWear())) {
			throw new AssertionError("Signed in to the site, and Wear still cannot do anything");
		}

		// A file of one's own goes in and is opened.
		Path source = FabricLoader.getInstance().getGameDir().resolve("account-test/my own skin.png");
		context.runOnClient(minecraft -> {
			try {
				Files.createDirectories(source.getParent());
				try (Stream<Path> found = Files.list(FabricLoader.getInstance().getGameDir().resolve("mosaicary/cache/skins/files"))) {
					Files.copy(found.filter(file -> file.toString().endsWith(".png")).findFirst().orElseThrow(), source,
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (java.io.IOException failed) {
				throw new java.io.UncheckedIOException(failed);
			}
			MosaicaryClient.instance().openWardrobe(minecraft.gui.screen());
			((SkinsScreen) minecraft.gui.screen()).onFilesDrop(List.of(source));
		});
		// Any file of the wardrobe's own will do: the same picture may be in it under another name already.
		context.waitFor(minecraft -> MosaicaryClient.instance().skins().wardrobe().shown().stream().anyMatch(one -> one.file != null), 20 * 20);
		Wardrobe.Entry mine = context.computeOnClient(minecraft ->
			MosaicaryClient.instance().skins().wardrobe().shown().stream().filter(one -> one.file != null).findFirst().orElseThrow());
		context.runOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).openEntry(mine));
		context.waitTicks(40);
		context.takeScreenshot("account-1-signed-in-skin");

		// Wear, with a real click.
		Steps.click(context, context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).wearButton()));
		UUID me = context.computeOnClient(minecraft -> minecraft.getUser().getProfileId());
		context.waitFor(minecraft -> MosaicaryClient.instance().skins().shownOn(me) != null, 20 * 20);
		context.waitTicks(10);
		context.takeScreenshot("account-2-worn");
		System.out.println("account test: worn through the site, shown on this player");

		// The file reaches the site with the next bringing in step.
		String sha = mine.file;
		for (int i = 0; i < 30 && !get(site + "/api/wardrobe/files/" + sha + ".png").startsWith("200"); i++) context.waitTicks(20);
		String file = get(site + "/api/wardrobe/files/" + sha + ".png");
		System.out.println("account test: the file on the site: " + file.substring(0, 3));
		if (!file.startsWith("200")) throw new AssertionError("The file did not reach the site");

		context.runOnClient(minecraft -> minecraft.gui.setScreen(new TitleScreen()));
		context.waitTicks(10);
		context.takeScreenshot("account-3-figure-wears-it");

		// In a world the skin goes to the other players with the mod, and theirs come back.
		UUID byName = context.computeOnClient(minecraft -> Skins.offlineId(minecraft.getUser().getName()));
		UUID other = UUID.nameUUIDFromBytes("OfflinePlayer:SomebodyElse".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.getInput().lookAt(0, 0);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(40);
			String wearing = "";
			for (int i = 0; i < 40; i++) {
				wearing = get(site + "/api/wear/" + byName);
				if (wearing.startsWith("200")) break;
				context.waitTicks(20);
			}
			System.out.println("account test: relay status " + context.computeOnClient(minecraft -> MosaicaryClient.instance().relay().status())
				+ ", the site says: " + wearing.substring(0, Math.min(170, wearing.length())));
			if (!wearing.contains(sha)) throw new AssertionError("The site does not know what this player wears: " + wearing);
			context.takeScreenshot("account-4-world");

			// What another game does with that answer.
			JsonObject said = JsonParser.parseString(wearing.substring(4)).getAsJsonObject();
			context.runOnClient(minecraft -> {
				try {
					Method apply = WearRelay.class.getDeclaredMethod("apply", UUID.class, JsonObject.class);
					apply.setAccessible(true);
					apply.invoke(MosaicaryClient.instance().relay(), other, said);
				} catch (ReflectiveOperationException failed) {
					throw new IllegalStateException(failed);
				}
			});
			context.waitFor(minecraft -> MosaicaryClient.instance().skins().shownOn(other) != null, 20 * 20);
			System.out.println("account test: a player wearing it is shown it");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
		}

		// Another copy of the game: nothing on disk, signing in brings the wardrobe back.
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(minecraft -> {
			Skins skins = MosaicaryClient.instance().skins();
			Wardrobe held = skins.wardrobe();
			try {
				try (Stream<Path> walk = Files.walk(held.files().getParent())) {
					for (Path one : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(one);
				}
				Path kept = skins.site().root().resolve("cache/skins/wardrobe");
				if (Files.isDirectory(kept)) {
					try (Stream<Path> walk = Files.walk(kept)) {
						for (Path one : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(one);
					}
				}
				Field field = Skins.class.getDeclaredField("wardrobe");
				field.setAccessible(true);
				field.set(skins, null);
				Field textures = Skins.class.getDeclaredField("textures");
				textures.setAccessible(true);
				((java.util.Map<?, ?>) textures.get(skins)).clear();
			} catch (java.io.IOException | ReflectiveOperationException failed) {
				throw new IllegalStateException(failed);
			}
			if (!skins.wardrobe().shown().isEmpty()) throw new AssertionError("The wardrobe was not emptied");
			MosaicaryClient.instance().wardrobeSync().syncAll();
		});
		context.waitFor(minecraft -> MosaicaryClient.instance().skins().wardrobe().shown().stream().anyMatch(one -> sha.equals(one.file)), 20 * 20);
		Wardrobe.Entry back = context.computeOnClient(minecraft ->
			MosaicaryClient.instance().skins().wardrobe().shown().stream().filter(one -> sha.equals(one.file)).findFirst().orElseThrow());
		context.waitFor(minecraft -> MosaicaryClient.instance().skins().texture(back).state() != Skins.State.LOADING, 20 * 20);
		Skins.State read = context.computeOnClient(minecraft -> MosaicaryClient.instance().skins().texture(back).state());
		System.out.println("account test: the wardrobe came back, the file: " + read + ", name: " + back.name);
		if (read != Skins.State.READY) throw new AssertionError("The file did not come back from the site");
		context.runOnClient(minecraft -> MosaicaryClient.instance().openWardrobe(minecraft.gui.screen()));
		context.waitTicks(5);
		context.runOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).openEntry(back));
		context.waitTicks(40);
		context.takeScreenshot("account-5-came-back");

		// "Delete my data", with a real click: nothing of it stays on the site, and the game signs out.
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new SettingsScreen(minecraft.gui.screen())));
		context.waitTicks(10);
		context.takeScreenshot("account-6-settings");
		Steps.click(context, context.computeOnClient(minecraft -> ((SettingsScreen) minecraft.gui.screen()).deleteButton()));
		context.waitFor(minecraft -> MosaicaryClient.instance().settings().siteKey == null, 20 * 20);
		context.waitTicks(10);
		String wearAfter = get(site + "/api/wear/" + byName);
		String fileAfter = get(site + "/api/wardrobe/files/" + sha + ".png");
		System.out.println("account test: after deleting: worn " + wearAfter.substring(0, 3) + ", file " + fileAfter.substring(0, 3));
		if (!wearAfter.startsWith("204") || !fileAfter.startsWith("404")) throw new AssertionError("Deleting left something on the site");

		// Back to how the run began.
		context.runOnClient(minecraft -> {
			Settings settings = MosaicaryClient.instance().settings();
			settings.site = before;
			settings.siteKey = null;
			settings.siteAccount = null;
			MosaicaryClient.instance().skins().site().base(before);
			MosaicaryClient.instance().saveSettings();
			MosaicaryClient.instance().skins().showOther(other, (String) null, null);
			minecraft.gui.setScreen(new TitleScreen());
		});
		context.waitTicks(5);
	}
}
