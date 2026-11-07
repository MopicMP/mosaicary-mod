package com.mopicmp.mosaicary.gametest;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.compat.GenderMod;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Female Gender Mod's breasts at sizes up to and past its own limit, which mods such as Over The
 * Limits lift: with Female Gender Mod installed and 3D Skin Layers not; skipped otherwise. The
 * size is set on the mod's settings directly, as its setters refuse what is past the limit. The
 * player wears the checkered skin and is taken nearly in profile at each size.
 */
public final class GenderSizesGameTest implements FabricClientGameTest {

	private static final float[] SIZES = { 0.4f, 0.8f, 1.2f, 2.0f, 3.0f };

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("skinlayers3d") || !(loader.isModLoaded("wildfire_gender") || loader.isModLoaded("female_gender_mod"))) return;
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(40);
		try (Steps.LightWorld world = Steps.lightWorld(context)) {
			context.getInput().lookAt(0, 0);
			context.waitFor((Minecraft minecraft) -> GenderMod.read(minecraft.getUser().getProfileId()) != null, 20 * 30);
			context.runOnClient((Minecraft minecraft) -> {
				MosaicaryClient.instance().skins().showFetched(minecraft.getUser().getProfileId(), "checkered", SkinLayersGenderGameTest.checkered());
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				minecraft.options.fov().set(30);
				Steps.hideGui(minecraft, true);
				JsonObject body = new JsonObject();
				body.addProperty("gender", "female");
				body.addProperty("breast_physics", false);
				GenderMod.put(minecraft.getUser().getProfileId(), body);
			});
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(20);
			for (float size : SIZES) {
				context.runOnClient((Minecraft minecraft) -> setSize(minecraft.getUser().getProfileId(), size));
				// The size eases in over a few ticks; the body, turned, eases back to the head's way.
				context.waitTicks(40);
				context.runOnClient((Minecraft minecraft) -> {
					minecraft.player.setYBodyRot(minecraft.player.getYRot() - 75);
					minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
				});
				// Taken before a tick goes by: within a tick the body turns back to 50 degrees from the head.
				context.takeScreenshot("gender-size-" + Math.round(size * 100));
				context.runOnClient((Minecraft minecraft) -> {
					minecraft.player.setYBodyRot(minecraft.player.getYRot());
					minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
				});
			}
			context.getInput().resizeWindow(854, 480);
			context.runOnClient((Minecraft minecraft) -> {
				minecraft.player.setYBodyRot(minecraft.player.getYRot());
				minecraft.options.setCameraType(CameraType.FIRST_PERSON);
				minecraft.options.fov().set(70);
				Steps.hideGui(minecraft, false);
				MosaicaryClient.instance().skins().showOther(minecraft.getUser().getProfileId(), null, null);
			});
		}
	}

	/** The size on the mod's settings themselves: its setter refuses anything past the limit. */
	static void setSize(UUID me, float size) {
		try {
			Object player = Class.forName("com.wildfire.main.WildfireGender").getMethod("getPlayerById", UUID.class).invoke(null, me);
			for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass()) {
				try {
					java.lang.reflect.Field field = type.getDeclaredField("pBustSize");
					field.setAccessible(true);
					field.setFloat(player, size);
					return;
				} catch (NoSuchFieldException next) {
					// Its superclass.
				}
			}
			throw new AssertionError("No breast size on the player's settings");
		} catch (ReflectiveOperationException failed) {
			throw new AssertionError("Could not set the breast size: " + failed);
		}
	}
}
