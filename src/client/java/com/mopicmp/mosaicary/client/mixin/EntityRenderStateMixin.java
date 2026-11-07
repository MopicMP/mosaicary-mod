//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.mopicmp.mosaicary.client.compat.GenderBreasts;
import com.mopicmp.mosaicary.client.wear.Wearing;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * The worn model's frame, carried in an entity's render state from where it is extracted to
 * where it is drawn: a field of the mod's own, so it needs nothing from the loader. The jacket
 * cut for Female Gender Mod's breasts goes the same way, from where the breasts are submitted to
 * where the player model is set up.
 */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements Wearing.Carrier, GenderBreasts.Carrier {

	@Unique
	private Wearing.Worn mosaicary$worn;

	@Unique
	private Object mosaicary$jacket;

	@Override
	public Object mosaicary$jacket() {
		return mosaicary$jacket;
	}

	@Override
	public void mosaicary$jacket(Object mesh) {
		mosaicary$jacket = mesh;
	}

	@Override
	public Wearing.Worn mosaicary$worn() {
		return mosaicary$worn;
	}

	@Override
	public void mosaicary$worn(Wearing.Worn worn) {
		mosaicary$worn = worn;
	}
}
//#endif
