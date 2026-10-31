package com.mopicmp.mosaicary.gametest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.ui.ConsentScreen;
import com.mopicmp.mosaicary.client.ui.MenuSkin;
import com.mopicmp.mosaicary.client.ui.MenuSkinScreen;
import com.mopicmp.mosaicary.client.ui.SettingsScreen;
import com.mopicmp.mosaicary.client.ui.SkinFileScreen;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Every screen in every language at the narrowest common layout: a screenshot of each, and a line
 * for each widget off the screen or on top of another. Runs only with {@code -PtestLanguages},
 * as the screenshots are for reading rather than for the test to judge.
 */
public final class LanguagesGameTest implements FabricClientGameTest {

	private static final String[] LANGUAGES = {
		"de_de", "es_es", "fr_fr", "it_it", "ja_jp", "ko_kr", "nl_nl", "pl_pl", "pt_br", "ru_ru", "tr_tr", "uk_ua", "zh_cn",
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getProperty("mosaicary.testLanguages") == null) return;
		context.getInput().resizeWindow(1280, 720);
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(3);
			minecraft.resizeGui();
		});
		context.waitTicks(10);
		for (String code : LANGUAGES) {
			select(context, code);
			walk(context, code);
		}
		select(context, "en_us");
		// The open tab is remembered between screens, and the next test expects the catalogue.
		context.runOnClient(minecraft -> minecraft.gui.setScreen(
			new SkinsScreen(new TitleScreen(), MosaicaryClient.instance().skins())));
		context.waitForScreen(SkinsScreen.class);
		tab(context, "mosaicary.skins.tab.catalogue");
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(0);
			minecraft.resizeGui();
			minecraft.gui.setScreen(new TitleScreen());
		});
		context.waitForScreen(TitleScreen.class);
	}

	private static void walk(ClientGameTestContext context, String code) {
		context.runOnClient(minecraft -> minecraft.gui.setScreen(
			new SkinsScreen(new TitleScreen(), MosaicaryClient.instance().skins())));
		context.waitForScreen(SkinsScreen.class);
		tab(context, "mosaicary.skins.tab.catalogue");
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.cardsRead(), 20 * 60);
		context.waitTicks(20);
		snap(context, code, "1-catalogue");

		int[] card = context.computeOnClient(minecraft -> ((SkinsScreen) minecraft.gui.screen()).cardCenter(0));
		Steps.click(context, card[0], card[1]);
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof SkinsScreen screen && screen.openCard() != null, 100);
		context.waitTicks(20);
		snap(context, code, "2-skin");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);

		tab(context, "mosaicary.skins.tab.mine");
		context.waitTicks(10);
		snap(context, code, "3-my-skins");

		context.runOnClient(minecraft -> {
			SkinsScreen screen = (SkinsScreen) minecraft.gui.screen();
			minecraft.gui.setScreen(new SkinFileScreen(screen, screen.skins(), file -> { }));
		});
		context.waitTicks(10);
		snap(context, code, "4-import");

		context.runOnClient(minecraft -> minecraft.gui.setScreen(new SettingsScreen(new TitleScreen())));
		context.waitTicks(5);
		snap(context, code, "5-settings");

		context.runOnClient(minecraft -> minecraft.gui.setScreen(new ConsentScreen(new TitleScreen(), () -> { })));
		context.waitTicks(5);
		snap(context, code, "6-consent");

		context.runOnClient(minecraft -> minecraft.gui.setScreen(new MenuSkinScreen(new TitleScreen(), MenuSkin.Beside.MAIN)));
		context.waitTicks(5);
		snap(context, code, "7-place");
	}

	private static void select(ClientGameTestContext context, String code) {
		context.runOnClient(minecraft -> {
			minecraft.options.languageCode = code;
			minecraft.getLanguageManager().setSelected(code);
			minecraft.getLanguageManager().onResourceManagerReload(minecraft.getResourceManager());
			minecraft.resizeGui();
		});
		context.waitTicks(2);
	}

	/** Clicks a tab of the wardrobe by its words, which are its message whether or not it shows them. */
	private static void tab(ClientGameTestContext context, String key) {
		double[] at = context.computeOnClient(minecraft -> {
			String label = Component.translatable(key).getString();
			String tooltip = Component.translatable(key + ".tooltip").getString();
			for (AbstractWidget widget : Screens.getWidgets(minecraft.gui.screen())) {
				String message = widget.getMessage().getString();
				if (message.equals(label) || message.equals(tooltip)) {
					return new double[] { widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0 };
				}
			}
			throw new AssertionError("No tab " + key);
		});
		Steps.click(context, at[0], at[1]);
		context.waitTicks(5);
	}

	private static void snap(ClientGameTestContext context, String code, String name) {
		context.takeScreenshot("lang-" + code + "-" + name);
		for (String problem : context.computeOnClient(LanguagesGameTest::problems)) {
			System.out.println("LANG " + code + " " + name + ": " + problem);
		}
	}

	/** The widgets off the screen or overlapping another, and those whose words are wider than they are. */
	private static List<String> problems(Minecraft minecraft) {
		Screen screen = minecraft.gui.screen();
		List<AbstractWidget> widgets = Screens.getWidgets(screen).stream().filter(widget -> widget.visible).toList();
		List<String> said = new ArrayList<>();
		for (int i = 0; i < widgets.size(); i++) {
			AbstractWidget one = widgets.get(i);
			if (one.getX() < 0 || one.getX() + one.getWidth() > screen.width || one.getY() < 0 || one.getY() + one.getHeight() > screen.height) {
				said.add("off the screen: " + describe(one));
			}
			for (int j = i + 1; j < widgets.size(); j++) {
				AbstractWidget other = widgets.get(j);
				if (one.getX() < other.getX() + other.getWidth() && other.getX() < one.getX() + one.getWidth()
					&& one.getY() < other.getY() + other.getHeight() && other.getY() < one.getY() + one.getHeight()) {
					said.add("overlap: " + describe(one) + " / " + describe(other));
				}
			}
		}
		return said;
	}

	private static String describe(AbstractWidget widget) {
		return "'" + widget.getMessage().getString() + "' at " + widget.getX() + "," + widget.getY() + " " + widget.getWidth() + "x" + widget.getHeight();
	}
}
