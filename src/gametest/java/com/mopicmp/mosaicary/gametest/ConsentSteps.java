//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.model.BbModel;
import com.mopicmp.mosaicary.client.model.Roles;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
import com.mopicmp.mosaicary.client.ui.ConsentScreen;
import com.mopicmp.mosaicary.client.ui.Dropdown;
import com.mopicmp.mosaicary.client.ui.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * First use, with real input: the key hint on entering a world, the sharing question
 * on the first Wear, an animation role changed on the Animations tab, and the
 * settings screen.
 *
 * The run starts with an empty game folder, so nothing has been answered or opened
 * yet. These steps run inside the wear test's world, so the run opens and closes as
 * few worlds as it can.
 */
final class ConsentSteps {

	private ConsentSteps() { }

	private static CatalogueScreen catalogue(net.minecraft.client.Minecraft minecraft) {
		return (CatalogueScreen) minecraft.gui.screen();
	}

	/** Right after entering the world: the key hint is up and counted. */
	static void keyHint(ClientGameTestContext context) {
		context.waitTicks(20);
		context.takeScreenshot("mosaicary-hint-1-key-toast");
		int shown = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().hintShown);
		boolean toast = context.computeOnClient((Minecraft minecraft) -> minecraft.gui.toastManager().getToast(
			com.mopicmp.mosaicary.client.ui.HintToast.class, net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN) != null);
		if (shown != 1 || !toast) throw new AssertionError("Key hint: counted " + shown + ", on screen " + toast);
	}

	/**
	 * With a model open in the catalogue: the first Wear asks about sharing, Not now
	 * is clicked, and the model is on all the same.
	 */
	static void consentOnFirstWear(ClientGameTestContext context, ModelCard card) {
		boolean opened = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().catalogueOpened);
		if (!opened) throw new AssertionError("Opening the catalogue was not remembered");
		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).wearButton()));
		context.waitForScreen(ConsentScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("mosaicary-consent-1-first-wear");
		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> ((ConsentScreen) minecraft.gui.screen()).noButton()));
		context.waitForScreen(CatalogueScreen.class);
		String answer = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().sharing);
		if (!Settings.NO.equals(answer)) throw new AssertionError("Not now saved " + answer);
		ModelCard worn = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().wearing().worn());
		if (worn == null || !worn.id().equals(card.id())) throw new AssertionError("Not now did not put the model on");
	}

	/**
	 * On the Animations tab: the group dropdown is switched to items in hand, the
	 * swing row is brought up with the wheel if it is below the fold, its list is
	 * opened and Nothing is clicked.
	 */
	static void editRole(ClientGameTestContext context, ModelCard card) {
		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).rolesTabButton()));
		context.waitFor((Minecraft minecraft) -> catalogue(minecraft).roleBox(Roles.Action.IDLE.key) != null
			|| catalogue(minecraft).sectionBox() != null, 100);
		context.waitTicks(5);
		context.takeScreenshot("mosaicary-roles-1-tab");

		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).sectionBox()));
		context.waitTicks(3);
		double[] items = context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).sectionBox()
			.optionCenter(Roles.Section.ITEMS, minecraft.gui.screen().width));
		if (items == null) throw new AssertionError("The group list did not open");
		Steps.click(context, items[0], items[1]);
		context.waitTicks(5);

		double scale = Steps.scale(context);
		AbstractWidget tab = context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).rolesTabButton());
		context.getInput().setCursorPos((tab.getX() + tab.getWidth() / 2.0) * scale, (tab.getBottom() + 70) * scale);
		context.waitTicks(3);
		String swingKey = Roles.Action.SWING.key;
		for (int i = 0; i < 12 && context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).roleBox(swingKey)) == null; i++) {
			context.getInput().scroll(-1);
			context.waitTicks(2);
		}
		Dropdown<String> swing = context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).roleBox(swingKey));
		if (swing == null) throw new AssertionError("The swing row did not come up");
		Steps.click(context, swing);
		context.waitTicks(5);
		context.takeScreenshot("mosaicary-roles-2-list-open");
		double[] nothing = context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).roleBox(swingKey)
			.optionCenter(Roles.NOTHING, minecraft.gui.screen().width));
		if (nothing == null) throw new AssertionError("The swing list did not open with Nothing in view");
		Steps.click(context, nothing[0], nothing[1]);
		context.waitTicks(5);
		String chosen = context.computeOnClient((Minecraft minecraft) -> {
			BbModel model = MosaicaryClient.instance().catalogue().model(card).mesh().model();
			return MosaicaryClient.instance().wearing().rolesFor(card, model).get(Roles.Action.SWING);
		});
		if (!Roles.NOTHING.equals(chosen)) throw new AssertionError("Swing role is " + chosen);
		String sent = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().wearing().rolesToSend(card).get("swing"));
		if (!Roles.NOTHING.equals(sent)) throw new AssertionError("Swing role travels as " + sent);
		context.takeScreenshot("mosaicary-roles-3-swing-nothing");
	}

	/** In a world: the catalogue's settings button, and switching sharing on. */
	static void settingsSteps(ClientGameTestContext context) {
		Steps.openCatalogue(context);
		AbstractWidget gear = context.computeOnClient((Minecraft minecraft) -> catalogue(minecraft).settingsButton());
		Steps.click(context, gear);
		context.waitForScreen(SettingsScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("mosaicary-settings-1-sharing-off");
		Steps.click(context, context.computeOnClient((Minecraft minecraft) -> ((SettingsScreen) minecraft.gui.screen()).shareOnButton()));
		context.waitTicks(5);
		if (!Settings.YES.equals(context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().sharing))) {
			throw new AssertionError("Sharing did not switch on");
		}
		context.takeScreenshot("mosaicary-settings-2-sharing-on");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(CatalogueScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}
}
//#endif
