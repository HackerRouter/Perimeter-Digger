package hackerrouter.perimeterdigger.client.navigation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionPositionFinderTest {
	@Test
	void acceptsAStandThatRemainsReachableAtTheBlockEdge() {
		assertTrue(InteractionPositionFinder.isSafelyWithinReach(new BlockPos(0, 64, 2), new BlockPos(0, 64, 0), 9.0));
	}

	@Test
	void rejectsAStandWhoseCenterIsReachableButFarEdgeIsNot() {
		assertFalse(InteractionPositionFinder.isSafelyWithinReach(new BlockPos(1, 64, 2), new BlockPos(0, 64, 0), 9.0));
	}
}
