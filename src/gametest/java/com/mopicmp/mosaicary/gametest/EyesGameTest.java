package com.mopicmp.mosaicary.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.skins.FreshMoves;
import com.mopicmp.mosaicary.client.skins.SkinCard;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.ui.IconButton;
import com.mopicmp.mosaicary.client.ui.MenuSkin;
import com.mopicmp.mosaicary.client.ui.MenuSkinScreen;
import com.mopicmp.mosaicary.client.ui.SettingsScreen;
import com.mopicmp.mosaicary.client.ui.SkinFileScreen;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * Live eyes with the real Fresh Moves+ and its mods, Mod Menu's button, and the screen that
 * places the figure beside the menu. Needs the other mods given to the run
 * ({@code -PtestMods}, {@code -PtestPacks}); without them it only takes what it can.
 */
public final class EyesGameTest implements FabricClientGameTest {

	/**
	 * Whether a skin would show what becomes of an empty corner: the corners the pack reads its
	 * eyes from hold nothing, and no hat layer covers the eyes, as the pack draws under that layer.
	 */
	private static boolean showsEmptyCorners(int[] pixels) {
		if (pixels == null) return false;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) if (pixels[y * 64 + x] >>> 24 != 0) return false;
			for (int x = 24; x < 32; x++) if (pixels[y * 64 + x] >>> 24 != 0) return false;
		}
		for (int row = 3; row <= 6; row++) {
			for (int column : new int[] { 1, 2, 5, 6 }) if (pixels[(8 + row) * 64 + 40 + column] >>> 24 != 0) return false;
		}
		return true;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(2);
			minecraft.resizeGui();
		});
		context.waitTicks(10);

		// Mod Menu's "Configure" opens the wardrobe's settings.
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			boolean ours = context.computeOnClient(minecraft -> FabricLoader.getInstance()
				.getEntrypointContainers("modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class).stream()
				.filter(container -> container.getProvider().getMetadata().getId().equals("mosaicary"))
				.anyMatch(container -> container.getEntrypoint().getModConfigScreenFactory().create(minecraft.gui.screen())
					instanceof SettingsScreen));
			System.out.println("eyes test: Mod Menu opens our settings: " + ours);
			if (!ours) throw new AssertionError("Mod Menu's Configure does not open the wardrobe's settings");
			context.takeScreenshot("eyes-0-title-with-modmenu");
		}

		// The settings, as Mod Menu opens them.
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new SettingsScreen(minecraft.gui.screen())));
		context.waitTicks(10);
		context.takeScreenshot("eyes-0b-settings");

		// The figure's place and size.
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new MenuSkinScreen(minecraft.gui.screen(), MenuSkin.Beside.PAUSE)));
		context.waitTicks(10);
		context.takeScreenshot("eyes-1a-figure-screen-pause");
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new TitleScreen()));
		context.waitTicks(5);
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new MenuSkinScreen(minecraft.gui.screen(), MenuSkin.Beside.MAIN)));
		context.waitTicks(10);
		context.takeScreenshot("eyes-1-figure-screen");
		// The largest GUI scale the game offers follows from the window, and the figure's place has no say in it.
		int largest = context.computeOnClient(minecraft -> minecraft.getWindow().calculateScale(0, false));
		context.runOnClient(minecraft -> {
			var settings = MosaicaryClient.instance().settings();
			settings.menuSkinX = -40;
			settings.menuSkinY = 20;
			settings.menuSkinSize = 150;
			MosaicaryClient.instance().saveSettings();
		});
		context.waitTicks(5);
		context.takeScreenshot("eyes-2-figure-moved-and-larger");
		int largestAfter = context.computeOnClient(minecraft -> minecraft.getWindow().calculateScale(0, false));
		System.out.println("eyes test: largest GUI scale before and after moving the figure: " + largest + ", " + largestAfter);
		if (largest != largestAfter) throw new AssertionError("Moving the figure changed the GUI scales on offer");
		for (int[] size : new int[][] { { 1280, 720 }, { 951, 588 }, { 903, 447 }, { 634, 392 } }) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(5);
			int most = context.computeOnClient(minecraft -> minecraft.getWindow().calculateScale(0, false));
			System.out.println("eyes test: a window of " + size[0] + " by " + size[1] + " offers GUI scales up to " + most);
		}
		context.getInput().resizeWindow(1280, 720);
		context.waitTicks(5);
		context.runOnClient(minecraft -> {
			var settings = MosaicaryClient.instance().settings();
			settings.menuSkinX = 0;
			settings.menuSkinY = 0;
			settings.menuSkinSize = 100;
			MosaicaryClient.instance().saveSettings();
			minecraft.gui.setScreen(new TitleScreen());
		});
		context.waitTicks(10);

		// Fresh Moves+ put in the run's resource packs, left switched off.
		String packs = System.getProperty("mosaicary.testPacks");
		if (packs != null) {
			context.runOnClient(minecraft -> {
				Path into = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
				try {
					Files.createDirectories(into);
					try (Stream<Path> found = Files.list(Path.of(packs))) {
						for (Path pack : (Iterable<Path>) found::iterator) Files.copy(pack, into.resolve(pack.getFileName()));
					}
				} catch (java.io.IOException failed) {
					throw new java.io.UncheckedIOException(failed);
				}
			});
		}
		FreshMoves.Status before = context.computeOnClient(minecraft -> {
			FreshMoves.rescan();
			return FreshMoves.status();
		});
		System.out.println("eyes test: before " + before);

		// The catalogue showing only skins with live eyes.
		context.runOnClient(minecraft -> {
			SkinsScreen.query(SkinsScreen.query().withEyes(true));
			MosaicaryClient.instance().openWardrobe(minecraft.gui.screen());
		});
		context.waitForScreen(SkinsScreen.class);
		context.waitFor(minecraft -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
		context.waitTicks(40);
		context.takeScreenshot("eyes-3-catalogue-live-eyes");
		int[] card = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).cardCenter(0));
		Steps.click(context, card[0], card[1]);
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.openCard() != null, 100);
		context.waitFor(minecraft -> {
			SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
			return MosaicaryClient.instance().skins().texture(screen.openCard()).state() != Skins.State.LOADING;
		}, 20 * 60);
		context.waitTicks(30);
		context.takeScreenshot("eyes-4-skin-before");

		// The yellow card's own button: switch the pack on, when that is what it offers.
		if (before.missing().isEmpty() && before.packOff() != null) {
			double[] on = context.computeOnClient(minecraft -> {
				for (AbstractWidget widget : Screens.getWidgets(minecraft.gui.screen())) {
					if (widget instanceof IconButton && widget.getMessage().getString().equals(
							net.minecraft.network.chat.Component.translatable("mosaicary.eyes.switch_on").getString())) {
						return new double[] { widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0 };
					}
				}
				return null;
			});
			if (on == null) throw new AssertionError("No Switch on button on the yellow card");
			Steps.click(context, on[0], on[1]);
			context.waitFor(minecraft -> FreshMoves.status().ready(), 20 * 120);
			// The resources reload behind an overlay; a few seconds for it to go.
			context.waitTicks(100);
			context.waitTicks(40);
			System.out.println("eyes test: after switching on " + context.computeOnClient(minecraft -> FreshMoves.status()));
			// Facing the viewer and still, so the face can be looked at; then the eyes should be
			// painted anew every few seconds: a blink, a glance.
			context.runOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).setView(-5, 0));
			int first = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).eyesPainted());
			for (int i = 0; i < 10; i++) {
				context.waitTicks(20);
				context.takeScreenshot("eyes-5-moving-" + i);
			}
			int last = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).eyesPainted());
			System.out.println("eyes test: eyes painted " + first + " -> " + last + " in ten seconds");
			if (last - first < 2) throw new AssertionError("The eyes did not move in ten seconds");
		} else {
			context.takeScreenshot("eyes-5-what-is-missing");
		}

		// The skin with live eyes, kept for the world below.
		SkinCard liveCard = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).openCard());

		// Zoomed in with the wheel over the figure: the head goes on under the card's buttons
		// rather than being cut off below them.
		int[] preview = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).previewCenter());
		double scale = Steps.scale(context);
		context.getInput().setCursorPos(preview[0] * scale, preview[1] * scale);
		context.waitTicks(3);
		context.getInput().scroll(4);
		context.waitTicks(10);
		context.takeScreenshot("eyes-6-zoomed-in");

		// A skin without live eyes, with whatever the run has switched on: its own face, with
		// nothing a model-replacing mod reads from the skin's empty corners drawn over it.
		String search = System.getProperty("mosaicary.testSearch");
		context.runOnClient(minecraft -> {
			SkinsScreen.query(SkinsScreen.query().withEyes(false).withWords(search == null ? "" : search));
			((SkinsScreen) minecraft.gui.screen()).closeDetail();
		});
		context.waitFor(minecraft -> MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.stream()
			.anyMatch(one -> !one.eyes()), 20 * 60);
		context.waitTicks(20);
		// One that can show what becomes of an empty corner; many cannot.
		int cards = context.computeOnClient(minecraft -> MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.size());
		boolean found = false;
		for (int i = 0; i < Math.min(cards, 12) && !found; i++) {
			int index = i;
			boolean eyes = context.computeOnClient(minecraft -> MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.get(index).eyes());
			if (eyes) continue;
			int[] center = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).cardCenter(index));
			Steps.click(context, center[0], center[1]);
			context.waitFor(minecraft -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.openCard() != null, 100);
			context.waitFor(minecraft -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				return MosaicaryClient.instance().skins().texture(screen.openCard()).state() != Skins.State.LOADING;
			}, 20 * 60);
			found = context.computeOnClient(minecraft -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				// A skin asked for by name on the command line is taken as it is.
				return search != null || showsEmptyCorners(MosaicaryClient.instance().skins().texture(screen.openCard()).pixels());
			});
			if (!found) {
				context.runOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).closeDetail());
				context.waitTicks(10);
			}
		}
		if (!found) throw new AssertionError("No skin among the first cards shows what becomes of an empty corner");
		context.runOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).setView(-5, 0));
		context.waitTicks(20);
		context.takeScreenshot("eyes-7-plain-skin-front");
		SkinCard plainOpen = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).openCard());

		// The legs up close, moved into view with the right button: where the two legs overlap and
		// the jacket meets them, parts lying in one plane must not flicker as the figure turns.
		int[] held = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).previewCenter());
		double guiScale = Steps.scale(context);
		context.getInput().setCursorPos(held[0] * guiScale, held[1] * guiScale);
		context.waitTicks(3);
		context.getInput().scroll(7);
		context.waitTicks(3);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		context.getInput().setCursorPos(held[0] * guiScale, (held[1] - 60) * guiScale);
		context.waitTicks(3);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		for (int yaw : new int[] { 0, 8, 17, 26, 35, -12, -24 }) {
			context.runOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).setView(-5, yaw));
			context.waitTicks(3);
			context.takeScreenshot("eyes-7b-legs-near-" + (yaw < 0 ? "m" + -yaw : "" + yaw));
		}
		boolean ready = context.computeOnClient(minecraft -> FreshMoves.status().ready());

		// The catalogue of live eyes again, with the pack on: the cards' eyes move.
		if (ready) {
			context.runOnClient(minecraft -> {
				SkinsScreen.query(SkinsScreen.query().withEyes(true).withWords(""));
				((SkinsScreen) minecraft.gui.screen()).closeDetail();
			});
			context.waitFor(minecraft -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(60);
			for (int i = 0; i < 16; i++) {
				context.waitTicks(10);
				context.takeScreenshot("eyes-7c-cards-moving-" + i);
			}
		}

		// A skin that players without the mod see otherwise, marked as such: on its card and in its panel.
		context.runOnClient(minecraft -> {
			SkinsScreen.query(SkinsScreen.query().withEyes(false).withWords(""));
			((SkinsScreen) minecraft.gui.screen()).closeDetail();
		});
		context.waitFor(minecraft -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
		context.waitFor(minecraft -> ((SkinsScreen) minecraft.gui.screen()).cardsRead(), 20 * 90);
		int clear = context.computeOnClient(minecraft -> {
			var shelved = MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards;
			for (int i = 0; i < shelved.size(); i++) {
				if (MosaicaryClient.instance().skins().look(shelved.get(i)).seeThrough()) return i;
			}
			return -1;
		});
		System.out.println("eyes test: first see-through card: " + clear);
		context.takeScreenshot("eyes-7d-cards-marked");
		SkinCard clearCard = null;
		if (clear >= 0) {
			int shownCard = clear;
			clearCard = context.computeOnClient(minecraft -> MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.get(shownCard));
			SkinCard opening = clearCard;
			context.runOnClient(minecraft -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				screen.openCard(opening);
				screen.setView(-5, 20);
			});
			context.waitFor(minecraft -> MosaicaryClient.instance().skins().texture(opening).state() != Skins.State.LOADING, 20 * 60);
			context.waitTicks(20);
			context.takeScreenshot("eyes-7e-see-through-skin");
			context.runOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).closeDetail());
			context.waitTicks(5);
		}

		// Importing a file: the wardrobe's button opens the window, a click on the skin takes it.
		Path importing = FabricLoader.getInstance().getGameDir().resolve("import-test");
		String pickedName = context.computeOnClient(minecraft -> {
			try {
				Files.createDirectories(importing.resolve("a folder"));
				Path kept = FabricLoader.getInstance().getGameDir().resolve("mosaicary/cache/skins/files");
				try (Stream<Path> listed = Files.list(kept)) {
					Path skin = listed.filter(file -> file.toString().endsWith(".png")).findFirst().orElseThrow();
					Files.copy(skin, importing.resolve("my skin.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				Files.write(importing.resolve("not a skin.png"), new byte[] { 1, 2, 3 });
			} catch (java.io.IOException failed) {
				throw new java.io.UncheckedIOException(failed);
			}
			SkinFileScreen.startAt(importing);
			return "my skin.png";
		});
		context.runOnClient(minecraft -> {
			SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
			minecraft.gui.setScreen(new SkinFileScreen(screen, screen.skins(), file -> screen.onFilesDrop(List.of(file))));
		});
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof SkinFileScreen files && files.itemCenter(pickedName) != null, 20 * 10);
		context.waitTicks(20);
		context.takeScreenshot("eyes-7f-import-window");
		int[] picked = context.computeOnClient(minecraft -> ((SkinFileScreen) minecraft.gui.screen()).itemCenter(pickedName));
		int before2 = context.computeOnClient(minecraft -> MosaicaryClient.instance().skins().wardrobe().shown().size());
		Steps.click(context, picked[0], picked[1]);
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof SkinsScreen
			&& MosaicaryClient.instance().skins().wardrobe().shown().size() > before2, 20 * 10);
		context.waitTicks(20);
		context.takeScreenshot("eyes-7g-imported");
		System.out.println("eyes test: imported a file: my skins " + before2 + " -> "
			+ context.computeOnClient(minecraft -> MosaicaryClient.instance().skins().wardrobe().shown().size()));

		// A file with a background behind everything, as some editors save one: imported, it comes
		// out with its corners empty, so that Fresh Moves has no black to draw over the face.
		Path filled = importing.resolve("filled corners.png");
		String filledName = "filled corners";
		context.runOnClient(minecraft -> {
			try (NativeImage image = NativeImage.read(Files.readAllBytes(importing.resolve("my skin.png")))) {
				for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) image.setPixel(x, y, 0xFF000000);
				image.writeToFile(filled);
			} catch (java.io.IOException failed) {
				throw new java.io.UncheckedIOException(failed);
			}
			((SkinsScreen) minecraft.gui.screen()).onFilesDrop(List.of(filled));
		});
		context.waitFor(minecraft -> MosaicaryClient.instance().skins().wardrobe().shown().stream().anyMatch(one -> filledName.equals(one.name)), 20 * 10);
		boolean emptied = context.computeOnClient(minecraft -> {
			Wardrobe wardrobe = MosaicaryClient.instance().skins().wardrobe();
			Wardrobe.Entry entry = wardrobe.shown().stream().filter(one -> filledName.equals(one.name)).findFirst().orElseThrow();
			try (NativeImage image = NativeImage.read(Files.readAllBytes(wardrobe.fileOf(entry)))) {
				return image.getPixel(0, 7) >>> 24 == 0;
			} catch (java.io.IOException failed) {
				throw new java.io.UncheckedIOException(failed);
			}
		});
		System.out.println("eyes test: an imported file's filled corner is emptied: " + emptied);
		if (!emptied) throw new AssertionError("An imported file kept its filled corner");

		// Game tests end on the main menu.
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new TitleScreen()));
		context.waitTicks(5);

		// The same for a skin the game downloads itself: whatever path it comes by.
		boolean downloadEmptied = context.computeOnClient(minecraft -> {
			try {
				NativeImage image = new NativeImage(64, 64, true);
				for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) image.setPixel(x, y, 0xFF000000);
				SkinTextureDownloader downloader = new SkinTextureDownloader(java.net.Proxy.NO_PROXY, minecraft.getTextureManager(), minecraft);
				var register = SkinTextureDownloader.class.getDeclaredMethod("registerTextureInManager", ClientAsset.Texture.class, NativeImage.class);
				register.setAccessible(true);
				Identifier id = Identifier.fromNamespaceAndPath("mosaicary", "test/filled-corners");
				register.invoke(downloader, new ClientAsset.ResourceTexture(id, id), image);
				return image.getPixel(0, 7) >>> 24 == 0;
			} catch (ReflectiveOperationException failed) {
				throw new IllegalStateException(failed);
			}
		});
		System.out.println("eyes test: a downloaded skin's filled corner is emptied: " + downloadEmptied);
		if (!downloadEmptied) throw new AssertionError("A downloaded skin kept its filled corner");

		// The game's own download keeps a skin's see-through places, with or without Entity Texture Features.
		boolean kept = context.computeOnClient(minecraft -> {
			try (NativeImage image = new NativeImage(64, 64, true)) {
				image.setPixel(8, 8, 0x00000000);
				image.setPixel(24, 24, 0x40FF0000);
				var process = SkinTextureDownloader.class.getDeclaredMethod("processLegacySkin", NativeImage.class, String.class);
				process.setAccessible(true);
				NativeImage out = (NativeImage) process.invoke(null, image, "test");
				return out.getPixel(8, 8) >>> 24 == 0 && out.getPixel(24, 24) >>> 24 == 0x40;
			} catch (ReflectiveOperationException failed) {
				throw new IllegalStateException(failed);
			}
		});
		System.out.println("eyes test: the game's download keeps see-through places: " + kept);
		if (!kept) throw new AssertionError("The game's download made a skin's see-through places solid");

		// In the world, on the player model the pack replaces, each skin shown the way putting it
		// on shows it: the pack draws its eyes out of the skin's corners, and whatever is empty
		// there has to stay empty rather than turn black.
		if (ready) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				context.getInput().lookAt(0, 0);
				context.runOnClient(minecraft -> {
					minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
					minecraft.options.fov().set(30);
					Skins skins = MosaicaryClient.instance().skins();
					skins.showFetched(minecraft.getUser().getProfileId(), "plain", skins.texture(plainOpen));
				});
				context.waitTicks(40);
				context.takeScreenshot("eyes-8-world-plain-skin");
				// The figure beside the pause menu.
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitForScreen(PauseScreen.class);
				context.waitTicks(10);
				context.takeScreenshot("eyes-8b-pause-menu");
				if (context.computeOnClient(minecraft -> MenuSkin.on(minecraft.gui.screen())) == null) {
					throw new AssertionError("No figure beside the pause menu");
				}
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitTicks(10);
				if (clearCard != null) {
					SkinCard wearing = clearCard;
					context.runOnClient(minecraft -> {
						Skins skins = MosaicaryClient.instance().skins();
						skins.showFetched(minecraft.getUser().getProfileId(), "clear", skins.texture(wearing));
					});
					context.waitTicks(40);
					context.takeScreenshot("eyes-8c-world-see-through");
				}
				context.runOnClient(minecraft -> {
					Skins skins = MosaicaryClient.instance().skins();
					skins.showFetched(minecraft.getUser().getProfileId(), "live", skins.texture(liveCard));
				});
				// A blink is over in a moment: frames close together, to catch the pack moving the eyes.
				context.waitTicks(40);
				for (int i = 0; i < 40; i++) {
					context.waitTicks(5);
					context.takeScreenshot("eyes-9-world-live-eyes-" + i);
				}
				context.runOnClient(minecraft -> {
					minecraft.options.setCameraType(CameraType.FIRST_PERSON);
					minecraft.options.fov().set(70);
				});
			}

			// The figure beside the main menu, wearing the same skin: its eyes move too.
			context.waitForScreen(TitleScreen.class);
			context.waitTicks(40);
			int firstOnMenu = context.computeOnClient(minecraft -> MenuSkin.eyesPainted());
			for (int i = 0; i < 6; i++) {
				context.waitTicks(40);
				context.takeScreenshot("eyes-10-menu-figure-" + i);
			}
			int lastOnMenu = context.computeOnClient(minecraft -> MenuSkin.eyesPainted());
			System.out.println("eyes test: the menu figure's eyes painted " + firstOnMenu + " -> " + lastOnMenu + " in twelve seconds");
			if (lastOnMenu - firstOnMenu < 2) throw new AssertionError("The menu figure's eyes did not move");
			context.runOnClient(minecraft -> MosaicaryClient.instance().skins().showOther(minecraft.getUser().getProfileId(), null, null));
		}
	}
}
