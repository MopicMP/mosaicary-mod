package com.mopicmp.mosaicary.client.wear;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mopicmp.mosaicary.client.site.ModelCard;

/**
 * Models worn as the relay needs them: the local player's model, its roles and size to send, the
 * models to show on others and the catalogue's cards. Wearing is the one implementation; the game
 * versions the model catalogue is not built for have none, and the relay carries skins alone.
 */
public interface WornModels {

	/** The sizes a model can be picked at, over its own size. */
	double SCALE_MIN = 0.1;
	double SCALE_MAX = 10;

	/** Told whenever the local player's model or its roles change, to pass it on. */
	interface Listener {
		void changed(ModelCard worn, Map<String, String> roles);
	}

	/** A size kept within what can be picked, to a thousandth, as the site keeps it. */
	static double cleanScale(double scale) {
		if (!Double.isFinite(scale)) return 1;
		return Math.round(Math.max(SCALE_MIN, Math.min(SCALE_MAX, scale)) * 1000) / 1000.0;
	}

	void listen(Listener listener);

	/** The model the local player wears, or null. */
	ModelCard worn();

	Map<String, String> rolesToSend(ModelCard card);

	/** The size the local player picked for a model, over the model's own size. */
	double scaleFor(ModelCard card);

	/** Shows a model on another player, or takes it off with a null card. */
	void setOther(UUID player, ModelCard card, Map<String, String> roles, double scale);

	/** The models of the catalogue's list, as far as it has been read. */
	List<ModelCard> cards();
}
