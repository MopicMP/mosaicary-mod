package com.mopicmp.mosaicary.client.ui;

import java.util.ArrayList;
import java.util.List;

import com.mopicmp.mosaicary.client.mixin.ScreenAccessor;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

/** The widgets of another screen: the pause menu's buttons, the figure beside the menus. */
public final class ScreenWidgets {

	private ScreenWidgets() { }

	/** The screen's widgets as they are now; a copy, so adding while going through it is safe. */
	public static List<AbstractWidget> of(Screen screen) {
		List<AbstractWidget> widgets = new ArrayList<>();
		for (Renderable one : ((ScreenAccessor) screen).mosaicary$renderables()) {
			if (one instanceof AbstractWidget widget) widgets.add(widget);
		}
		return widgets;
	}

	/** Adds a widget to the screen, drawn, clickable and narrated like the screen's own. */
	public static void add(Screen screen, AbstractWidget widget) {
		((ScreenAccessor) screen).mosaicary$addRenderableWidget(widget);
	}
}
