package com.mopicmp.mosaicary.gametest;

import java.lang.reflect.Field;

import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The preview of a skin walking, seen from the side and close up: the legs cross each other there,
 * and their trousers, with or without 3D Skin Layers ({@code -PtestMods}), must not flicker where
 * they overlap. A run of screenshots a few frames apart, to be looked at.
 */
public final class WalkSideGameTest implements FabricClientGameTest {

	/** How many pixels of the trousers' outer layer a skin needs: about half of it. */
	private static final int TROUSER_PIXELS = 180;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Steps.skipped(this)) return;
		context.getInput().resizeWindow(1280, 720);
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);
		// With -PtestSearch, the first skin the catalogue finds for those words; otherwise one with much on its trousers.
		String search = System.getProperty("mosaicary.testSearch");
		if (search != null) SkinsScreen.query(SkinsScreen.query().withWords(search));
		context.runOnClient((Minecraft minecraft) -> {
			minecraft.gui.setScreen(new SkinsScreen(new TitleScreen(), MosaicaryClient.instance().skins()));
		});
		context.waitForScreen(SkinsScreen.class);
		context.waitFor((Minecraft minecraft) -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
		context.waitTicks(20);
		int cards = context.computeOnClient((Minecraft minecraft) -> MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.size());
		boolean found = false;
		for (int i = 0; i < Math.min(cards, 24) && !found; i++) {
			int index = i;
			int[] center = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).cardCenter(index));
			Steps.click(context, center[0], center[1]);
			context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.openCard() != null, 100);
			context.waitFor((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				return MosaicaryClient.instance().skins().texture(screen.openCard()).state() != Skins.State.LOADING;
			}, 20 * 60);
			int trousers = context.computeOnClient((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				return trouserPixels(MosaicaryClient.instance().skins().texture(screen.openCard()).pixels());
			});
			if (search != null || trousers >= TROUSER_PIXELS) {
				found = true;
				System.out.println("walk side test: card " + index + " has " + trousers + " pixels on its trousers");
			} else {
				context.runOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).closeDetail());
				context.waitTicks(10);
			}
		}
		if (!found) throw new AssertionError("No skin among the first cards has much on its trousers");

		// Walking, from the side, the legs close up.
		float[][] views = { { -10, 70 }, { -10, 110 } };
		for (int view = 0; view < views.length; view++) {
			float[] turn = views[view];
			context.runOnClient((Minecraft minecraft) -> {
				SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
				screen.setView(turn[0], turn[1]);
				set(screen, "walking", true);
				set(screen, "zoom", 3f);
				set(screen, "panY", 0f);
			});
			context.waitTicks(20);
			// At set moments of the walk, the same in every run, so that runs can be compared frame by frame.
			for (int frame = 0; frame < 10; frame++) {
				float at = frame * 0.06f;
				context.runOnClient((Minecraft minecraft) -> SkinsScreen.stoppedAt = at);
				context.waitTicks(2);
				context.takeScreenshot("walk-side-" + view + "-" + frame);
			}
			System.out.println("walk side test: " + context.computeOnClient((Minecraft minecraft) ->
				com.mopicmp.mosaicary.client.compat.SkinLayers3d.hung()) + " outer parts with a mesh");
		}
		context.runOnClient((Minecraft minecraft) -> {
			SkinsScreen.stoppedAt = null;
			SkinsScreen.query(SkinsScreen.query().withWords(""));
			set(minecraft.gui.screen(), "walking", false);
			minecraft.gui.setScreen(new TitleScreen());
		});
		context.waitForScreen(TitleScreen.class);
	}

	private static void set(Object screen, String name, Object value) {
		try {
			Field field = SkinsScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(screen, value);
		} catch (ReflectiveOperationException failed) {
			throw new AssertionError("No field " + name + " on the skins screen", failed);
		}
	}

	/** The drawn pixels of the trousers' outer layer, both legs. */
	private static int trouserPixels(int[] pixels) {
		if (pixels == null) return 0;
		int[][] boxes = { { 0, 32, 16, 48 }, { 0, 48, 16, 64 } };
		int drawn = 0;
		for (int[] box : boxes) {
			for (int y = box[1]; y < box[3]; y++) {
				for (int x = box[0]; x < box[2]; x++) if ((pixels[y * 64 + x] >>> 24) != 0) drawn++;
			}
		}
		return drawn;
	}
}
