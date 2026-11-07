//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.Catalogue;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.build.Builds;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.SiteClient;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
import com.mopicmp.mosaicary.client.ui.IconButton;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/**
 * Builds on their own shelf, and installed for the mods that place them, with real input.
 *
 * The models' shelf has no build on it; the builds button shows only builds; a build
 * opens to its picture with Install, which says why it cannot install while no mod here
 * places builds (none is in the test game). With all three set as installed, Install
 * puts one file in each mod's folder, the three read back as the same build, and the
 * WorldEdit command is in the chat. The install is one counted download on the live site.
 */
public final class BuildsGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		// The model shelf is hidden in this release; -Dmosaicary.models=true brings it and this test back.
		if (!com.mopicmp.mosaicary.client.MosaicaryClient.MODELS) return;
		try (Steps.LightWorld world = Steps.lightWorld(context)) {
			Steps.openCatalogue(context);
			List<ModelCard> models = shown(context);
			if (models.isEmpty()) throw new AssertionError("The models' shelf is empty");
			long mixed = models.stream().filter(ModelCard::isBuild).count();
			if (mixed > 0) throw new AssertionError("The models' shelf shows " + mixed + " builds");

			Steps.click(context, context.computeOnClient((Minecraft minecraft) -> screen(minecraft.gui.screen()).buildsButton()));
			context.waitFor((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().picturesSettled(screen(minecraft.gui.screen()).shown()), 20 * 120);
			context.waitTicks(10);
			List<ModelCard> builds = shown(context);
			if (builds.isEmpty() || !builds.stream().allMatch(ModelCard::isBuild)) throw new AssertionError("The builds' shelf shows " + builds);
			context.takeScreenshot("mosaicary-builds-1-shelf");

			// No mod here places builds: Install is there, inactive, and says why.
			ModelCard card = Steps.openModel(context, builds.get(0).id());
			context.waitFor((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().picture(card).state() != Catalogue.State.LOADING, 20 * 60);
			IconButton install = context.computeOnClient((Minecraft minecraft) -> screen(minecraft.gui.screen()).downloadButton());
			if (install == null || install.active) throw new AssertionError("Install is offered with no mod to install into");
			hover(context, install);
			context.takeScreenshot("mosaicary-builds-2-no-mod-to-place-it");

			// All three, as if they were installed; the view is opened again to read them.
			context.runOnClient((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().site().placers(List.of(Builds.Format.values())));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof CatalogueScreen screen && screen.openCard() == null, 100);
			Steps.openModel(context, card.id());
			install = context.computeOnClient((Minecraft minecraft) -> screen(minecraft.gui.screen()).downloadButton());
			if (!install.active) throw new AssertionError("Install is not offered with all three mods");
			hover(context, install);
			context.takeScreenshot("mosaicary-builds-3-install-tooltip");
			Steps.click(context, install);
			context.waitFor((Minecraft minecraft) -> {
				Catalogue.Download state = MosaicaryClient.instance().catalogue().download(card);
				return state != null && state.state() != Catalogue.State.LOADING;
			}, 20 * 120);
			Catalogue.Download done = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().download(card));
			if (done.state() != Catalogue.State.READY) throw new AssertionError("Install failed: " + done.failure());

			SiteClient site = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().site());
			int blocks = -1;
			for (Builds.Format format : Builds.Format.values()) {
				Path file = site.buildPath(card, format);
				try {
					Builds.Structure structure = Builds.read(Files.readAllBytes(file));
					if (Builds.formatOf(Files.readAllBytes(file)) != format) throw new AssertionError(file + " is not " + format);
					if (blocks >= 0 && structure.blockCount() != blocks) {
						throw new AssertionError(file + " has " + structure.blockCount() + " blocks, not " + blocks);
					}
					blocks = structure.blockCount();
					System.out.println("[builds] " + format.mod + ": " + file + " (" + Files.size(file) + " bytes, " + blocks + " blocks)");
				} catch (IOException unreadable) {
					throw new UncheckedIOException(unreadable);
				}
			}
			context.waitTicks(5);
			hover(context, install);
			context.takeScreenshot("mosaicary-builds-4-installed");

			// Out to the world, where the chat holds WorldEdit's command.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof CatalogueScreen screen && screen.openCard() == null, 100);
			Steps.click(context, context.computeOnClient((Minecraft minecraft) -> screen(minecraft.gui.screen()).buildsButton()));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);
			context.waitTicks(10);
			context.takeScreenshot("mosaicary-builds-5-worldedit-command");
			context.runOnClient((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().site().placers(List.of()));
		}
	}

	private static CatalogueScreen screen(Object screen) {
		return (CatalogueScreen) screen;
	}

	private static List<ModelCard> shown(ClientGameTestContext context) {
		return context.computeOnClient((Minecraft minecraft) -> screen(minecraft.gui.screen()).shown());
	}

	/** Rests the pointer on a widget until its tooltip shows. */
	private static void hover(ClientGameTestContext context, IconButton widget) {
		double scale = Steps.scale(context);
		context.getInput().setCursorPos((widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale);
		context.waitTicks(20);
	}
}
//#else
//$$ package com.mopicmp.mosaicary.gametest;
//$$
//$$ import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
//$$ import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
//$$
//$$ /** The model catalogue is built from 1.21.11 on; before, there is nothing here to test. */
//$$ public final class BuildsGameTest implements FabricClientGameTest {
//$$ 	@Override
//$$ 	public void runTest(ClientGameTestContext context) {
//$$ 	}
//$$ }
//#endif
