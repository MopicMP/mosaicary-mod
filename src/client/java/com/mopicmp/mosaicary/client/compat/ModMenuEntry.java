//#if FABRIC>=1
package com.mopicmp.mosaicary.client.compat;

import com.mopicmp.mosaicary.client.ui.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The "Configure" button of Mod Menu, and of the mods that open other mods' settings through
 * Mod Menu's interface: it opens the wardrobe's own settings screen. Loaded only when Mod Menu
 * is installed (the {@code modmenu} entry point in fabric.mod.json); the mod needs nothing from
 * it otherwise. Every setting takes effect at once, so nothing here asks for a restart.
 */
public final class ModMenuEntry implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
//#endif
