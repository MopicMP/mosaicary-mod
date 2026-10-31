package com.mopicmp.mosaicary.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.render.Pictures;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

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
 * A screenshot is taken only after what it shows has been ready for a number of
 * ticks in a row: the picture read back is the frame drawn before this tick, so a
 * texture uploaded in the same tick would not be in it yet. The mouse is hidden from
 * the screen meanwhile, so no tooltip covers what is being photographed. Each wait
 * has a time limit, so a site that does not answer still ends the run with a picture
 * of whatever the screen showed.
 */
final class Shots {

	private enum Step { WAIT_TITLE, SCALE, WAIT_LIST, SHOOT_GRID, NEXT, OPEN_MODEL, WAIT_MODEL, SHOOT_MODEL, NEXT_FRAME, SHOOT_FRAME, QUIT, DONE }

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
		ClientTickEvents.END_CLIENT_TICK.register(shots::tick);
	}

	private void tick(Minecraft minecraft) {
		if (step == Step.DONE || writing) return;
		ticks++;
		waited++;
		switch (step) {
			case WAIT_TITLE -> {
				if (minecraft.gui.screen() instanceof TitleScreen && minecraft.gui.overlay() == null && ticks > 40) {
					started = System.currentTimeMillis();
					screen = client.open(minecraft.gui.screen());
					Mosaicary.LOGGER.info("Screenshot run: catalogue opened");
					go(Step.SCALE);
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
