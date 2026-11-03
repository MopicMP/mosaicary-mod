//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
import com.mopicmp.mosaicary.client.ui.RingScreen;
import com.mopicmp.mosaicary.client.ui.SlotScreen;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/**
 * Slots and the ring, with real input, as steps of the wear test's world: bind two models to slots from the detail
 * view, hold the ring key, point at a slot, release, and check the model changed;
 * then point at the centre and release to go back to the own skin.
 */
final class RingGameTest {

	/** A live model without movement animations, bound to the second slot. */
	static final String PLAIN = "m3TT0q6uIz09";

	private RingGameTest() { }

	/** Runs inside a world that is already open, with no screen on. */
	static void steps(ClientGameTestContext context) {
		bind(context, WearGameTest.RIGGED, 0);
		bind(context, PLAIN, 1);
		// Out of the model's view to the grid, then out of the catalogue.
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof CatalogueScreen screen && screen.openCard() == null, 100);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.getInput().pressKey(InputConstants.KEY_F5);
		context.getInput().pressKey(InputConstants.KEY_F5);
		context.waitTicks(10);

		// Hold the ring key, point at the second slot (to the upper right), release.
		context.getInput().holdKey(InputConstants.KEY_R);
		context.waitForScreen(RingScreen.class);
		pointAtSlot(context, 1);
		context.waitTicks(20);
		context.takeScreenshot("mosaicary-ring-1-open-pointing-at-slot-2");
		context.getInput().releaseKey(InputConstants.KEY_R);
		context.waitForScreen(null);
		expectWorn(context, PLAIN);
		context.waitFor((Minecraft minecraft) -> MosaicaryClient.instance().catalogue()
			.model(MosaicaryClient.instance().wearing().worn()).state() != com.mopicmp.mosaicary.client.Catalogue.State.LOADING, 600);
		context.waitTicks(20);
		context.takeScreenshot("mosaicary-ring-2-slot-2-worn");

		// The first slot, then the centre.
		context.getInput().holdKey(InputConstants.KEY_R);
		context.waitForScreen(RingScreen.class);
		pointAtSlot(context, 0);
		context.getInput().releaseKey(InputConstants.KEY_R);
		context.waitForScreen(null);
		expectWorn(context, WearGameTest.RIGGED);
		context.waitTicks(20);
		context.takeScreenshot("mosaicary-ring-3-slot-1-worn");

		context.getInput().holdKey(InputConstants.KEY_R);
		context.waitForScreen(RingScreen.class);
		double scale = Steps.scale(context);
		int[] size = context.computeOnClient((Minecraft minecraft) -> new int[] { minecraft.gui.screen().width, minecraft.gui.screen().height });
		context.getInput().setCursorPos(size[0] / 2.0 * scale, size[1] / 2.0 * scale);
		context.waitTicks(3);
		context.getInput().releaseKey(InputConstants.KEY_R);
		context.waitForScreen(null);
		expectWorn(context, null);
		context.getInput().pressKey(InputConstants.KEY_F5);
	}

	/** Opens a model in the catalogue and binds it to a slot through the slot chooser. */
	private static void bind(ClientGameTestContext context, String id, int slot) {
		if (!(context.computeOnClient((Minecraft minecraft) -> minecraft.gui.screen()) instanceof CatalogueScreen)) {
			Steps.openCatalogue(context);
		} else {
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof CatalogueScreen screen && screen.openCard() == null, 100);
		}
		Steps.openModel(context, id);
		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).bindButton()));
		context.waitForScreen(SlotScreen.class);
		context.waitTicks(10);
		if (slot == 0) context.takeScreenshot("mosaicary-slots-chooser");
		int[] centre = context.computeOnClient((Minecraft minecraft) -> ((SlotScreen) minecraft.gui.screen()).slotCentre(slot));
		Steps.click(context, centre[0], centre[1]);
		context.waitForScreen(CatalogueScreen.class);
		Settings.Saved held = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().slots.get(slot));
		if (held == null || !held.id.equals(id)) throw new AssertionError("Slot " + (slot + 1) + " does not hold " + id);
	}

	private static void pointAtSlot(ClientGameTestContext context, int slot) {
		double scale = Steps.scale(context);
		int[] at = context.computeOnClient((Minecraft minecraft) -> ((RingScreen) minecraft.gui.screen()).slotPoint(slot));
		context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
		context.waitTicks(3);
	}

	private static void expectWorn(ClientGameTestContext context, String id) {
		ModelCard worn = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().wearing().worn());
		String got = worn == null ? null : worn.id();
		if (id == null ? got != null : !id.equals(got)) throw new AssertionError("Expected " + id + " worn, got " + got);
	}
}
//#endif
