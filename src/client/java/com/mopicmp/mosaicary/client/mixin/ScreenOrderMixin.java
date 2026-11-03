//#if MC<12111
//$$ package com.mopicmp.mosaicary.client.mixin;
//$$
//$$ import java.util.List;
//$$
//$$ import org.spongepowered.asm.mixin.Final;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ import net.minecraft.client.gui.GuiGraphics;
//$$ import net.minecraft.client.gui.components.Renderable;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$
//$$ /**
//$$  * Draws the mod's screens in the order 1.21.11 draws every screen: the background, then the
//$$  * screen, whose call to {@code Screen.render} adds the buttons. In 1.21.1 {@code Screen.render}
//$$  * draws the background itself, blurred, under the buttons but over all the screen drew before
//$$  * calling it, and the mod's screens draw everything before. Other screens are left as they are.
//$$  */
//$$ @Mixin(Screen.class)
//$$ public abstract class ScreenOrderMixin {
//$$
//$$ 	@Shadow
//$$ 	@Final
//$$ 	private List<Renderable> renderables;
//$$
//$$ 	@Shadow
//$$ 	public abstract void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
//$$
//$$ 	@Inject(method = "renderWithTooltip", at = @At("HEAD"))
//$$ 	private void mosaicary$backgroundFirst(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
//$$ 		if (ours()) renderBackground(graphics, mouseX, mouseY, partialTick);
//$$ 	}
//$$
//$$ 	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
//$$ 	private void mosaicary$buttonsOnly(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
//$$ 		if (!ours()) return;
//$$ 		for (Renderable one : renderables) one.render(graphics, mouseX, mouseY, partialTick);
//$$ 		info.cancel();
//$$ 	}
//$$
//$$ 	private boolean ours() {
//$$ 		return getClass().getName().startsWith("com.mopicmp.mosaicary.");
//$$ 	}
//$$ }
//#endif
