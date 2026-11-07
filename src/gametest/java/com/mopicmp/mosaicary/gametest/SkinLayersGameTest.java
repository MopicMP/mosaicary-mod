package com.mopicmp.mosaicary.gametest;

import java.util.UUID;

import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.compat.SkinLayers3d;
import com.mopicmp.mosaicary.client.skins.SkinCard;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

/**
 * The skin's outer layer in three dimensions with 3D Skin Layers installed ({@code -PtestMods}
 * pointing at a folder with its jar); skipped without it. On the wardrobe's preview and the figure
 * beside the main menu, which the mod draws itself and hangs the other mod's meshes on: all six
 * outer parts given one. On the player in the world wearing a skin put on through the wardrobe:
 * the other mod builds its layer from the very texture the wardrobe gave the game. Screenshots of
 * each, the preview from the front and turned.
 */
public final class SkinLayersGameTest implements FabricClientGameTest {

	/** How many pixels of the outer layer a skin needs to show it: about a fifth of it. */
	private static final int OUTER_PIXELS = 300;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		if (!FabricLoader.getInstance().isModLoaded("skinlayers3d")) return;
		context.getInput().resizeWindow(1280, 720);
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);
		if (!context.computeOnClient((Minecraft minecraft) -> SkinLayers3d.available())) {
			throw new AssertionError("3D Skin Layers is installed, but the wardrobe did not find its API");
		}

		// The wardrobe: a skin with much on its outer layer, among the first cards.
		context.runOnClient((Minecraft minecraft) -> {
			minecraft.gui.setScreen(new SkinsScreen(new TitleScreen(), MosaicaryClient.instance().skins()));
		});
		context.waitForScreen(SkinsScreen.class);
		context.waitFor((Minecraft minecraft) -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
		context.waitTicks(20);
		int cards = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.size());
		SkinCard chosen = null;
		for (int i = 0; i < Math.min(cards, 24) && chosen == null; i++) {
			int index = i;
			int[] center = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).cardCenter(index));
			Steps.click(context, center[0], center[1]);
			context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.openCard() != null, 100);
			context.waitFor((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				return MosaicaryClient.instance().skins().texture(screen.openCard()).state() != Skins.State.LOADING;
			}, 20 * 60);
			int outer = context.computeOnClient((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				return outerPixels(MosaicaryClient.instance().skins().texture(screen.openCard()).pixels());
			});
			if (outer >= OUTER_PIXELS) {
				chosen = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).openCard());
				System.out.println("skin layers test: card " + index + " has " + outer + " pixels on its outer layer");
			} else {
				context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).closeDetail());
				context.waitTicks(10);
			}
		}
		if (chosen == null) throw new AssertionError("No skin among the first cards has much on its outer layer");
		context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).setView(-5, 0));
		context.waitTicks(20);
		expectAllHung(context, "the wardrobe's preview");
		context.takeScreenshot("skin-layers-1-wardrobe-front");
		context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).setView(-15, 60));
		context.waitTicks(20);
		context.takeScreenshot("skin-layers-2-wardrobe-turned");

		// The preview's own switch: flat after a click, in three dimensions again after another.
		for (boolean on : new boolean[] { false, true }) {
			Steps.click(context, context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).layersButton()));
			context.waitTicks(10);
			int hung = context.computeOnClient((Minecraft minecraft) -> SkinLayers3d.hung());
			boolean setting = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().settings().skinLayersOnFigures);
			System.out.println("skin layers test: the preview's switch " + (on ? "on" : "off") + ": " + hung + " outer parts with a mesh");
			if (setting != on || (on ? hung != 6 : hung != 0)) {
				throw new AssertionError("The preview's switch for 3D Skin Layers left " + hung + " parts with a mesh, setting " + setting);
			}
			if (!on) context.takeScreenshot("skin-layers-2b-wardrobe-flat");
		}

		// In the world, the same skin put on: 3D Skin Layers builds its layer from the wardrobe's texture.
		SkinCard wearing = chosen;
		context.runOnClient((Minecraft minecraft) -> { minecraft.gui.setScreen(new TitleScreen()); });
		try (Steps.LightWorld world = Steps.lightWorld(context)) {
			context.getInput().lookAt(0, 0);
			context.runOnClient((Minecraft minecraft) -> {
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				minecraft.options.fov().set(30);
				Skins skins = MosaicaryClient.instance().skins();
				skins.showFetched(minecraft.getUser().getProfileId(), "layers", skins.texture(wearing));
			});
			context.waitTicks(40);
			context.takeScreenshot("skin-layers-3-world-front");
			String built = context.computeOnClient((Minecraft minecraft) -> {
				UUID me = minecraft.getUser().getProfileId();
				Identifier ours = MosaicaryClient.instance().skins().shownOn(me).texture();
				try {
					Object player = minecraft.player;
					Object theirs = player.getClass().getMethod("getCurrentSkin").invoke(player);
					Object head = player.getClass().getMethod("getHeadMesh").invoke(player);
					if (!ours.equals(theirs)) return "built from " + theirs + ", not from the wardrobe's " + ours;
					return head == null ? "no head mesh for " + ours : null;
				} catch (ReflectiveOperationException failed) {
					return "the player has no 3D Skin Layers data: " + failed;
				}
			});
			System.out.println("skin layers test: in the world " + (built == null ? "built from the wardrobe's texture" : built));
			if (built != null) throw new AssertionError("3D Skin Layers in the world: " + built);
			context.runOnClient((Minecraft minecraft) -> {
				minecraft.options.setCameraType(CameraType.FIRST_PERSON);
				minecraft.options.fov().set(70);
			});
		}

		// The figure beside the main menu, wearing it still.
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(40);
		expectAllHung(context, "the figure beside the main menu");
		context.takeScreenshot("skin-layers-4-menu-figure");
		context.runOnClient((Minecraft minecraft) -> MosaicaryClient.instance().skins().showOther(minecraft.getUser().getProfileId(), null, null));
	}

	private static void expectAllHung(ClientGameTestContext context, String where) {
		int hung = context.computeOnClient((Minecraft minecraft) -> SkinLayers3d.hung());
		System.out.println("skin layers test: " + hung + " outer parts made three-dimensional on " + where);
		if (hung != 6) throw new AssertionError(hung + " of 6 outer parts made three-dimensional on " + where);
	}

	/** The drawn pixels of a skin's outer layer: the hat, the jacket, the sleeves and the trousers. */
	private static int outerPixels(int[] pixels) {
		if (pixels == null) return 0;
		int[][] boxes = { { 32, 0, 64, 16 }, { 0, 32, 56, 48 }, { 0, 48, 16, 64 }, { 48, 48, 64, 64 } };
		int drawn = 0;
		for (int[] box : boxes) {
			for (int y = box[1]; y < box[3]; y++) {
				for (int x = box[0]; x < box[2]; x++) if ((pixels[y * 64 + x] >>> 24) != 0) drawn++;
			}
		}
		return drawn;
	}
}
