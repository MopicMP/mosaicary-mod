package com.mopicmp.mosaicary.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Wearing a model, with real input: open the catalogue, open a model, press Wear and
 * answer the sharing question, change a role, look at the player in third person
 * standing and walking, then take it off.
 *
 * The model is the live site's rigged character, which has idle, walking and run
 * animations; that is the case the worn model is played by movement state.
 */
public final class WearGameTest implements FabricClientGameTest {

	/** The live model with idle, walking and run animations. */
	static final String RIGGED = "rS16fadhG31s";

	@Override
	public void runTest(ClientGameTestContext context) {
		// The model shelf is hidden in this release; -Dmosaicary.models=true brings it and this test back.
		if (!com.mopicmp.mosaicary.client.MosaicaryClient.MODELS) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.getInput().lookAt(0, 0);

			// The hints, the sharing question, the roles, wearing, the settings and the
			// ring share this one world: every world the run closes is another chance for
			// the test harness to stall on this machine.
			ConsentSteps.keyHint(context);

			Steps.openCatalogue(context);
			ModelCard card = Steps.openModel(context, RIGGED);
			ConsentSteps.consentOnFirstWear(context, card);
			context.waitTicks(5);
			context.takeScreenshot("mosaicary-wear-1-detail-shows-take-off");
			ConsentSteps.editRole(context, card);

			// Back to the world, the camera in front of the player.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);
			context.getInput().pressKey(InputConstants.KEY_F5);
			context.getInput().pressKey(InputConstants.KEY_F5);
			context.waitTicks(30);
			context.takeScreenshot("mosaicary-wear-2-standing");

			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(14);
			context.takeScreenshot("mosaicary-wear-3-walking");
			context.getInput().releaseKey(options -> options.keyUp);
			context.waitTicks(10);

			// Taking it off through the same button.
			Steps.openCatalogue(context);
			Steps.openModel(context, RIGGED);
			Steps.click(context, context.computeOnClient(minecraft -> ((CatalogueScreen) minecraft.gui.screen()).wearButton()));
			if (context.computeOnClient(minecraft -> MosaicaryClient.instance().wearing().worn()) != null) {
				throw new AssertionError("Take off left the model on");
			}
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);
			context.waitTicks(10);
			context.takeScreenshot("mosaicary-wear-4-taken-off");
			context.getInput().pressKey(InputConstants.KEY_F5);

			ConsentSteps.settingsSteps(context);
			RingGameTest.steps(context);
			FiguraSteps.steps(context);
		}
	}
}
