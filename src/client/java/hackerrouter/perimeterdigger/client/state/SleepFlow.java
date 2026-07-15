package hackerrouter.perimeterdigger.client.state;

import net.minecraft.core.BlockPos;

final class SleepFlow {
	AutomationState previousState = AutomationState.IDLE;
	BlockPos bed;
	BlockPos stand;
	boolean flying;
	int flightAttempts;
	int interactionTicks;
	boolean entered;
	boolean debugOnly;

	void reset() {
		previousState = AutomationState.IDLE;
		bed = null;
		stand = null;
		flying = false;
		flightAttempts = 0;
		interactionTicks = 0;
		entered = false;
		debugOnly = false;
	}
}
