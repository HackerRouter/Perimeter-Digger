package hackerrouter.perimeterdigger.client.state;

import hackerrouter.perimeterdigger.client.config.PositionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Map;

final class SupplyFlow {
	AutomationState previousState = AutomationState.IDLE;
	Kind kind;
	Phase phase;
	PositionConfig point;
	BlockPos stand;
	boolean flying;
	int flightAttempts;
	int interactionTicks;
	boolean debugOnly;
	DurabilityPlan durabilityPlan;

	void reset() {
		previousState = AutomationState.IDLE;
		kind = null;
		phase = null;
		point = null;
		stand = null;
		flying = false;
		flightAttempts = 0;
		interactionTicks = 0;
		debugOnly = false;
		durabilityPlan = null;
	}

	enum Kind {
		CONSUMABLES("consumables"),
		DURABILITY("durability");

		final String displayName;

		Kind(String displayName) {
			this.displayName = displayName;
		}
	}

	enum Phase {
		PREPARING,
		OPENING,
		TRANSFERRING,
		FINALIZING
	}

	record DurabilityPlan(Map<Item, Integer> targetHealthyCounts, Item chestItem, Item offhandItem, Item selectedItem, int selectedSlot) {
	}
}
