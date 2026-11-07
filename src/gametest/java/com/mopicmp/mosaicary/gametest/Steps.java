package com.mopicmp.mosaicary.gametest;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
//#if MC>=12111
import com.mopicmp.mosaicary.client.Catalogue;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
//#endif

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

/** Steps shared by the game tests, all through real input. */
final class Steps {

	private Steps() { }

	/**
	 * Whether a test is left out of this run: one test alone is run with {@code -PtestOnly=}its
	 * class name, as Fabric runs every test of a mod and the whole round takes ten minutes.
	 */
	static boolean skipped(Object test) {
		String only = System.getProperty("mosaicary.testOnly");
		return only != null && !only.equals(test.getClass().getSimpleName());
	}

	/**
	 * A test world, made so that it loads on a computer without a graphics card (Xvfb), where the
	 * processor draws the game. The test runs the game and its server a tick each in turn, so the
	 * world is built only as fast as frames are drawn, and the loading screen's blurred background
	 * takes seconds a frame: on two cores the world was never done ("Timeout loading world"). So the
	 * window is small and the blur off while it loads, and the fewest chunks are drawn around the
	 * player while it is open. Its chunks drawn, the window is its size again; closed, the world
	 * gives back the settings.
	 */
	static LightWorld lightWorld(ClientGameTestContext context) {
		int[] before = context.computeOnClient((Minecraft minecraft) -> new int[] { minecraft.getWindow().getScreenWidth(),
			minecraft.getWindow().getScreenHeight(), minecraft.options.menuBackgroundBlurriness().get(), minecraft.options.renderDistance().get() });
		context.getInput().resizeWindow(640, 360);
		context.runOnClient((Minecraft minecraft) -> {
			minecraft.options.menuBackgroundBlurriness().set(0);
			minecraft.options.renderDistance().set(2);
		});
		TestSingleplayerContext world = context.worldBuilder().create();
		world.getConnection().waitForChunksRender();
		context.getInput().resizeWindow(before[0], before[1]);
		return new LightWorld(context, world, before[2], before[3]);
	}

	/** A world made by {@link #lightWorld}: closing it closes the world and puts the settings back. */
	record LightWorld(ClientGameTestContext context, TestSingleplayerContext world, int blur, int distance) implements AutoCloseable {
		@Override
		public void close() {
			world.close();
			context.runOnClient((Minecraft minecraft) -> {
				minecraft.options.menuBackgroundBlurriness().set(blur);
				minecraft.options.renderDistance().set(distance);
			});
		}
	}

	/** Hides the game's own interface, as F1 does, or shows it again; on the screen and in screenshots. */
	static void hideGui(Minecraft minecraft, boolean hidden) {
		//#if MC>=260100
		if (minecraft.gui.hud.isHidden() != hidden) minecraft.gui.hud.toggle();
		//#else
		//$$ minecraft.options.hideGui = hidden;
		//#endif
	}

	/** The GUI scale, to turn GUI units into window pixels for the pointer. */
	static double scale(ClientGameTestContext context) {
		return context.computeOnClient((Minecraft minecraft) -> (double) minecraft.getWindow().getGuiScale());
	}

	/**
	 * Moves the pointer to a point in GUI units, waits for the game to see the move,
	 * and clicks. The wait matters: the game turns pointer movement into events once a
	 * frame, and a move and a press in the same frame read as a drag.
	 */
	static void click(ClientGameTestContext context, double guiX, double guiY) {
		double scale = scale(context);
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTicks(3);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	static void click(ClientGameTestContext context, AbstractWidget widget) {
		if (widget == null) throw new AssertionError("Nothing to click");
		click(context, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
	}

	//#if MC>=12111
	/** Opens the catalogue with its key and waits for the list and the card pictures. */
	static CatalogueScreen openCatalogue(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_M);
		context.waitForScreen(CatalogueScreen.class);
		context.waitFor((Minecraft minecraft) -> {
			Catalogue catalogue = MosaicaryClient.instance().catalogue();
			return catalogue.listState() != Catalogue.State.LOADING
				&& catalogue.picturesSettled(((CatalogueScreen) minecraft.gui.screen()).shown());
		}, 20 * 120);
		context.waitTicks(10);
		return context.computeOnClient((Minecraft minecraft) -> (CatalogueScreen) minecraft.gui.screen());
	}

	/** Clicks a model's card by its id and waits until its 3D view has the model. */
	static ModelCard openModel(ClientGameTestContext context, String id) {
		List<ModelCard> shown = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).shown());
		ModelCard card = shown.stream().filter(one -> one.id().equals(id)).findFirst()
			.orElseThrow(() -> new AssertionError("The catalogue does not show " + id));
		int[] center = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).cardCenter(card));
		click(context, center[0], center[1]);
		context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof CatalogueScreen screen && screen.openCard() != null, 100);
		context.waitFor((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().model(card).state() != Catalogue.State.LOADING,
			20 * 120);
		context.waitTicks(10);
		return card;
	}
	//#endif
}
