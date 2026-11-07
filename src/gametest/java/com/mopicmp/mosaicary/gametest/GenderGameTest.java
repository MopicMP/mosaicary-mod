package com.mopicmp.mosaicary.gametest;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.compat.GenderMod;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Female Gender Mod's body going with the skin, with that mod installed ({@code -PtestMods}
 * pointing at a folder with its jar); skipped without it. Two skins in the wardrobe; a body put on
 * while the first is worn is kept with it when the second is put on, which, having none, leaves the
 * body as it is; another body put on while the second is worn is kept with that one, and putting
 * the first on again brings its own back. Through the mod's own settings, read back from it. Then
 * in the main menu, where the mod has no settings for the player yet: the second skin put on there
 * brings its body as soon as a world is joined.
 *
 * Putting a skin on needs Mojang or an account on the site, neither of which a test has: the test
 * takes the two steps {@code Skins.wear} takes once the skin is on, the Gender mod first, then the
 * wardrobe. Screenshots of the player with each body.
 */
public final class GenderGameTest implements FabricClientGameTest {

	private static final String FIRST = "site:aaaaaaaaaaaa";
	private static final String SECOND = "site:bbbbbbbbbbbb";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		if (!FabricLoader.getInstance().isModLoaded("wildfire_gender") && !FabricLoader.getInstance().isModLoaded("female_gender_mod")) return;
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(40);
		if (!context.computeOnClient((Minecraft minecraft) -> GenderMod.available())) {
			throw new AssertionError("Female Gender Mod is installed, but the wardrobe did not find its API");
		}
		// Whether the player's settings are there in the menus already, where the wardrobe opens too.
		String inMenu = context.computeOnClient((Minecraft minecraft) -> String.valueOf(GenderMod.read(minecraft.getUser().getProfileId())));
		System.out.println("gender test: the body in the main menu: " + inMenu);

		JsonObject first = body("female", 0.7, false);
		JsonObject second = body("other", 0.2, true);
		try (Steps.LightWorld world = Steps.lightWorld(context)) {
			context.getInput().lookAt(0, 0);
			view(context, true);
			context.waitFor((Minecraft minecraft) -> GenderMod.read(minecraft.getUser().getProfileId()) != null, 20 * 30);

			context.runOnClient((Minecraft minecraft) -> {
				UUID me = minecraft.getUser().getProfileId();
				Wardrobe wardrobe = MosaicaryClient.instance().skins().wardrobe();
				long now = System.currentTimeMillis();
				wardrobe.add(Wardrobe.Entry.ofSite("aaaaaaaaaaaa", "First", "classic", null, now));
				wardrobe.add(Wardrobe.Entry.ofSite("bbbbbbbbbbbb", "Second", "classic", null, now));
				wardrobe.worn(wardrobe.get(FIRST), null, "classic", now);
				if (!GenderMod.put(me, first)) throw new AssertionError("Female Gender Mod did not take a body in the world");
			});
			context.waitTicks(20);
			expectBody(context, first, "the body put on with the first skin");
			context.takeScreenshot("gender-1-first-body");
			inTheirScreen(context, "gender-1-first-body-side");
			angled(context, "gender-1-first-body-angled");

			// The second skin, with no body kept: the first keeps the body, which stays on.
			wear(context, SECOND);
			expectKept(context, FIRST, first);
			expectBody(context, first, "the body after putting on a skin with none kept");

			// Another body with the second skin, then the first again: each skin has its own.
			boolean marked = context.computeOnClient((Minecraft minecraft) -> {
				GenderMod.put(minecraft.getUser().getProfileId(), second);
				return needsSync(minecraft);
			});
			System.out.println("gender test: marked to be sent to the server when put on: " + marked);
			if (!marked) throw new AssertionError("A body put on was not marked to be sent to the server");
			context.waitTicks(20);
			context.takeScreenshot("gender-2-second-body");
			wear(context, FIRST);
			expectKept(context, SECOND, second);
			expectBody(context, first, "the body after putting the first skin on again");
			context.waitTicks(20);
			context.takeScreenshot("gender-3-first-body-again");
			inTheirScreen(context, "gender-3-first-body-again-side");

			// The mod sends a marked body on its next tick, and the mark goes.
			boolean sent = !context.computeOnClient(GenderGameTest::needsSync);
			System.out.println("gender test: sent to the server a second later: " + sent);
			view(context, false);
		}

