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
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * A Figura avatar from the player's own folder, with real input: the Figura button
 * lists it, its card opens its view (with the notice that scripts are not run), Wear
 * puts it on, and it is seen in third person.
 *
 * The avatar is written into the test run's game folder first: a humanoid in six
 * groups named the way Figura attaches them, without a texture, and one Lua script.
 */
final class FiguraSteps {

	private FiguraSteps() { }

	private static final String[][] PARTS = {
		// name, from, to, pivot
		{ "Head", "-4,24,-4", "4,32,4", "0,24,0" },
		{ "Body", "-4,12,-2", "4,24,2", "0,24,0" },
		{ "RightArm", "-8,12,-2", "-4,24,2", "-5,22,0" },
		{ "LeftArm", "4,12,-2", "8,24,2", "5,22,0" },
		{ "RightLeg", "-4,0,-2", "0,12,2", "-2,12,0" },
		{ "LeftLeg", "0,0,-2", "4,12,2", "2,12,0" },
	};

	private static String avatarModel() {
		StringBuilder elements = new StringBuilder();
		StringBuilder outliner = new StringBuilder();
		for (int i = 0; i < PARTS.length; i++) {
			String[] part = PARTS[i];
			String comma = i > 0 ? "," : "";
			StringBuilder faces = new StringBuilder();
			for (String face : List.of("north", "east", "south", "west", "up", "down")) {
				faces.append(faces.isEmpty() ? "" : ",").append('"').append(face).append("\":{\"uv\":[0,0,1,1]}");
			}
			elements.append(comma).append("{\"type\":\"cube\",\"uuid\":\"c").append(i).append("\",\"color\":").append(i)
				.append(",\"from\":[").append(part[1]).append("],\"to\":[").append(part[2]).append("],\"faces\":{")
				.append(faces).append("}}");
			outliner.append(comma).append("{\"name\":\"").append(part[0]).append("\",\"uuid\":\"g").append(i)
				.append("\",\"origin\":[").append(part[3]).append("],\"children\":[\"c").append(i).append("\"]}");
		}
		return "{\"meta\":{\"format_version\":\"4.10\",\"model_format\":\"free\"},\"elements\":[" + elements
			+ "],\"outliner\":[" + outliner + "],\"textures\":[]}";
	}

	/** Writes the avatar into the game folder's figura/avatars. */
	static void writeAvatar() {
		Path folder = FabricLoader.getInstance().getGameDir().resolve("figura/avatars/Game test avatar");
		try {
			Files.createDirectories(folder);
			Files.writeString(folder.resolve("avatar.json"), "{\"name\":\"Game test avatar\",\"authors\":[\"Mosaicary\"]}");
			Files.writeString(folder.resolve("figure.bbmodel"), avatarModel());
			Files.writeString(folder.resolve("script.lua"), "vanilla_model.PLAYER:setVisible(false)\n");
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	static void steps(ClientGameTestContext context) {
		writeAvatar();
		Steps.openCatalogue(context);
		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).figuraButton()));
		context.waitFor((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().localState() == Catalogue.State.READY, 200);
		context.waitTicks(5);
		context.takeScreenshot("mosaicary-figura-1-own-avatars");
		List<ModelCard> shown = context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).shown());
		if (shown.size() != 1 || !shown.get(0).isLocal()) throw new AssertionError("The Figura list shows " + shown);

		ModelCard card = Steps.openModel(context, shown.get(0).id());
		Catalogue.Model model = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().catalogue().model(card));
		if (model.state() != Catalogue.State.READY) throw new AssertionError("The avatar did not open: " + model.problem());
		if (model.scripts() != 1) throw new AssertionError("Scripts counted: " + model.scripts());
		context.takeScreenshot("mosaicary-figura-2-detail");

		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).wearButton()));
		ModelCard worn = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().wearing().worn());
		if (worn == null || !worn.id().equals(card.id())) throw new AssertionError("Wear did not put the avatar on");

		// Back to the site's list for the next steps, then out to the world.
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof CatalogueScreen screen && screen.openCard() == null, 100);
		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> ((CatalogueScreen) minecraft.gui.screen()).figuraButton()));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.getInput().pressKey(InputConstants.KEY_F5);
		context.getInput().pressKey(InputConstants.KEY_F5);
		context.waitTicks(30);
		context.takeScreenshot("mosaicary-figura-3-worn");
		context.getInput().pressKey(InputConstants.KEY_F5);
	}
}
//#endif
