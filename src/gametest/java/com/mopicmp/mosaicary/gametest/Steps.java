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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

/** Steps shared by the game tests, all through real input. */
final class Steps {

	private Steps() { }

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
