package com.mopicmp.mosaicary.gametest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.compat.GenderMod;
import com.mopicmp.mosaicary.client.ui.IconButton;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;

/**
 * Female Gender Mod's body, set in the card of a skin, clicked as a player does. The button beside
 * the catalogue's girls opens the card of the skin worn last, or of the first girl, with the body's
 * panel in place of the description. From the main menu what is set waits, kept in the settings,
 * and is put on in the mod on entering a world; there a change goes into the mod at once, and the
 * panel's button opens the mod's own window, which closes back to the catalogue. Without the mod
 * ({@code -PtestMods} not pointing at it) the button is there to lead to the mod's page and is not
 * clicked, as that would open a browser.
 */
public final class GenderBodyGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		context.getInput().resizeWindow(1280, 720);
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(40);
		SkinsScreen.query(SkinsScreen.query().withCategory("girls"));
		try {
			if (!context.computeOnClient((Minecraft minecraft) -> GenderMod.installed())) {
				openCatalogue(context);
				IconButton button = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).genderButton());
				if (button == null || !button.active) throw new AssertionError("Without Female Gender Mod there is no button to its page");
				context.takeScreenshot("gender-body-0-missing");
				System.out.println("gender body test: without the mod, the button leads to its page");
				context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(new TitleScreen()); });
				return;
			}

			// From the main menu: a female body with the breasts at half their largest size, which waits.
			openCatalogue(context);
			openPanel(context, "menu");
			context.takeScreenshot("gender-body-1-menu");
			Steps.click(context, named(context, "mosaicary.body.gender.female"));
			context.waitTicks(5);
			Steps.click(context, sliders(context).get(0));
			context.waitTicks(20);
			float[] range = context.computeOnClient((Minecraft minecraft) -> GenderMod.range("bust_size"));
			double half = Math.round((range[0] + range[1]) / 2 * 100) / 100.0;
			JsonObject waiting = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().genderWaiting);
			System.out.println("gender body test: menu: waiting " + waiting);
			if (waiting == null || !"female".equals(waiting.get("gender").getAsString())
					|| Math.abs(waiting.get("bust_size").getAsDouble() - half) > 0.011) {
				throw new AssertionError("The body set in the menu is not waiting for a world: " + waiting);
			}
			context.takeScreenshot("gender-body-2-menu-set");
			context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(new TitleScreen()); });
			context.waitForScreen(TitleScreen.class);

			try (Steps.LightWorld world = Steps.lightWorld(context)) {
				context.getInput().resizeWindow(1280, 720);
				// Put on once the mod has read the player's settings.
				context.waitFor((Minecraft minecraft) -> {
					JsonObject now = GenderMod.read(minecraft.getUser().getProfileId());
					return now != null && now.has("gender") && "female".equals(now.get("gender").getAsString())
						&& Math.abs(now.get("bust_size").getAsDouble() - half) < 0.011;
				}, 20 * 30);
				System.out.println("gender body test: world: the mod has " + context.computeOnClient((Minecraft minecraft) ->
					GenderMod.read(minecraft.getUser().getProfileId())));
				if (context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().genderWaiting) != null) {
					throw new AssertionError("The body put on is still waiting");
				}

				// In the world a switch goes into the mod at once.
				openCatalogue(context);
				openPanel(context, "world");
				boolean before = context.computeOnClient((Minecraft minecraft) ->
					GenderMod.read(minecraft.getUser().getProfileId()).get("breast_physics").getAsBoolean());
				Steps.click(context, named(context, "mosaicary.body.physics"));
				context.waitTicks(5);
				boolean after = context.computeOnClient((Minecraft minecraft) ->
					GenderMod.read(minecraft.getUser().getProfileId()).get("breast_physics").getAsBoolean());
				System.out.println("gender body test: world: physics " + before + " -> " + after);
				if (before == after) throw new AssertionError("The physics switch did not reach the mod");
				context.takeScreenshot("gender-body-3-world");

				// The mod's own window from the panel, and back.
				AbstractWidget window = named(context, "mosaicary.body.window");
				Steps.click(context, window);
				context.waitFor((Minecraft minecraft) -> {
					Screen open = minecraft.gui.screen();
					return open != null && !(open instanceof SkinsScreen);
				}, 100);
				String opened = context.computeOnClient((Minecraft minecraft) -> minecraft.gui.screen().getClass().getName());
				System.out.println("gender body test: world: opened " + opened);
				if (!opened.startsWith("com.wildfire.")) throw new AssertionError("Opened " + opened + ", not Female Gender Mod's window");
				context.waitTicks(20);
				context.takeScreenshot("gender-body-4-window");
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof SkinsScreen, 100);
				System.out.println("gender body test: world: closed back to the catalogue");
				context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(null); });
				context.waitForScreen(null);
			}
		} finally {
			SkinsScreen.query(SkinsScreen.query().withCategory("any"));
		}
	}

	private static void openCatalogue(ClientGameTestContext context) {
		context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(new SkinsScreen(minecraft.gui.screen(), MosaicaryClient.instance().skins())); });
		context.waitForScreen(SkinsScreen.class);
		context.waitFor((Minecraft minecraft) -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
		context.waitTicks(20);
	}

	/** The button beside the girls, clicked: the card opens with the body's panel. */
	private static void openPanel(ClientGameTestContext context, String where) {
		IconButton button = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).genderButton());
		if (button == null) throw new AssertionError("No button to the body beside the girls (" + where + ")");
		if (!button.active) throw new AssertionError("The button to the body is greyed out (" + where + ")");
		Steps.click(context, button);
		context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.bodyOpen(), 100);
		context.waitTicks(20);
		System.out.println("gender body test: " + where + ": the body's panel is open on " + context.computeOnClient((Minecraft minecraft) ->
			((SkinsScreen) minecraft.gui.screen()).openCard() != null ? ((SkinsScreen) minecraft.gui.screen()).openCard().name() : "a skin of My skins"));
	}

	/** The widget of the screen named by a key, as a player reads it. */
	private static AbstractWidget named(ClientGameTestContext context, String key) {
		AbstractWidget found = context.computeOnClient((Minecraft minecraft) -> {
			String name = I18n.get(key);
			for (var child : minecraft.gui.screen().children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(name)) return widget;
			}
			return null;
		});
		if (found == null) throw new AssertionError("Nothing named " + key + " on the screen");
		return found;
	}

	/** The panel's sliders, top to bottom: the size first. */
	private static List<AbstractWidget> sliders(ClientGameTestContext context) {
		return context.computeOnClient((Minecraft minecraft) -> {
			List<AbstractWidget> out = new ArrayList<>();
			for (var child : minecraft.gui.screen().children()) {
				if (child instanceof AbstractSliderButton slider) out.add(slider);
			}
			return out;
		});
	}
}
