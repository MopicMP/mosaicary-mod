package com.mopicmp.mosaicary.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.platform.ClientPlatform;
import com.mopicmp.mosaicary.client.render.Pictures;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
import com.mopicmp.mosaicary.client.skins.FreshMoves;
import com.mopicmp.mosaicary.client.skins.SkinCard;
import com.mopicmp.mosaicary.client.ui.MenuSkin;
import com.mopicmp.mosaicary.client.ui.PauseMenuButton;
import com.mopicmp.mosaicary.client.ui.ScreenWidgets;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * A development-only run that photographs the catalogue and quits.
 *
 * Switched on by the system property {@code mosaicary.shots}, the folder to write
 * into (the {@code runShots} Gradle task sets it). It waits for the title screen,
 * opens the catalogue, waits for the list and every card picture, and takes
 * {@code grid-<scale>.png}. Then it opens each model in turn, waits for it, and takes
 * {@code detail-<id>-<scale>.png}, writing the site's own card picture for the same
 * model beside it as {@code site-<id>.png} so the two can be compared. With
 * {@code mosaicary.shotsScales} (such as {@code 2,4}) the whole round is repeated at
 * each GUI scale. Then it quits.
 *
 * That is the model catalogue's round, hidden in this release with the catalogue, and run only
 * with {@code mosaicary.shotsModels}. Otherwise it photographs the skin wardrobe, which also
 * serves loaders and game versions without Fabric's client game tests: the title screen with the player's
 * figure beside it as {@code title-<scale>.png}, then the wardrobe opened from it once its
 * first shelf of cards has come, as {@code wardrobe-<scale>.png}; the first card opened
 * ({@code wardrobe-card-<scale>.png}), the My skins tab ({@code wardrobe-mine-<scale>.png}) and the
 * settings as the loader's list of mods opens them ({@code settings-<scale>.png}).
 *
 * With {@code mosaicary.shotsServer} ({@code host:port}) it goes through the wardrobe on a server
 * instead, writing each step to the log as {@code Server run: ...}: it joins, waits for the
 * server's greeting, presses the wardrobe's key, opens the pause menu ({@code pause.png}) and
 * presses the wardrobe's button there, keeps the first skin of the shelf in My skins
 * ({@code wardrobe-server.png}) and waits long enough for the wardrobe to have been sent to
 * the server, where it is kept in the world's {@code mosaicary/wardrobes} folder.
 *
 * A screenshot is taken only after what it shows has been ready for a number of
 * ticks in a row: the picture read back is the frame drawn before this tick, so a
 * texture uploaded in the same tick would not be in it yet. The mouse is hidden from
 * the screen meanwhile, so no tooltip covers what is being photographed. Each wait
 * has a time limit, so a site that does not answer still ends the run with a picture
 * of whatever the screen showed.
 */
final class Shots {

	private enum Step { WAIT_TITLE, SHOOT_TITLE, WAIT_WARDROBE, CARD, SHOOT_CARD, SHOOT_MINE, SHOOT_SETTINGS, JOIN, WAIT_JOIN, WAIT_GREETING, WAIT_KEY, PAUSE,
		WAIT_SERVER_WARDROBE, WAIT_SENT, SCALE, WAIT_LIST, SHOOT_GRID, NEXT, OPEN_MODEL, WAIT_MODEL, SHOOT_MODEL, NEXT_FRAME, SHOOT_FRAME, QUIT, DONE }

	private static final int SETTLE_TICKS = 20;
	private static final int LIMIT_TICKS = 20 * 120;

	private final Path folder;
	private final MosaicaryClient client;
	private final List<Integer> scales = new ArrayList<>();
	private Step step = Step.WAIT_TITLE;
	private int ticks;
	private int waited;
	private int stable;
	private int scale;
	private long started;
	private CatalogueScreen screen;
	private SkinsScreen wardrobe;
	private SkinCard opened;
	private int paintedBefore;
	private final List<ModelCard> queue = new ArrayList<>();
	private ModelCard current;
	private final List<String> frames = new ArrayList<>();
	private String frame = "";
	private boolean writing;

