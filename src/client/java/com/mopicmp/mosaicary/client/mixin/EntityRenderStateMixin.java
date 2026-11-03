package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.mopicmp.mosaicary.client.wear.Wearing;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * The worn model's frame, carried in an entity's render state from where it is extracted to
 * where it is drawn: a field of the mod's own, so it needs nothing from the loader.
 */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements Wearing.Carrier {

	@Unique
	private Wearing.Worn mosaicary$worn;

	@Override
	public Wearing.Worn mosaicary$worn() {
		return mosaicary$worn;
	}

	@Override
	public void mosaicary$worn(Wearing.Worn worn) {
		mosaicary$worn = worn;
	}
}
