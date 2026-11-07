package com.mopicmp.mosaicary.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.compat.GenderMod;
import com.mopicmp.mosaicary.client.compat.SkinLayers3d;
import com.mopicmp.mosaicary.client.platform.ClientPlatform;
import com.mopicmp.mosaicary.client.render.Pictures;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.skins.FreshMoves;
import com.mopicmp.mosaicary.client.skins.SkinCard;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.ui.MenuSkin;
import com.mopicmp.mosaicary.client.ui.PauseMenuButton;
import com.mopicmp.mosaicary.client.ui.ScreenWidgets;
import com.mopicmp.mosaicary.client.ui.IconButton;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;
import com.mopicmp.mosaicary.client.ui.Theme;
//#if MC>=12111
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
//#endif

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
//#if MC>=12000
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
//#endif
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
//#if MC>=12111
import net.minecraft.client.input.KeyEvent;
//#endif
import net.minecraft.client.multiplayer.ServerData;
//#if MC>=11700
import net.minecraft.client.multiplayer.resolver.ServerAddress;
//#endif
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

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
 * settings as the loader's list of mods opens them ({@code settings-<scale>.png}). With
 * {@code mosaicary.shotsTurns} (such as {@code 30,90,150}) the opened card's figure is also taken
 * turned by each of those degrees ({@code wardrobe-card-<scale>-turn<degrees>.png}): its lighting
 * changes as it turns. A tilt can follow a turn after an {@code @} ({@code 0@-50}, the wardrobe's
 * own is -5), and the picture is then {@code ...-turn<degrees>-tilt<degrees>.png}.
 *
 * With {@code mosaicary.shotsServer} ({@code host:port}) it goes through the wardrobe on a server
 * instead, writing each step to the log as {@code Server run: ...}: it joins, waits for the
 * server's greeting, presses the wardrobe's key, opens the pause menu ({@code pause.png}) and
 * presses the wardrobe's button there, keeps the first skin of the shelf in My skins
 * ({@code wardrobe-server.png}) and waits long enough for the wardrobe to have been sent to
 * the server, where it is kept in the world's {@code mosaicary/wardrobes} folder.
 *
 * With {@code mosaicary.shotsGender} it makes a new world through the game's own buttons, puts
 * a grey skin with a checkered jacket front on the player and takes it close up from the front,
 * with Female Gender Mod's female body and then a flat one, straight on and with the body turned
 * ({@code gender-*.png}), as the client game test does from 1.21.4 on: for Female Gender Mod
 * and 3D Skin Layers together (client/compat/GenderBreasts) in the versions before it, with both
 * given in {@code -PtestMods}. From 1.18.2 on, the first version Female Gender Mod is joined in.
 *
 * A screenshot is taken only after what it shows has been ready for a number of
 * ticks in a row: the picture read back is the frame drawn before this tick, so a
 * texture uploaded in the same tick would not be in it yet. The mouse is hidden from
 * the screen meanwhile, so no tooltip covers what is being photographed. Each wait
 * has a time limit, so a site that does not answer still ends the run with a picture
 * of whatever the screen showed.
 */
final class Shots {

	private enum Step { WAIT_TITLE, SHOOT_TITLE, WAIT_WARDROBE, CARD, SHOOT_CARD, SHOOT_TURN, SHOOT_MINE, SHOOT_SETTINGS, JOIN, WAIT_JOIN, WAIT_GREETING, WAIT_KEY, PAUSE,
		WAIT_SERVER_WARDROBE, WAIT_SENT, GENDER_WORLD, GENDER_WAIT, GENDER_ROUND, SCALE, WAIT_LIST, SHOOT_GRID, NEXT, OPEN_MODEL, WAIT_MODEL, SHOOT_MODEL, NEXT_FRAME, SHOOT_FRAME, QUIT, DONE }

	private static final int SETTLE_TICKS = 20;
	private static final int LIMIT_TICKS = 20 * 120;

