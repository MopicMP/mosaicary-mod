package com.mopicmp.mosaicary.gametest;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.compat.GenderFigure;
import com.mopicmp.mosaicary.client.compat.GenderMod;
import com.mopicmp.mosaicary.client.skins.FreshMoves;
import com.mopicmp.mosaicary.client.skins.LiveEyes;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Female Gender Mod's breasts on the figures the mod draws itself, by the player's body in that mod
 * ({@code -PtestMods} with it, and with 3D Skin Layers for their cubes): a girl of the catalogue
 * from the front, turned and in profile, walking with the bounce switched on; none with a male body;
 * the figure beside the main menu, where the body is read from the mod's own file. Screenshots, to
 * be looked at.
 */
public final class GenderFigureGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		if (!context.computeOnClient((Minecraft minecraft) -> GenderMod.available())) return;
		context.getInput().resizeWindow(1280, 720);
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(40);
		SkinsScreen.query(SkinsScreen.query().withCategory("girls"));
		try (Steps.LightWorld world = Steps.lightWorld(context)) {
			context.getInput().resizeWindow(1280, 720);
			body(context, "female", 0.8f);
			liveEyes(context);
			openFirstGirl(context);
			float[][] views = { { -5, 0 }, { -10, 45 }, { -5, 90 } };
			for (int i = 0; i < views.length; i++) {
				float[] view = views[i];
				context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).setView(view[0], view[1]));
				context.waitTicks(10);
				context.takeScreenshot("gender-figure-" + i + "-female-" + (int) view[1]);
			}
			// Walking in profile, at set moments of the walk, the bounce on.
			context.runOnClient((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				screen.setView(-5, 90);
				set(screen, "walking", true);
			});
			for (int frame = 0; frame < 6; frame++) {
				float at = 1 + frame * 0.1f;
				context.runOnClient((Minecraft minecraft) -> SkinsScreen.stoppedAt = at);
				context.waitTicks(2);
				context.takeScreenshot("gender-figure-3-walk-" + frame);
			}
			context.runOnClient((Minecraft minecraft) -> {
				SkinsScreen.stoppedAt = null;
				set(minecraft.gui.screen(), "walking", false);
			});
			// The same skin and body on the player, drawn by Female Gender Mod itself, to set beside the preview.
			context.runOnClient((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				Skins skins = MosaicaryClient.instance().skins();
				skins.showFetched(minecraft.getUser().getProfileId(), "figure", skins.texture(screen.openCard()));
				minecraft.gui.setScreen(null);
				minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
				minecraft.options.fov().set(20);
				Steps.hideGui(minecraft, true);
			});
			context.waitForScreen(null);
			context.getInput().lookAt(0, 15);
			context.waitTicks(40);
			context.takeScreenshot("gender-figure-6-world-front");
			// The head turned and the body, standing still, left where it was: the chest from aside.
			context.getInput().lookAt(40, 15);
			context.waitTicks(10);
			context.takeScreenshot("gender-figure-7-world-turned");
			context.getInput().lookAt(0, 0);
			context.runOnClient((Minecraft minecraft) -> {
				minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
				minecraft.options.fov().set(70);
				Steps.hideGui(minecraft, false);
			});
			openFirstGirl(context);
			body(context, "male", 0.8f);
			context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).setView(-10, 45));
			context.waitTicks(10);
			context.takeScreenshot("gender-figure-4-male");
			// Back to a female body for the menu's figure, saved by the mod to its file.
			body(context, "female", 0.8f);
			context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(null); });
			context.waitForScreen(null);
		} finally {
			SkinsScreen.query(SkinsScreen.query().withCategory("any"));
		}
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);
		context.takeScreenshot("gender-figure-5-menu");
		System.out.println("gender figure test: the menu's figure has the body " + context.computeOnClient((Minecraft minecraft) ->
			String.valueOf(GenderMod.current(minecraft.getUser().getProfileId()))));
	}

	/**
	 * A girl with live eyes: the picture the figure with breasts is drawn from follows the eyes'
	 * texture, its face the same as that one's each time they move, and not the skin's own face, which
	 * holds only the marks the eyes are read from. With Fresh Moves+ given ({@code -PtestPacks}) it is
	 * switched on, and the preview's face is shown up close to be looked at, with the breasts and
	 * without them.
	 */
	private static void liveEyes(ClientGameTestContext context) {
		openLiveGirl(context);
		LiveEyes eyes = context.computeOnClient((Minecraft minecraft) ->
			LiveEyes.of(MosaicaryClient.instance().skins().texture(((SkinsScreen) minecraft.gui.screen()).openCard()).pixels()));
		if (eyes == null) throw new AssertionError("The first girl with live eyes has none to move");
		try {
			int checks = 0;
			int painted = 0;
			// As the eyes are first painted, then until they have moved twice.
			for (int tick = 0; tick < 20 * 30 && painted < 3; tick++) {
				int[] seen = context.computeOnClient((Minecraft minecraft) -> {
					Identifier frame = eyes.frame();
					int[] pixels = MosaicaryClient.instance().skins().texture(((SkinsScreen) minecraft.gui.screen()).openCard()).pixels();
					GenderFigure.Picture picture = GenderFigure.picture(frame, pixels, null);
					if (picture == null) throw new AssertionError("No picture for the figure with breasts");
					NativeImage live = ((DynamicTexture) minecraft.getTextureManager().getTexture(frame)).getPixels();
					NativeImage shown = ((DynamicTexture) minecraft.getTextureManager().getTexture(picture.texture())).getPixels();
					int wrong = 0;
					// The face, from (8, 8) to (16, 16) of a skin, at the eyes' size.
					int scale = live.getWidth() / 64;
					for (int y = 8 * scale; y < 16 * scale; y++) {
						for (int x = 8 * scale; x < 16 * scale; x++) {
							if (live.getPixel(x, y) != shown.getPixel(x * shown.getWidth() / live.getWidth(), y * shown.getWidth() / live.getWidth())) wrong++;
						}
					}
					return new int[] { wrong, eyes.painted() };
				});
				checks++;
				painted = seen[1];
				if (seen[0] != 0) {
					throw new AssertionError("The figure's face differs from the live eyes' in " + seen[0] + " pixels, painted " + seen[1] + " times");
				}
				context.waitTick();
			}
			System.out.println("gender figure test: the face follows the live eyes, " + checks + " frames, painted " + painted + " times");
			if (painted < 3) throw new AssertionError("The live eyes did not move twice in thirty seconds");
		} finally {
			context.runOnClient((Minecraft minecraft) -> eyes.close());
		}
		String packs = System.getProperty("mosaicary.testPacks");
		if (packs == null) return;
		context.runOnClient((Minecraft minecraft) -> {
			Path into = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
			try {
				Files.createDirectories(into);
				try (Stream<Path> found = Files.list(Path.of(packs))) {
					for (Path pack : (Iterable<Path>) found::iterator) Files.copy(pack, into.resolve(pack.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (java.io.IOException failed) {
				throw new java.io.UncheckedIOException(failed);
			}
			FreshMoves.rescan();
			FreshMoves.Status status = FreshMoves.status();
			System.out.println("gender figure test: Fresh Moves " + status);
			if (status.missing().isEmpty() && status.packOff() != null) FreshMoves.switchOn(status.packOff());
		});
		if (!context.computeOnClient((Minecraft minecraft) -> FreshMoves.status().ready())) {
			context.waitFor((Minecraft minecraft) -> FreshMoves.status().ready(), 20 * 120);
		}
		// The resources reload behind an overlay; a few seconds for it to go.
		context.waitTicks(140);
		openLiveGirl(context);
		context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).setView(-5, 0));
		int[] preview = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).previewCenter());
		double scale = Steps.scale(context);
		context.getInput().setCursorPos(preview[0] * scale, preview[1] * scale);
		context.waitTicks(3);
		context.getInput().scroll(4);
		for (int i = 0; i < 6; i++) {
			context.waitTicks(20);
			context.takeScreenshot("gender-figure-eyes-" + i);
		}
		System.out.println("gender figure test: the preview's eyes painted " + context.computeOnClient((Minecraft minecraft) ->
			((SkinsScreen) minecraft.gui.screen()).eyesPainted()) + " times");
		// The same without the breasts, as the figure was drawn before them; from the front, and from above and aside.
		for (float[] view : new float[][] { { -5, 0 }, { 25, 35 }, { 25, -35 } }) {
			context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).setView(view[0], view[1]));
			for (boolean breasts : new boolean[] { true, false }) {
				context.runOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().genderOnFigures = breasts);
				for (int i = 0; i < 3; i++) {
					context.waitTicks(15);
					context.takeScreenshot("gender-figure-eyes-" + (int) view[0] + "-" + (int) view[1] + (breasts ? "-with-" : "-without-") + i);
				}
			}
		}
		context.runOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().genderOnFigures = true);
	}

	/**
	 * A girl with live eyes: the one asked for by its id ({@code -PtestSkin}), among those the words
	 * of {@code -PtestSearch} find, else the first.
	 */
	private static void openLiveGirl(ClientGameTestContext context) {
		String search = System.getProperty("mosaicary.testSearch");
		String id = System.getProperty("mosaicary.testSkin");
		SkinsScreen.query(SkinsScreen.query().withEyes(true).withWords(search == null ? "" : search));
		try {
			if (id == null) {
				openFirstGirl(context);
				return;
			}
			context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(new SkinsScreen(null, MosaicaryClient.instance().skins())); });
			context.waitForScreen(SkinsScreen.class);
			context.waitFor((Minecraft minecraft) -> MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.stream()
				.anyMatch(card -> card.id().equals(id)), 20 * 60);
			context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).openCard(MosaicaryClient.instance().skins()
				.shelf(SkinsScreen.query()).cards.stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow()));
			context.waitFor((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				return MosaicaryClient.instance().skins().texture(screen.openCard()).state() != Skins.State.LOADING;
			}, 20 * 60);
			System.out.println("gender figure test: opened " + id + " " + context.computeOnClient((Minecraft minecraft) ->
				((SkinsScreen) minecraft.gui.screen()).openCard().name()));
			context.waitTicks(10);
		} finally {
			SkinsScreen.query(SkinsScreen.query().withEyes(false).withWords(""));
		}
	}

	private static void body(ClientGameTestContext context, String gender, float size) {
		context.runOnClient((Minecraft minecraft) -> {
			JsonObject body = new JsonObject();
			body.addProperty("gender", gender);
			body.addProperty("bust_size", size);
			body.addProperty("breast_physics", true);
			boolean put = GenderMod.put(minecraft.getUser().getProfileId(), body);
			System.out.println("gender figure test: the " + gender + " body " + (put ? "is on" : "was not taken"));
		});
		context.waitTicks(5);
	}

	private static void openFirstGirl(ClientGameTestContext context) {
		context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(new SkinsScreen(null, MosaicaryClient.instance().skins())); });
		context.waitForScreen(SkinsScreen.class);
		context.waitFor((Minecraft minecraft) -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
		context.waitTicks(20);
		int[] center = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).cardCenter(0));
		Steps.click(context, center[0], center[1]);
		context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.openCard() != null, 100);
		context.waitFor((Minecraft minecraft) -> {
			SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
			return MosaicaryClient.instance().skins().texture(screen.openCard()).state() != Skins.State.LOADING;
		}, 20 * 60);
		System.out.println("gender figure test: opened " + context.computeOnClient((Minecraft minecraft) ->
			((SkinsScreen) minecraft.gui.screen()).openCard().name()));
		context.waitTicks(10);
	}

	private static void set(Object screen, String name, Object value) {
		try {
			Field field = SkinsScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(screen, value);
		} catch (ReflectiveOperationException failed) {
			throw new AssertionError("No field " + name + " on the skins screen", failed);
		}
	}
}
