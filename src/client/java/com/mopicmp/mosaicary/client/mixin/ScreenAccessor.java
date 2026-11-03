package com.mopicmp.mosaicary.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * A screen's widgets, read and added to from outside the screen (client/ui/ScreenWidgets), the
 * same way on every loader. The invoker is declared with the method's erased types: the game's
 * own takes any widget that is also drawn and narrated.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

	@Accessor("renderables")
	List<Renderable> mosaicary$renderables();

	@Invoker("addRenderableWidget")
	GuiEventListener mosaicary$addRenderableWidget(GuiEventListener widget);
}
