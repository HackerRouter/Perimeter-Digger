package hackerrouter.perimeterdigger.client.state;

import hackerrouter.perimeterdigger.client.config.UnloadingPointConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class UnloadFlow {
	NamedPoint point;
	List<Candidate> candidates = List.of();
	Vec3 edgePosition;
	int candidateIndex;
	int flightAttempts;
	int settleTicks;
	boolean debugOnly;

	void reset() {
		point = null;
		candidates = List.of();
		edgePosition = null;
		candidateIndex = 0;
		flightAttempts = 0;
		settleTicks = 0;
		debugOnly = false;
	}

	record NamedPoint(String name, UnloadingPointConfig point) {
	}

	record Candidate(BlockPos position, int horizontalDistanceSquared, int yDifference, double playerDistanceSquared) {
	}
}
