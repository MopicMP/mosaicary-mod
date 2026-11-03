package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * The render state a screen is extracted into, for the catalogue's model preview, which goes in
 * as a picture-in-picture state of its own (client/ui/CatalogueScreen). Fabric API opens the
 * field on Fabric; this opens it the same way on every loader.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

	@Accessor("guiRenderState")
	GuiRenderState mosaicary$guiRenderState();
}
