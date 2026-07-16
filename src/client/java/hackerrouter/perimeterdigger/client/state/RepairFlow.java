package hackerrouter.perimeterdigger.client.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;

final class RepairFlow {
	AutomationState previousState = AutomationState.IDLE;
	Stage stage;
	BlockPos navigationTarget;
	boolean flying;
	int flightAttempts;
	int portalWaitTicks;
	List<BlockPos> portalExitCandidates = List.of();
	BlockPos portalExitOrigin;
	int portalExitSearchScans;
	BlockPos postPortalNavigationTarget;
	AutomationState postPortalNavigationState;
	List<BlockPos> furnaces = List.of();
	int furnaceIndex;
	FurnacePhase furnacePhase;
	int interactionTicks;
	BlockPos machineTakeoffPoint;
	int durabilitySnapshot;
	int stableTicks;
	Plan plan;
	boolean debugOnly;
	boolean crossDimension = true;

	void reset() {
		previousState = AutomationState.IDLE;
		stage = null;
		navigationTarget = null;
		flying = false;
		flightAttempts = 0;
		portalWaitTicks = 0;
		portalExitCandidates = List.of();
		portalExitOrigin = null;
		portalExitSearchScans = 0;
		postPortalNavigationTarget = null;
		postPortalNavigationState = null;
		furnaces = List.of();
		furnaceIndex = 0;
		furnacePhase = null;
		interactionTicks = 0;
		machineTakeoffPoint = null;
		durabilitySnapshot = 0;
		stableTicks = 0;
		plan = null;
		debugOnly = false;
		crossDimension = true;
	}

	enum Stage {
		OUTBOUND_PERIMETER_PORTAL,
		OUTBOUND_REPAIR_PORTAL,
		REPAIR_MACHINE,
		RETURN_TO_MACHINE_TAKEOFF,
		RETURN_REPAIR_PORTAL,
		RETURN_PERIMETER_PORTAL,
		RETURN_TO_MINE
	}

	enum FurnacePhase {
		PREPARING,
		OPENING,
		TAKING_OUTPUT,
		WAITING_FOR_REPAIR
	}

	record Plan(Map<Item, Integer> targetCounts, Map<Item, Integer> baselineFullCounts) {
	}
}
