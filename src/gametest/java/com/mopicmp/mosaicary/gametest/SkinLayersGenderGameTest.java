package com.mopicmp.mosaicary.gametest;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.compat.GenderMod;
import com.mopicmp.mosaicary.client.render.Textures;
import com.mopicmp.mosaicary.client.skins.Skins;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * 3D Skin Layers and Female Gender Mod together, with both installed ({@code -PtestMods} pointing
 * at a folder with both jars); skipped otherwise. The player wears a skin made here, grey with a
 * checkered outer layer on the front of the jacket, so that every cube of the three-dimensional
 * layer shows, and is taken close up, from the front and with the body turned aside, with a
 * female body and with a flat one.
 */
public final class SkinLayersGenderGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("skinlayers3d") || !(loader.isModLoaded("wildfire_gender") || loader.isModLoaded("female_gender_mod"))) return;
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(40);
		try (Steps.LightWorld world = Steps.lightWorld(context)) {
			context.getInput().lookAt(0, 0);
			context.waitFor((Minecraft minecraft) -> GenderMod.read(minecraft.getUser().getProfileId()) != null, 20 * 30);
			context.runOnClient((Minecraft minecraft) -> {
				Skins skins = MosaicaryClient.instance().skins();
				skins.showFetched(minecraft.getUser().getProfileId(), "checkered", checkered());
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				minecraft.options.fov().set(30);
				Steps.hideGui(minecraft, true);
			});
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(40);

			for (String gender : new String[] { "female", "male" }) {
				JsonObject body = new JsonObject();
				body.addProperty("gender", gender);
				body.addProperty("bust_size", 0.8);
				body.addProperty("breast_physics", false);
				context.runOnClient((Minecraft minecraft) -> GenderMod.put(minecraft.getUser().getProfileId(), body));
				context.waitTicks(20);
				context.takeScreenshot("layers-gender-" + gender + "-front");
				// The head turned, the body left where it stood: the camera before the face sees it from aside.
				context.runOnClient((Minecraft minecraft) -> {
					minecraft.player.setYBodyRot(minecraft.player.getYRot() - 45);
					minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
				});
				context.waitTicks(3);
				context.takeScreenshot("layers-gender-" + gender + "-turned");
				context.runOnClient((Minecraft minecraft) -> {
					minecraft.player.setYBodyRot(minecraft.player.getYRot());
					minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
				});
			}

			// The female body again, in profile and turned, where the jacket behind the breasts shows.
			JsonObject female = new JsonObject();
			female.addProperty("gender", "female");
			female.addProperty("bust_size", 0.8);
			female.addProperty("breast_physics", false);
			context.runOnClient((Minecraft minecraft) -> GenderMod.put(minecraft.getUser().getProfileId(), female));
			context.waitTicks(20);
			for (int turn : new int[] { 45, 90 }) {
				// Taken before a tick goes by: within a tick the body turns back to 50 degrees from the head.
				context.runOnClient((Minecraft minecraft) -> {
					minecraft.player.setYBodyRot(minecraft.player.getYRot() - turn);
					minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
				});
				context.takeScreenshot("layers-gender-female-turned-" + turn);
			}
			context.runOnClient((Minecraft minecraft) -> {
				minecraft.player.setYBodyRot(minecraft.player.getYRot());
				minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
			});

			context.getInput().resizeWindow(854, 480);
			context.runOnClient((Minecraft minecraft) -> {
				minecraft.options.setCameraType(CameraType.FIRST_PERSON);
				minecraft.options.fov().set(70);
				Steps.hideGui(minecraft, false);
				MosaicaryClient.instance().skins().showOther(minecraft.getUser().getProfileId(), null, null);
			});
		}
	}

	/**
	 * Grey, with the front of the jacket's outer layer checkered in two bright colours and the rest of
	 * the outer layer empty.
	 */
	static Skins.Texture checkered() {
		int[] pixels = new int[64 * 64];
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				boolean outer = (y < 16 && x >= 32) || (y >= 32 && y < 48 && x < 56) || (y >= 48 && (x < 16 || x >= 48));
				pixels[y * 64 + x] = outer ? 0 : 0xFF808080;
			}
		}
		// The jacket's front: 8 by 12 from (20, 36).
		for (int y = 36; y < 48; y++) {
			for (int x = 20; x < 28; x++) pixels[y * 64 + x] = ((x + y) & 1) == 0 ? 0xFF00E0FF : 0xFFFF00E0;
		}
		NativeImage image = new NativeImage(64, 64, false);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) image.setPixel(x, y, pixels[y * 64 + x]);
		}
		Identifier id = Mosaicary.id("skins/texture/test-checkered");
		Minecraft.getInstance().getTextureManager().register(id, Textures.of(() -> "Checkered test skin", image));
		return new Skins.Texture(Skins.State.READY, id, PlayerModelType.WIDE, pixels, null);
	}
}