	private final Path folder;
	private final MosaicaryClient client;
	private final List<Integer> scales = new ArrayList<>();
	private final List<int[]> turns = new ArrayList<>();
	private Step step = Step.WAIT_TITLE;
	private int ticks;
	private int waited;
	private int stable;
	private int scale;
	private long started;
	//#if MC>=12111
	private CatalogueScreen screen;
	//#endif
	private SkinsScreen wardrobe;
	private SkinCard opened;
	private int paintedBefore;
	private final List<ModelCard> queue = new ArrayList<>();
	private ModelCard current;
	private final List<String> frames = new ArrayList<>();
	private String frame = "";
	private boolean writing;
	/** The gender round: the screen last seen and the one whose button was pressed, and what is left to do. */
	private Screen seen;
	private Screen pressedOn;
	private boolean flat;
	private final List<Later> later = new ArrayList<>();
	/** How far the body is held turned from the head in each tick, past what the game lets it stay at; or 0. */
	private int turned;

	/** One thing the gender round does, the given number of ticks after the one before. */
	private record Later(int ticks, Consumer<Minecraft> action) { }

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
		for (String one : System.getProperty("mosaicary.shotsTurns", "").split(",")) {
			String[] parts = one.trim().split("@");
			try {
				// The turn, the tilt, and whether the tilt was asked for (the picture's name says so).
				turns.add(new int[] { Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : -5, parts.length - 1 });
			} catch (NumberFormatException ignored) {
				// A malformed or empty entry is skipped.
			}
		}
	}

	static void start(Path folder, MosaicaryClient client) {
		Shots shots = new Shots(folder, client);
		Mosaicary.LOGGER.info("Screenshot run: pictures go to {}", folder.toAbsolutePath());
		Theme.hideMouse = true;
		ClientPlatform.onTick(shots::tick);
	}

	private void tick(Minecraft minecraft) {
		if (step == Step.DONE || writing) return;
		ticks++;
		waited++;
		switch (step) {
			case WAIT_TITLE -> {
				// NeoForge lists warnings about mods (other mods' too) on a screen of its own before the menu;
				// they are in the log, and the run goes on as the screen's Proceed button would. The game's
				// own calls stay outside the loader's branch, where the preprocessor converts them.
				if (loadingWarnings(minecraft.gui.screen())) {
					Mosaicary.LOGGER.info("Screenshot run: NeoForge showed its loading warnings; going on to the title screen");
					minecraft.gui.setScreen(new TitleScreen());
					return;
				}
				// A game folder used for the first time opens on the accessibility questions; they are taken as
				// answered. Before 1.20 there are none.
				//#if MC>=12000
				if (minecraft.gui.screen() instanceof AccessibilityOnboardingScreen) {
					//#if MC>=12002
					minecraft.options.onboardingAccessibilityFinished();
					//#else
					//$$ minecraft.options.onboardAccessibility = false;
					//$$ minecraft.options.save();
					//#endif
					minecraft.gui.setScreen(new TitleScreen());
					return;
				}
				//#endif
				if (minecraft.gui.screen() instanceof TitleScreen && minecraft.gui.overlay() == null && ticks > 40) {
					started = System.currentTimeMillis();
					//#if MC>=11802
					if (Boolean.getBoolean("mosaicary.shotsGender")) {
						genderLog("its window " + (GenderMod.windowInMenus() ? "opens" : "does not open") + " in the menus");
						minecraft.gui.setScreen(new SelectWorldScreen(minecraft.gui.screen()));
						go(Step.GENDER_WORLD);
						return;
					}
					//#endif
					if (System.getProperty("mosaicary.shotsServer") != null) {
						go(Step.JOIN);
						return;
					}
					if (!Boolean.getBoolean("mosaicary.shotsModels")) {
						// The first of shotsScales, when given, for the whole round; shotsSearch narrows the shelf,
						// whose first card is the one opened when none has eyes.
						scale = scales.get(0);
						if (scale != 0) {
							// An option is an object of its own from 1.19 on, and before a field.
							//#if MC>=11900
							minecraft.options.guiScale().set(scale);
							//#else
							//$$ minecraft.options.guiScale = scale;
							//#endif
							minecraft.resizeGui();
						}
						String search = System.getProperty("mosaicary.shotsSearch");
						if (search != null && !search.isBlank()) SkinsScreen.query(SkinsScreen.query().withWords(search));
						go(Step.SHOOT_TITLE);
						return;
					}
					//#if MC>=12111
					screen = client.open(minecraft.gui.screen());
					Mosaicary.LOGGER.info("Screenshot run: catalogue opened");
					go(Step.SCALE);
					//#else
					//$$ Mosaicary.LOGGER.info("Screenshot run: no model catalogue in this game version");
					//$$ go(Step.QUIT);
					//#endif
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
						Mosaicary.LOGGER.info("Screenshot run: card {} with eyes {}; Fresh Moves ready {}; eyes painted {} times in ten seconds; 3D Skin Layers on {} of 6 outer parts",
							opened.id(), opened.eyes(), FreshMoves.status().ready(), wardrobe.eyesPainted() - paintedBefore, SkinLayers3d.hung());
					}
					shoot(minecraft, "wardrobe-card-" + label() + ".png");
					if (turns.isEmpty()) leaveCard();
					go(turns.isEmpty() ? Step.SHOOT_MINE : Step.SHOOT_TURN);
				}
			}
			case SHOOT_TURN -> {
				int[] turn = turns.get(0);
				if (waited == 1) wardrobe.setView(turn[1], turn[0]);
				if (waited > SETTLE_TICKS) {
					turns.remove(0);
					shoot(minecraft, "wardrobe-card-" + label() + "-turn" + turn[0] + (turn[2] > 0 ? "-tilt" + turn[1] : "") + ".png");
					if (turns.isEmpty()) leaveCard();
					go(turns.isEmpty() ? Step.SHOOT_MINE : Step.SHOOT_TURN);
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
			//#if MC>=11802
			case GENDER_WORLD -> {
				// Through the world list's button and then the new world screen's, each once it has stood a moment.
				Screen open = minecraft.gui.screen();
				if (open != seen) {
					seen = open;
					stable = 0;
				}
				if (minecraft.player != null && open == null) {
					genderLog("in the new world");
					go(Step.GENDER_WAIT);
				} else if (open != null && open != pressedOn && ++stable > SETTLE_TICKS) {
					// A superflat world, the same around the player wherever it starts and quick to make: on the
					// new world screen its type is set first, behind a tab from 1.19.3 on and before behind more options.
					AbstractWidget type = button(open, "selectWorld.mapType", false);
					AbstractWidget more = button(open, "createWorld.tab.world.title", true);
					if (more == null) more = button(open, "selectWorld.moreWorldOptions", true);
					AbstractWidget create = button(open, "selectWorld.create", true);
					if (!flat && type != null) {
						// Default, then Superflat.
						press(open, type);
						flat = true;
						stable = 0;
						genderLog("the world type is " + type.getMessage().getString());
					} else if (!flat && more != null && open.getClass() != SelectWorldScreen.class) {
						press(open, more);
						stable = 0;
					} else if (create != null) {
						pressedOn = open;
						genderLog("pressed " + create.getMessage().getString() + " on " + open.getClass().getSimpleName());
						press(open, create);
					}
				}
				if (waited > 3 * LIMIT_TICKS) {
					genderLog("no world, the screen is " + open);
					go(Step.QUIT);
				}
			}
			case GENDER_WAIT -> {
				// Long enough for the chunks around to be there.
				if (waited > 5 * SETTLE_TICKS) {
					planGenderRound(minecraft);
					go(Step.GENDER_ROUND);
				}
			}
			case GENDER_ROUND -> {
				if (turned != 0) turnBody(minecraft, turned);
				if (later.isEmpty()) {
					genderLog("done");
					go(Step.QUIT);
				} else if (waited >= later.get(0).ticks()) {
					later.remove(0).action().accept(minecraft);
					waited = 0;
				}
			}
			//#endif
			case JOIN -> {
				String address = System.getProperty("mosaicary.shotsServer");
				boolean ring = false;
				for (KeyMapping key : minecraft.options.keyMappings) ring |= key.getName().equals("key.mosaicary.ring");
				serverLog("the model ring's key is " + (ring ? "registered" : "not registered"));
				//#if MC>=12002
				ConnectScreen.startConnecting(minecraft.gui.screen(), minecraft, ServerAddress.parseString(address),
					new ServerData("Mosaicary test", address, ServerData.Type.OTHER), false, null);
				//#elseif MC>=12000
				//$$ ConnectScreen.startConnecting(minecraft.screen, minecraft, ServerAddress.parseString(address),
				//$$ 	new ServerData("Mosaicary test", address, false), false);
				//#elseif MC>=11700
				//$$ ConnectScreen.startConnecting(minecraft.screen, minecraft, ServerAddress.parseString(address),
				//$$ 	new ServerData("Mosaicary test", address, false));
				//#else
				//$$ minecraft.setScreen(new ConnectScreen(minecraft.screen, minecraft, new ServerData("Mosaicary test", address, false)));
				//#endif
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
					serverLog(button == null ? "no wardrobe button on the pause menu" : "the pause menu has the wardrobe button at "
						+ button.getX() + ", " + button.getY() + (button.visible ? "" : ", hidden") + " on a screen of "
						+ minecraft.gui.screen().width + " x " + minecraft.gui.screen().height);
					//#if MC>=12111
					if (button != null) button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
					//#else
					//$$ if (button != null) button.onPress();
					//#endif
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
			//#if MC>=12111
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
			//#endif
			case QUIT -> {
				Mosaicary.LOGGER.info("Screenshot run finished");
				step = Step.DONE;
				minecraft.stop();
			}
			default -> { }
		}
	}

	private static boolean loadingWarnings(Screen screen) {
		//#if NEOFORGE>=1
		//$$ return screen instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen;
		//#else
		return false;
		//#endif
	}

	/** The wardrobe's settings, opened the way the loader's list of mods opens them. */
	private static void openSettings(Minecraft minecraft) {
		Screen settings = settingsFromModList(minecraft.gui.screen());
		if (settings != null) minecraft.gui.setScreen(settings);
	}

	private static Screen settingsFromModList(Screen parent) {
		//#if NEOFORGE>=1
		//$$ net.neoforged.fml.ModContainer mod = net.neoforged.fml.ModList.get().getModContainerById(Mosaicary.ID).orElseThrow();
		//$$ var factory = net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(mod.getModInfo());
		//$$ Mosaicary.LOGGER.info("Screenshot run: the mod list's Config button {}", factory.isPresent() ? "is there" : "is missing");
		//$$ return factory.isPresent() ? factory.get().createScreen(mod, parent) : null;
		//#else
		// Mod Menu, when it is there, opens the same screen (client/compat/ModMenuEntry).
		return new com.mopicmp.mosaicary.client.ui.SettingsScreen(parent);
		//#endif
	}

	//#if MC>=11802
	/** The gender round's shots, as the client game test SkinLayersGenderGameTest takes them. */
	private void planGenderRound(Minecraft minecraft) {
		later.add(new Later(0, on -> {
			on.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			// An option is an object of its own from 1.19 on, and before a field.
			//#if MC>=11900
			on.options.fov().set(30);
			//#else
			//$$ on.options.fov = 30;
			//#endif
			hideGui(on, true);
			on.player.setXRot(0);
			// The outer Minecraft: the preprocessor's patterns do not see the type of a lambda's own parameter.
			client.skins().showFetched(minecraft.getUser().getProfileId(), "checkered", checkered());
			genderLog("the checkered skin is on, " + (SkinLayers3d.available() ? "with" : "without") + " 3D Skin Layers");
		}));
		// Each shot some ticks after what it shows: drawn by the processor alone, the game may draw a
		// frame only every few ticks, and a picture is the frame drawn last.
		for (String gender : new String[] { "female", "male" }) {
			later.add(new Later(40, on -> putBody(on, gender)));
			later.add(new Later(20, on -> {
				if (gender.equals("female")) logGenderJoin(minecraft);
				shoot(on, "gender-" + gender + "-front.png");
			}));
			later.add(new Later(1, on -> turned = 45));
			later.add(new Later(10, on -> shoot(on, "gender-" + gender + "-turned.png")));
			later.add(new Later(1, on -> {
				turned = 0;
				turnBody(on, 0);
			}));
		}
		// In profile and turned, where the jacket behind the breasts shows; the body held there in each
		// tick, as the game turns it back to 50 degrees from the head.
		later.add(new Later(1, on -> putBody(on, "female")));
		for (int turn : new int[] { 45, 90 }) {
			later.add(new Later(turn == 45 ? 20 : 1, on -> turned = turn));
			later.add(new Later(10, on -> shoot(on, "gender-female-turned-" + turn + ".png")));
		}
		later.add(new Later(1, on -> {
			turned = 0;
			turnBody(on, 0);
			hideGui(on, false);
		}));
		// Female Gender Mod's body, set in a skin's card through the button beside the catalogue's
		// girls; and from there the mod's own window.
		later.add(new Later(10, Shots::openGirls));
		later.add(new Later(60, Shots::pressGender));
		later.add(new Later(40, on -> shootBody(on)));
		later.add(new Later(1, Shots::pressGenderWindow));
		later.add(new Later(20, on -> shootGenderWindow(on)));
		later.add(new Later(1, Shots::closeGenderWindow));
		// The breasts on the preview of a girl of the catalogue, by the body on now.
		later.add(new Later(10, Shots::openGirls));
		later.add(new Later(60, Shots::openFirstGirl));
		later.add(new Later(40, on -> previewTurned(on, 45)));
		later.add(new Later(10, on -> shoot(on, "gender-preview-45.png")));
		later.add(new Later(1, on -> previewTurned(on, 90)));
		later.add(new Later(10, on -> shoot(on, "gender-preview-90.png")));
		later.add(new Later(1, Shots::closeGenderWindow));
	}

	private static void openFirstGirl(Minecraft minecraft) {
		Screen open = minecraft.gui.screen();
		Skins.Shelf shelf = MosaicaryClient.instance().skins().shelf(SkinsScreen.query());
		if (open instanceof SkinsScreen screen && !shelf.cards.isEmpty()) {
			screen.openCard(shelf.cards.get(0));
			genderLog("the preview of " + shelf.cards.get(0).name());
		} else {
			genderLog("no girl of the catalogue to preview");
		}
	}

	private static void previewTurned(Minecraft minecraft, float degrees) {
		if (minecraft.gui.screen() instanceof SkinsScreen screen) screen.setView(-8, degrees);
	}

	private static void openGirls(Minecraft minecraft) {
		SkinsScreen.query(SkinsScreen.query().withCategory("girls"));
		minecraft.gui.setScreen(new SkinsScreen(null, MosaicaryClient.instance().skins()));
	}

	private static void pressGender(Minecraft minecraft) {
		Screen open = minecraft.gui.screen();
		IconButton button = open instanceof SkinsScreen screen ? screen.genderButton() : null;
		genderLog("the button to the body's panel " + (button == null ? "is not there" : button.active ? "is live" : "is greyed out"));
		if (button != null) button.press();
	}

	private void shootBody(Minecraft minecraft) {
		Screen open = minecraft.gui.screen();
		genderLog("the body's panel " + (open instanceof SkinsScreen screen && screen.bodyOpen() ? "is open" : "is not open"));
		shoot(minecraft, "gender-body.png");
	}

	/** The panel's button to the mod's own window, found by its name as a player finds it. */
	private static void pressGenderWindow(Minecraft minecraft) {
		Screen open = minecraft.gui.screen();
		String name = I18n.get("mosaicary.body.window");
		AbstractWidget button = null;
		if (open instanceof SkinsScreen) {
			for (Object child : open.children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(name)) button = widget;
			}
		}
		genderLog("the panel's button to its window " + (button == null ? "is not there" : button.active ? "is live" : "is greyed out"));
		if (button != null) press(open, button);
	}

	private void shootGenderWindow(Minecraft minecraft) {
		Screen open = minecraft.gui.screen();
		genderLog("its window: " + (open == null ? "none" : open.getClass().getName()));
		shoot(minecraft, "gender-window.png");
	}

	private static void closeGenderWindow(Minecraft minecraft) {
		// Through its own close button where it has one (up to 1.21.1 a small X, which goes to the
		// screen it was opened from), else as Escape does.
		Screen open = minecraft.gui.screen();
		AbstractWidget cross = null;
		if (open != null) {
			for (Object child : open.children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals("X")) cross = widget;
			}
		}
		if (cross != null) press(open, cross);
		else if (open != null && !(open instanceof SkinsScreen)) open.onClose();
		Screen back = minecraft.gui.screen();
		genderLog("closed " + (cross != null ? "with its X" : "as with Escape") + " back to " + (back == null ? "the world" : back.getClass().getSimpleName()));
		minecraft.gui.setScreen(null);
		SkinsScreen.query(SkinsScreen.query().withCategory("any"));
	}

	/** What joining in Female Gender Mod's drawing has to go on, for the log. */
	private static void logGenderJoin(Minecraft minecraft) {
		boolean joined = false;
		for (String layer : new String[] { "com.wildfire.client.render.GenderLayer", "com.wildfire.render.GenderLayer" }) {
			try {
				for (java.lang.reflect.Method method : Class.forName(layer).getDeclaredMethods()) joined |= method.getName().contains("mosaicary$");
			} catch (ClassNotFoundException | LinkageError absent) {
				// The other line's name.
			}
		}
		genderLog("the outer layer's mixin is " + (joined ? "in" : "not in") + " Female Gender Mod's layer");
		Identifier built = SkinLayers3d.builtSkin(minecraft.player);
		genderLog("3D Skin Layers keeps " + (SkinLayers3d.torso(minecraft.player) != null ? "a" : "no") + " jacket on the player, built from "
			+ built + (built == null ? "" : SkinLayers3d.skin(built) != null ? ", whose pixels it reads" : ", whose pixels it does not read"));
	}

	private static void putBody(Minecraft minecraft, String gender) {
		JsonObject body = new JsonObject();
		body.addProperty("gender", gender);
		body.addProperty("bust_size", 0.8);
		body.addProperty("breast_physics", false);
		genderLog("the " + gender + " body " + (GenderMod.put(minecraft.getUser().getProfileId(), body) ? "is on" : "was not taken"));
	}

	/** The body turned from where the head looks, the head left as it is. */
	private static void turnBody(Minecraft minecraft, int degrees) {
		minecraft.player.setYBodyRot(minecraft.player.getYRot() - degrees);
		minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
	}

	/** Hides the game's own interface, as F1 does, or shows it again. */
	private static void hideGui(Minecraft minecraft, boolean hidden) {
		//#if MC>=260100
		if (minecraft.gui.hud.isHidden() != hidden) minecraft.gui.hud.toggle();
		//#else
		//$$ minecraft.options.hideGui = hidden;
		//#endif
	}

	/**
	 * The screen's shown button or tab whose label is the game's text for a key, or (with {@code whole}
	 * false) starts with it.
	 */
	private static AbstractWidget button(ContainerEventHandler in, String key, boolean whole) {
		String text = I18n.get(key);
		for (GuiEventListener child : in.children()) {
			if (child instanceof AbstractWidget widget && widget.visible && widget.active) {
				String label = widget.getMessage().getString();
				if (whole ? label.equals(text) : label.startsWith(text)) return widget;
			} else if (child instanceof ContainerEventHandler inner) {
				// The tabs of the new world screen, on their bar.
				AbstractWidget found = button(inner, key, whole);
				if (found != null) return found;
			}
		}
		return null;
	}

	/**
	 * Presses a button, or picks a tab, with a click of the screen at its middle: a tab is picked by
	 * the bar it is on, which takes the click from the screen.
	 */
	private static void press(Screen screen, AbstractWidget widget) {
		//#if MC>=12111
		if (widget instanceof AbstractButton button) button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
		//#else
		//$$ double x = widget.getX() + widget.getWidth() / 2.0;
		//$$ double y = widget.getY() + widget.getHeight() / 2.0;
		//$$ screen.mouseClicked(x, y, InputConstants.MOUSE_BUTTON_LEFT);
		//$$ screen.mouseReleased(x, y, InputConstants.MOUSE_BUTTON_LEFT);
		//#endif
	}

	/** Grey, with the front of the jacket's outer layer checkered in two bright colours and the rest of the outer layer empty. */
	private static Skins.Texture checkered() {
		int[] pixels = new int[64 * 64];
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				boolean outer = (y < 16 && x >= 32) || (y >= 32 && y < 48 && x < 56) || (y >= 48 && (x < 16 || x >= 48));
				pixels[y * 64 + x] = outer ? 0 : 0xFF808080;
			}
		}
		// The jacket's front: 8 by 12 from (20, 36).
		for (int y = 36; y < 48; y++) {
			for (int x = 20; x < 28; x++) pixels[y * 64 + x] = ((x + y) & 1) == 0 ? 0xFF00E0FF : 0xFFFF00E0;
		}
		return new Skins.Texture(Skins.State.READY, null, PlayerModelType.WIDE, pixels, null);
	}

	private static void genderLog(String line) {
		Mosaicary.LOGGER.info("Gender run: {}", line);
	}
	//#endif

	private static void serverLog(String line) {
		Mosaicary.LOGGER.info("Server run: {}", line);
	}

	private void leaveCard() {
		wardrobe.closeDetail();
		wardrobe.showTab(SkinsScreen.Tab.WARDROBE);
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
		// Before 1.21.5 the picture is read back at once rather than handed over later.
		//#if MC>=12111
		Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> write(minecraft, image, target));
		//#elseif MC>=11700
		//$$ write(minecraft, Screenshot.takeScreenshot(minecraft.getMainRenderTarget()), target);
		//#else
		//$$ com.mojang.blaze3d.pipeline.RenderTarget frame = minecraft.getMainRenderTarget();
		//$$ write(minecraft, Screenshot.takeScreenshot(frame.width, frame.height, frame), target);
		//#endif
	}

	private void write(Minecraft minecraft, NativeImage image, Path target) {
		try (image) {
			image.writeToFile(target);
			Mosaicary.LOGGER.info("Screenshot written: {} ({} x {}, GUI scale {})", target.toAbsolutePath(),
				image.getWidth(), image.getHeight(), minecraft.getWindow().getGuiScale());
		} catch (Exception failed) {
			Mosaicary.LOGGER.warn("Screenshot {} failed: {}", target, failed.toString());
		} finally {
			minecraft.execute(() -> writing = false);
		}
	}

	//#if MC>=12111
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
	//#endif
}