	private Shots(Path folder, MosaicaryClient client) {
		this.folder = folder;
		this.client = client;
		String asked = System.getProperty("mosaicary.shotsScales", "0");
		for (String one : asked.split(",")) {
			try {
				scales.add(Integer.parseInt(one.trim()));
			} catch (NumberFormatException ignored) {
				// A malformed entry is skipped.
			}
		}
		if (scales.isEmpty()) scales.add(0);
	}

	static void start(Path folder, MosaicaryClient client) {
		Shots shots = new Shots(folder, client);
		Mosaicary.LOGGER.info("Screenshot run: pictures go to {}", folder.toAbsolutePath());
		CatalogueScreen.hideMouse = true;
		ClientPlatform.onTick(shots::tick);
	}

	private void tick(Minecraft minecraft) {
		if (step == Step.DONE || writing) return;
		ticks++;
		waited++;
		switch (step) {
			case WAIT_TITLE -> {
				//#if NEOFORGE>=1
				//$$ // NeoForge lists warnings about mods (other mods' too) on a screen of its own before the menu;
				//$$ // they are in the log, and the run goes on as the screen's Proceed button would.
				//$$ if (minecraft.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen) {
				//$$ 	Mosaicary.LOGGER.info("Screenshot run: NeoForge showed its loading warnings; going on to the title screen");
				//$$ 	minecraft.gui.setScreen(new TitleScreen());
				//$$ 	return;
				//$$ }
				//#endif
				// A game folder used for the first time opens on the accessibility questions; they are taken as answered.
				if (minecraft.gui.screen() instanceof AccessibilityOnboardingScreen) {
					minecraft.options.onboardingAccessibilityFinished();
					minecraft.gui.setScreen(new TitleScreen());
					return;
				}
				if (minecraft.gui.screen() instanceof TitleScreen && minecraft.gui.overlay() == null && ticks > 40) {
					started = System.currentTimeMillis();
					if (System.getProperty("mosaicary.shotsServer") != null) {
						go(Step.JOIN);
						return;
					}
					if (!Boolean.getBoolean("mosaicary.shotsModels")) {
						// The first of shotsScales, when given, for the whole round; shotsSearch narrows the shelf,
						// whose first card is the one opened when none has eyes.
						scale = scales.get(0);
						if (scale != 0) {
							minecraft.options.guiScale().set(scale);
							minecraft.resizeGui();
						}
						String search = System.getProperty("mosaicary.shotsSearch");
						if (search != null && !search.isBlank()) SkinsScreen.query(SkinsScreen.query().withWords(search));
						go(Step.SHOOT_TITLE);
						return;
					}
					screen = client.open(minecraft.gui.screen());
					Mosaicary.LOGGER.info("Screenshot run: catalogue opened");
					go(Step.SCALE);
				}
			}
			case SHOOT_TITLE -> {
				// The figure fades in with the menu and waits for the account's skin.
				if (MenuSkin.on(minecraft.gui.screen()) != null && !client.skins().askingOwn()) stable++;
				else stable = 0;
				if (stable > 3 * SETTLE_TICKS || waited > LIMIT_TICKS) {
					shoot(minecraft, "title-" + label() + ".png");
					wardrobe = client.openWardrobe(minecraft.gui.screen());
					go(Step.WAIT_WARDROBE);
				}
			}
			case WAIT_WARDROBE -> {
				// The cards' pictures arrive after the shelf; a few seconds more lets them come.
				if (!client.skins().shelf(SkinsScreen.query()).cards.isEmpty()) stable++;
				else stable = 0;
				if (stable > 5 * SETTLE_TICKS || waited > LIMIT_TICKS) {
					Mosaicary.LOGGER.info("Screenshot run: wardrobe after {} ms", System.currentTimeMillis() - started);
					shoot(minecraft, "wardrobe-" + label() + ".png");
					go(Step.CARD);
				}
			}
			case CARD -> {
				List<SkinCard> cards = client.skins().shelf(SkinsScreen.query()).cards;
				// A skin with eyes for Fresh Moves when the shelf has one, to see them move.
				opened = cards.stream().filter(SkinCard::eyes).findFirst().orElse(cards.isEmpty() ? null : cards.get(0));
				if (opened != null) wardrobe.openCard(opened);
				go(Step.SHOOT_CARD);
			}
			case SHOOT_CARD -> {
				// The figure's texture and the eyes' check come after the card opens.
				// Facing the viewer, then ten seconds, as the game test counts them: the eyes are painted
				// anew every few seconds, a blink or a glance.
				if (waited == SETTLE_TICKS) {
					wardrobe.setView(-5, 0);
					paintedBefore = wardrobe.eyesPainted();
				}
				if (waited > 11 * SETTLE_TICKS) {
					if (opened != null) {
						Mosaicary.LOGGER.info("Screenshot run: card {} with eyes {}; Fresh Moves ready {}; eyes painted {} times in ten seconds",
							opened.id(), opened.eyes(), FreshMoves.status().ready(), wardrobe.eyesPainted() - paintedBefore);
					}
					shoot(minecraft, "wardrobe-card-" + label() + ".png");
					wardrobe.closeDetail();
					wardrobe.showTab(SkinsScreen.Tab.WARDROBE);
					go(Step.SHOOT_MINE);
				}
			}
			case SHOOT_MINE -> {
				if (waited > 2 * SETTLE_TICKS) {
					shoot(minecraft, "wardrobe-mine-" + label() + ".png");
					openSettings(minecraft);
					go(Step.SHOOT_SETTINGS);
				}
			}
			case SHOOT_SETTINGS -> {
				if (waited > SETTLE_TICKS) {
					shoot(minecraft, "settings-" + label() + ".png");
					go(Step.QUIT);
				}
			}
			case JOIN -> {
				String address = System.getProperty("mosaicary.shotsServer");
				boolean ring = false;
				for (KeyMapping key : minecraft.options.keyMappings) ring |= key.getName().equals("key.mosaicary.ring");
				serverLog("the model ring's key is " + (ring ? "registered" : "not registered"));
				ConnectScreen.startConnecting(minecraft.gui.screen(), minecraft, ServerAddress.parseString(address),
					new ServerData("Mosaicary test", address, ServerData.Type.OTHER), false, null);
				go(Step.WAIT_JOIN);
			}
			case WAIT_JOIN -> {
				if (minecraft.player != null && minecraft.gui.screen() == null) stable++;
				else stable = 0;
				if (stable > 2 * SETTLE_TICKS) {
					serverLog("joined as " + minecraft.getUser().getName());
					go(Step.WAIT_GREETING);
				} else if (waited > LIMIT_TICKS) {
					serverLog("could not join, the screen is " + minecraft.gui.screen());
					go(Step.QUIT);
				}
			}
			case WAIT_GREETING -> {
				boolean greeted = client.wardrobeSync().serverKeeps();
				if (greeted || waited > 10 * SETTLE_TICKS) {
					serverLog(greeted ? "the server greeted the client: it keeps wardrobes" : "no greeting from the server");
					KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_M));
					go(Step.WAIT_KEY);
				}
			}
			case WAIT_KEY -> {
				boolean open = minecraft.gui.screen() instanceof SkinsScreen;
				if (open || waited > SETTLE_TICKS) {
					serverLog(open ? "M opened the wardrobe" : "M opened " + minecraft.gui.screen());
					minecraft.gui.setScreen(null);
					minecraft.pauseGame(false);
					go(Step.PAUSE);
				}
			}
			case PAUSE -> {
				if (minecraft.gui.screen() instanceof PauseScreen) stable++;
				if (stable > SETTLE_TICKS) {
					shoot(minecraft, "pause.png");
					PauseMenuButton button = null;
					for (AbstractWidget widget : ScreenWidgets.of(minecraft.gui.screen())) {
						if (widget instanceof PauseMenuButton found) button = found;
					}
					serverLog(button == null ? "no wardrobe button on the pause menu" : "the pause menu has the wardrobe button");
					if (button != null) button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
					go(Step.WAIT_SERVER_WARDROBE);
				} else if (waited > LIMIT_TICKS) {
					serverLog("the pause menu did not open");
					go(Step.QUIT);
				}
			}
			case WAIT_SERVER_WARDROBE -> {
				List<SkinCard> cards = client.skins().shelf(SkinsScreen.query()).cards;
				if (minecraft.gui.screen() instanceof SkinsScreen && !cards.isEmpty()) stable++;
				else stable = 0;
				if (stable > 5 * SETTLE_TICKS) {
					serverLog("the pause menu's button opened the wardrobe");
					SkinCard card = cards.get(0);
					client.skins().wardrobe().add(card.entry(System.currentTimeMillis()));
					serverLog("kept site:" + card.id() + " in My skins");
					shoot(minecraft, "wardrobe-server.png");
					go(Step.WAIT_SENT);
				} else if (waited > LIMIT_TICKS) {
					serverLog("the wardrobe did not open from the pause menu: " + minecraft.gui.screen());
					go(Step.QUIT);
				}
			}
			case WAIT_SENT -> {
				// A change goes to the server three seconds after it is made.
				if (waited > 10 * SETTLE_TICKS) {
					serverLog("done");
					go(Step.QUIT);
				}
			}
			case SCALE -> {
				scale = scales.remove(0);
				minecraft.options.guiScale().set(scale);
				minecraft.resizeGui();
				if (minecraft.gui.screen() != screen) minecraft.gui.setScreen(screen);
				screen.closeModel();
				go(Step.WAIT_LIST);
			}
			case WAIT_LIST -> {
				Catalogue catalogue = client.catalogue();
				boolean listed = catalogue.listState() == Catalogue.State.READY || catalogue.listState() == Catalogue.State.FAILED;
				if (listed && catalogue.picturesSettled(screen.shown())) stable++;
				else stable = 0;
				if (stable > SETTLE_TICKS || waited > LIMIT_TICKS) {
					Mosaicary.LOGGER.info("Screenshot run: list {} after {} ms", catalogue.listState(),
						System.currentTimeMillis() - started);
					go(Step.SHOOT_GRID);
				}
			}
			case SHOOT_GRID -> {
				shoot(minecraft, "grid-" + label() + ".png");
				queue.addAll(screen.shown());
				// The model with the most parts first: it is the one most likely to show a fault.
				queue.sort(Comparator.comparingInt((ModelCard card) -> card.cubes() + card.meshes()).reversed());
				String only = System.getProperty("mosaicary.shotsModel");
				if (only != null && !only.isBlank()) queue.removeIf(card -> !card.id().equals(only));
				go(Step.NEXT);
			}
			case NEXT -> {
				if (!queue.isEmpty()) {
					current = queue.remove(0);
					go(Step.OPEN_MODEL);
				} else if (!scales.isEmpty()) {
					go(Step.SCALE);
				} else {
					go(Step.QUIT);
				}
			}
			case OPEN_MODEL -> {
				screen.openModel(current);
				writeSitePicture(current);
				go(Step.WAIT_MODEL);
			}
			case WAIT_MODEL -> {
				Catalogue.Model model = client.catalogue().model(current);
				if (model.state() != Catalogue.State.LOADING) stable++;
				else stable = 0;
				if (stable > SETTLE_TICKS || waited > LIMIT_TICKS) go(Step.SHOOT_MODEL);
			}
			case SHOOT_MODEL -> {
				shoot(minecraft, "detail-" + current.id() + "-" + label() + ".png");
				frames.clear();
				String asked = System.getProperty("mosaicary.shotsFrames", "");
				for (String one : asked.split(",")) if (!one.isBlank()) frames.add(one.trim());
				go(Step.NEXT_FRAME);
			}
			case NEXT_FRAME -> {
				// Each frame is name@seconds; a model without that animation skips it.
				while (!frames.isEmpty()) {
					String one = frames.remove(0);
					int at = one.lastIndexOf('@');
					String name = at > 0 ? one.substring(0, at) : one;
					double seconds = 0;
					try {
						seconds = at > 0 ? Double.parseDouble(one.substring(at + 1)) : 0;
					} catch (NumberFormatException ignored) {
						// Taken as the start.
					}
					if (screen.showFrame(name, seconds)) {
						// "blockbench" turns the view to Blockbench's opening angle, to compare
						// with a screenshot of the editor; otherwise the card's angle is kept.
						if ("blockbench".equals(System.getProperty("mosaicary.shotsView"))) {
							screen.setView((float) (-3 * Math.PI / 4), 0.515f);
						}
						frame = name.replaceAll("[^A-Za-z0-9_-]", "_") + "-" + seconds;
						go(Step.SHOOT_FRAME);
						return;
					}
				}
				go(Step.NEXT);
			}
			case SHOOT_FRAME -> {
				if (waited > SETTLE_TICKS) {
					shoot(minecraft, "frame-" + current.id() + "-" + frame + "-" + label() + ".png");
					go(Step.NEXT_FRAME);
				}
			}
			case QUIT -> {
				Mosaicary.LOGGER.info("Screenshot run finished");
				step = Step.DONE;
				minecraft.stop();
			}
			default -> { }
		}
	}

	/** The wardrobe's settings, opened the way the loader's list of mods opens them. */
	private static void openSettings(Minecraft minecraft) {
		Screen parent = minecraft.gui.screen();
		//#if NEOFORGE>=1
		//$$ net.neoforged.fml.ModContainer mod = net.neoforged.fml.ModList.get().getModContainerById(Mosaicary.ID).orElseThrow();
		//$$ var factory = net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(mod.getModInfo());
		//$$ Mosaicary.LOGGER.info("Screenshot run: the mod list's Config button {}", factory.isPresent() ? "is there" : "is missing");
		//$$ if (factory.isPresent()) minecraft.gui.setScreen(factory.get().createScreen(mod, parent));
		//#else
		// Mod Menu, when it is there, opens the same screen (client/compat/ModMenuEntry).
		minecraft.gui.setScreen(new com.mopicmp.mosaicary.client.ui.SettingsScreen(parent));
		//#endif
	}

	private static void serverLog(String line) {
		Mosaicary.LOGGER.info("Server run: {}", line);
	}

	private String label() {
		return scale == 0 ? "auto" : "scale" + scale;
	}

	private void go(Step next) {
		step = next;
		waited = 0;
		stable = 0;
	}

	private void shoot(Minecraft minecraft, String name) {
		Path target = folder.resolve(name);
		writing = true;
		try {
			Files.createDirectories(folder);
		} catch (Exception failed) {
			Mosaicary.LOGGER.warn("Cannot create {}: {}", folder, failed.toString());
		}
		Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
			try (image) {
				image.writeToFile(target);
				Mosaicary.LOGGER.info("Screenshot written: {} ({} x {}, GUI scale {})", target.toAbsolutePath(),
					image.getWidth(), image.getHeight(), minecraft.getWindow().getGuiScale());
			} catch (Exception failed) {
				Mosaicary.LOGGER.warn("Screenshot {} failed: {}", target, failed.toString());
			} finally {
				minecraft.execute(() -> writing = false);
			}
		});
	}

	/** The site's card picture for the model, as PNG, for comparing with the 3D preview. */
	private void writeSitePicture(ModelCard card) {
		client.catalogue().site().preview(card).thenAccept(bytes -> {
			NativeImage image = Pictures.decode(bytes);
			if (image == null) return;
			try (image) {
				Files.createDirectories(folder);
				image.writeToFile(folder.resolve("site-" + card.id() + ".png"));
			} catch (Exception failed) {
				Mosaicary.LOGGER.warn("Could not write the site picture: {}", failed.toString());
			}
		});
	}
}
