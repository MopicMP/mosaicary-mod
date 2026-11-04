package com.mopicmp.mosaicary.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.ui.MenuSkin;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The player's skin beside the main menu, with real input.
 *
 * Its size at every GUI scale (screenshots), the head following the pointer to either side,
 * the same after a click has opened the wardrobe and Escape has come back to the menu, and the
 * setting that hides it, switched while the menu is open.
 */
public final class MenuSkinGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		context.waitForScreen(TitleScreen.class);
		// The menu fades in for two seconds, and the figure with it.
		context.waitTicks(60);

		for (int scale : new int[] { 1, 2, 3, 4 }) {
			setScale(context, scale);
			context.takeScreenshot("menu-skin-1-scale-" + scale);
		}
		setScale(context, 2);

		float[] before = lookBothWays(context, "2-before");
		// Which way is which was read off the screenshots: a positive turn shows the face to the
		// left of the screen. The pointer on the left must give it, on the right the opposite.
		if (!(before[0] > 0 && before[1] < 0)) {
			throw new AssertionError("The head does not turn towards the pointer: left " + before[0] + ", right " + before[1]);
		}
		// A click opens the wardrobe; Escape comes back to the menu.
		MenuSkin figure = figure(context);
		Steps.click(context, figure);
		context.waitForScreen(SkinsScreen.class);
		context.waitTicks(20);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(10);
		float[] after = lookBothWays(context, "3-after-wardrobe");

		// Again, this time through a skin's 3D preview, and out with the close button.
		Steps.click(context, figure(context));
		context.waitForScreen(SkinsScreen.class);
		context.waitFor((Minecraft minecraft) -> !MosaicaryClient.instance().skins().shelf(SkinsScreen.query()).cards.isEmpty(), 20 * 60);
		context.waitTicks(20);
		int[] card = context.computeOnClient((Minecraft minecraft) -> ((SkinsScreen) minecraft.gui.screen()).cardCenter(0));
		Steps.click(context, card[0], card[1]);
		context.waitFor((Minecraft minecraft) -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.openCard() != null, 100);
		context.waitTicks(60);
		context.takeScreenshot("menu-skin-3b-wardrobe-preview");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		double[] close = context.computeOnClient((Minecraft minecraft) -> {
			var screen = minecraft.gui.screen();
			for (var widget : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)) {
				if (widget.getX() + widget.getWidth() >= screen.width - 8 && widget.getY() <= 8) {
					return new double[] { widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0 };
				}
			}
			return null;
		});
		if (close == null) throw new AssertionError("No close button in the wardrobe");
		Steps.click(context, close[0], close[1]);
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(10);
		float[] again = lookBothWays(context, "3c-after-preview");
		System.out.printf("menu skin head yaw after preview: left %.2f right %.2f%n", again[0], again[1]);
		if (Math.signum(before[0]) != Math.signum(again[0]) || Math.signum(before[1]) != Math.signum(again[1])) {
			throw new AssertionError("The head turns the other way after the wardrobe's preview");
		}
		System.out.printf("menu skin head yaw: before left %.2f right %.2f, after left %.2f right %.2f%n",
			before[0], before[1], after[0], after[1]);
		if (Math.signum(before[0]) != Math.signum(after[0]) || Math.signum(before[1]) != Math.signum(after[1])) {
			throw new AssertionError("The head turns the other way after the wardrobe");
		}

		// Turned with its back to the viewer by a real drag, the face still turns towards the pointer,
		// over the shoulder on that side: the head used to turn as if the figure faced the viewer, and
		// looked away from the pointer. And as the pointer crosses behind the figure the head swings
		// across a little each frame: it used to jump from one shoulder to the other in one.
		float[] turned = lookTurned(context);
		System.out.printf("menu skin turned round, face: left %.2f right %.2f; the head's largest step %.2f%n",
			turned[0], turned[1], turned[2]);
		if (!(turned[0] > 0 && turned[1] < 0)) {
			throw new AssertionError("Turned round, the face does not turn towards the pointer: left " + turned[0] + ", right " + turned[1]);
		}
		if (turned[2] > 0.4f) {
			throw new AssertionError("Turned round, the head jumps by " + turned[2] + " as the pointer crosses behind it");
		}

		// Switched off while the menu is open: gone at once; on again: back.
		context.runOnClient((Minecraft minecraft) -> {
			MosaicaryClient.instance().settings().menuSkin = false;
			MosaicaryClient.instance().saveSettings();
		});
		context.waitTicks(5);
		context.takeScreenshot("menu-skin-4-switched-off");
		context.runOnClient((Minecraft minecraft) -> {
			MosaicaryClient.instance().settings().menuSkin = true;
			MosaicaryClient.instance().saveSettings();
		});
		context.waitTicks(5);
		context.takeScreenshot("menu-skin-5-switched-on");
		hoverOver(context);
	}

	private static void setScale(ClientGameTestContext context, int scale) {
		context.runOnClient((Minecraft minecraft) -> {
			minecraft.options.guiScale().set(scale);
			minecraft.resizeGui();
		});
		context.waitTicks(10);
	}

	/** The words on the pointer over the figure, which go on to further lines rather than across the screen. */
	private static void hoverOver(ClientGameTestContext context) {
		MenuSkin figure = figure(context);
		double scale = Steps.scale(context);
		context.getInput().setCursorPos((figure.getX() + figure.getWidth() / 2.0) * scale, (figure.getY() + figure.getHeight() / 2.0) * scale);
		context.waitTicks(5);
		context.takeScreenshot("menu-skin-6-tooltip");
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
	}

	private static MenuSkin figure(ClientGameTestContext context) {
		MenuSkin figure = context.computeOnClient((Minecraft minecraft) -> MenuSkin.on(minecraft.gui.screen()));
		if (figure == null) throw new AssertionError("No figure beside the main menu");
		return figure;
	}

	/**
	 * The figure dragged round until its back is to the viewer, then the pointer taken from its left
	 * to its right in 24 steps at the height of its eyes, crossing behind it: where the face points at
	 * the first step and the last, and the largest change of the head's turn from one step to the next.
	 * The drag is done as a hand would: the pointer there first, the button pressed a few frames
	 * later, then moved a frame at a time.
	 */
	private static float[] lookTurned(ClientGameTestContext context) {
		MenuSkin figure = figure(context);
		double scale = Steps.scale(context);
		context.getInput().setCursorPos((figure.getX() + figure.getWidth() / 2.0) * scale, (figure.getY() + figure.getHeight() / 2.0) * scale);
		context.waitTicks(3);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		// 2.5 degrees a point, on top of the 20 the figure stands turned: 64 points put its back to the viewer.
		for (int i = 0; i < 8; i++) {
			context.getInput().moveCursor(8 * scale, 0);
			context.waitTick();
		}
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		if (context.computeOnClient((Minecraft minecraft) -> minecraft.gui.screen()) instanceof SkinsScreen) {
			throw new AssertionError("Dragging the figure opened the wardrobe");
		}
		double eyes = figure.getY() + figure.getHeight() * 0.12;
		double from = figure.getX() - 150;
		double to = context.computeOnClient((Minecraft minecraft) -> (double) minecraft.gui.screen().width) - 4;
		int steps = 24;
		float first = 0;
		float last = 0;
		float before = Float.NaN;
		float largest = 0;
		for (int i = 0; i <= steps; i++) {
			context.getInput().setCursorPos((from + (to - from) * i / steps) * scale, eyes * scale);
			context.waitTicks(2);
			float head = context.computeOnClient((Minecraft minecraft) -> MenuSkin.on(minecraft.gui.screen()).headYaw());
			float face = context.computeOnClient((Minecraft minecraft) -> MenuSkin.on(minecraft.gui.screen()).faceYaw());
			if (!Float.isNaN(before)) largest = Math.max(largest, Math.abs(head - before));
			before = head;
			if (i == 0) {
				first = face;
				context.takeScreenshot("menu-skin-7-turned-left");
			}
			if (i == steps / 2) context.takeScreenshot("menu-skin-7-turned-behind");
			if (i == steps) {
				last = face;
				context.takeScreenshot("menu-skin-7-turned-right");
			}
		}
		return new float[] { first, last, largest };
	}

	/** The pointer to the left of the figure, then to the right; the head's turn each time. */
	private static float[] lookBothWays(ClientGameTestContext context, String name) {
		MenuSkin figure = figure(context);
		double scale = Steps.scale(context);
		double eyes = figure.getY() + figure.getHeight() * 0.12;
		context.getInput().setCursorPos((figure.getX() - 150) * scale, eyes * scale);
		context.waitTicks(5);
		context.takeScreenshot("menu-skin-" + name + "-left");
		float left = context.computeOnClient((Minecraft minecraft) -> MenuSkin.on(minecraft.gui.screen()).headYaw());
		context.getInput().setCursorPos((figure.getRight() + 40) * scale, eyes * scale);
		context.waitTicks(5);
		context.takeScreenshot("menu-skin-" + name + "-right");
		float right = context.computeOnClient((Minecraft minecraft) -> MenuSkin.on(minecraft.gui.screen()).headYaw());
		return new float[] { left, right };
	}
}