		// The main menu: the second skin put on there waits for a world to bring its body.
		context.waitForScreen(TitleScreen.class);
		wear(context, SECOND);
		try (Steps.LightWorld world = Steps.lightWorld(context)) {
			view(context, true);
			context.waitFor((Minecraft minecraft) -> GenderMod.read(minecraft.getUser().getProfileId()) != null, 20 * 30);
			context.waitTicks(20);
			expectBody(context, second, "the body in a world after putting the second skin on in the main menu");
			expectKept(context, FIRST, first);
			context.takeScreenshot("gender-4-second-body-from-the-menu");
			inTheirScreen(context, "gender-4-second-body-from-the-menu-side");
			view(context, false);
		}
	}

	/**
	 * The body from the side, which from the front shows little: in the mod's own screen (its key, H),
	 * whose figure of the player turns after the cursor, here held at the screen's left edge.
	 */
	private static void inTheirScreen(ClientGameTestContext context, String name) {
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() != null, 100);
		context.getInput().setCursorPos(0, context.computeOnClient((Minecraft minecraft) -> minecraft.getWindow().getHeight() / 3.0));
		context.waitTicks(20);
		context.takeScreenshot(name);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * The chest close up and at an angle, in the world: the head turned and the body, standing still,
	 * left where it was, so the camera in front of the face sees the body from aside.
	 */
	private static void angled(ClientGameTestContext context, String name) {
		context.runOnClient((Minecraft minecraft) -> minecraft.options.fov().set(20));
		context.getInput().lookAt(40, 15);
		context.waitTicks(10);
		context.takeScreenshot(name);
		context.getInput().lookAt(0, 0);
		context.runOnClient((Minecraft minecraft) -> minecraft.options.fov().set(50));
		context.waitTicks(10);
	}

	/** The player from the front and further away, without the hotbar in front of the body; or back as it was. */
	private static void view(ClientGameTestContext context, boolean onPlayer) {
		context.runOnClient((Minecraft minecraft) -> {
			minecraft.options.setCameraType(onPlayer ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
			minecraft.options.fov().set(onPlayer ? 50 : 70);
			Steps.hideGui(minecraft, onPlayer);
		});
	}

	private static boolean needsSync(Minecraft minecraft) {
		try {
			Object player = Class.forName("com.wildfire.main.WildfireGender").getMethod("getPlayerById", UUID.class)
				.invoke(null, minecraft.getUser().getProfileId());
			return player.getClass().getField("needsSync").getBoolean(player);
		} catch (ReflectiveOperationException other) {
			throw new AssertionError("The player's settings in Female Gender Mod have no sync mark: " + other);
		}
	}

	private static JsonObject body(String gender, double bust, boolean physics) {
		JsonObject body = new JsonObject();
		body.addProperty("gender", gender);
		body.addProperty("bust_size", bust);
		body.addProperty("breast_physics", physics);
		body.addProperty("breasts_cleavage", 0.05);
		return body;
	}

	/** What {@code Skins.wear} does once the skin is on. */
	private static void wear(ClientGameTestContext context, String key) {
		context.runOnClient((Minecraft minecraft) -> {
			Wardrobe wardrobe = MosaicaryClient.instance().skins().wardrobe();
			Wardrobe.Entry entry = wardrobe.get(key);
			GenderMod.wearing(wardrobe, entry, minecraft.getUser().getProfileId());
			wardrobe.worn(entry, null, entry.arms, System.currentTimeMillis());
		});
		context.waitTicks(20);
	}

	private static void expectKept(ClientGameTestContext context, String key, JsonObject wanted) {
		String kept = context.computeOnClient((Minecraft minecraft) -> {
			JsonObject body = MosaicaryClient.instance().skins().wardrobe().get(key).body;
			return body == null ? "none" : body.toString();
		});
		System.out.println("gender test: kept with " + key + ": " + kept);
		if (!contains(kept, wanted)) throw new AssertionError("Kept with " + key + ": " + kept + ", not " + wanted);
	}

	private static void expectBody(ClientGameTestContext context, JsonObject wanted, String what) {
		String now = context.computeOnClient((Minecraft minecraft) -> String.valueOf(GenderMod.read(minecraft.getUser().getProfileId())));
		System.out.println("gender test: " + what + ": " + now);
		if (!contains(now, wanted)) throw new AssertionError(what + ": " + now + ", not " + wanted);
	}

	/** Whether a body, as text, has every value of {@code wanted}; the mod adds the rest of its own. */
	private static boolean contains(String body, JsonObject wanted) {
		if (body == null || body.equals("none") || body.equals("null")) return false;
		JsonObject read = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
		for (var member : wanted.entrySet()) {
			if (!read.has(member.getKey())) return false;
			var value = read.get(member.getKey());
			var expected = member.getValue();
			if (expected.getAsJsonPrimitive().isNumber()) {
				if (Math.abs(value.getAsDouble() - expected.getAsDouble()) > 1e-3) return false;
			} else if (!value.equals(expected)) {
				return false;
			}
		}
		return true;
	}
}
