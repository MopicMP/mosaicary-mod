//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.gametest;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.Catalogue;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
import com.mopicmp.mosaicary.client.ui.PauseMenuButton;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;

/**
 * The catalogue driven with real input in a running game.
 *
 * Every step goes through the game's own keyboard and mouse handlers (Fabric's
 * test input feeds them the way the window would), so what passes here is what a
 * player's key press or click does, not what a method call does. Screenshots of
 * each step land in the test run's screenshots folder.
 *
 * Steps: in a fresh world, the M key opens the catalogue; a click on the first
 * card opens its 3D view; dragging across the preview turns the model; Escape goes
 * back and closes; the pause menu carries the catalogue button, and clicking it
 * opens the catalogue over the pause menu.
 */
public final class CatalogueGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		// The model shelf is hidden in this release; -Dmosaicary.models=true brings it and this test back.
		if (!com.mopicmp.mosaicary.client.MosaicaryClient.MODELS) return;
		try (Steps.LightWorld world = Steps.lightWorld(context)) {

			// The key.
			context.getInput().pressKey(InputConstants.KEY_M);
			context.waitForScreen(CatalogueScreen.class);
			context.waitFor((Minecraft minecraft) -> {
				Catalogue catalogue = MosaicaryClient.instance().catalogue();
				return catalogue.listState() != Catalogue.State.LOADING
					&& catalogue.picturesSettled(((CatalogueScreen) minecraft.gui.screen()).shown());
			}, 20 * 120);
			context.waitTicks(20);
			context.takeScreenshot("mosaicary-1-key-opens-catalogue");

			// A real click on the first card.
			double scale = context.computeOnClient((Minecraft minecraft) -> (double) minecraft.getWindow().getGuiScale());
			List<ModelCard> shown = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).shown());
			if (shown.isEmpty()) throw new AssertionError("The catalogue shows no models to click");
			context.getInput().setCursorPos(40 * scale, 60 * scale);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).openCard() != null, 100);
			context.waitFor((Minecraft minecraft) -> {
				CatalogueScreen screen = (CatalogueScreen) minecraft.gui.screen();
				return MosaicaryClient.instance().catalogue().model(screen.openCard()).state() != Catalogue.State.LOADING;
			}, 20 * 120);
			context.waitTicks(20);
			context.takeScreenshot("mosaicary-2-click-opens-detail");

			// A real drag across the preview turns the model.
			// The pointer is moved there first and the button pressed a few frames later,
			// as a hand would: the game turns movement into drag events once per frame,
			// so a jump and a press in the same frame would count the jump as a drag.
			context.getInput().setCursorPos(120 * scale, 120 * scale);
			context.waitTicks(3);
			float before = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).spin());
			float tiltBefore = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).tilt());
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			for (int i = 0; i < 10; i++) {
				context.getInput().moveCursor(12 * scale, 0);
				context.waitTick();
			}
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			float after = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).spin());
			float tiltAfter = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).tilt());
			if (Math.abs(after - before) < 0.5f) {
				throw new AssertionError("Dragging did not turn the model: spin " + before + " -> " + after);
			}
			// A sideways drag turns about the vertical axis only.
			if (Math.abs(tiltAfter - tiltBefore) > 0.05f) {
				throw new AssertionError("A sideways drag tilted the model: " + tiltBefore + " -> " + tiltAfter);
			}
			context.waitTicks(10);
			context.takeScreenshot("mosaicary-3-drag-turns-model");

			// Escape: back to the grid, then closed.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof CatalogueScreen screen && screen.openCard() == null, 100);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			// The pause menu and its button.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(PauseScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("mosaicary-4-pause-menu");
			double[] button = context.computeOnClient((Minecraft minecraft) -> {
				for (AbstractWidget widget : Screens.getWidgets(minecraft.gui.screen())) {
					if (widget instanceof PauseMenuButton) {
						return new double[] { widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0 };
					}
				}
				return null;
			});
			if (button == null) throw new AssertionError("The pause menu has no catalogue button");
			context.getInput().setCursorPos(button[0] * scale, button[1] * scale);
			context.waitTicks(10);
			context.takeScreenshot("mosaicary-5-pause-button-tooltip");
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitForScreen(CatalogueScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("mosaicary-6-pause-button-opens-catalogue");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(PauseScreen.class);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);
		}
	}
}
//#else
//$$ package com.mopicmp.mosaicary.gametest;
//$$
//$$ import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
//$$ import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
//$$
//$$ /** The model catalogue is built from 1.21.11 on; before, there is nothing here to test. */
//$$ public final class CatalogueGameTest implements FabricClientGameTest {
//$$ 	@Override
//$$ 	public void runTest(ClientGameTestContext context) {
//$$ 	}
//$$ }
//#endif
