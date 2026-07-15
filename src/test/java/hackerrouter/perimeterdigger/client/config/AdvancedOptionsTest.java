package hackerrouter.perimeterdigger.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedOptionsTest {
	@Test
	void keysAreUniqueAndResolvable() {
		assertEquals(AdvancedOptions.keys().size(), AdvancedOptions.keys().stream().distinct().count());
		for (String key : AdvancedOptions.keys()) assertEquals(key, AdvancedOptions.get(key).key());
	}

	@Test
	void appliesIntegerAndDecimalValues() {
		AdvancedConfig config = new AdvancedConfig();
		AdvancedOptions.get("tool_durability_threshold").set(config, 48);
		AdvancedOptions.get("navigation_stall_timeout_seconds").set(config, 7.5);
		assertEquals(48, config.toolDurabilityThreshold);
		assertEquals(7.5, config.navigationStallTimeoutSeconds);
	}

	@Test
	void rejectsInvalidRangesAndIntegerFractions() {
		AdvancedConfig config = new AdvancedConfig();
		assertThrows(IllegalArgumentException.class,
				() -> AdvancedOptions.get("inventory_reserved_slots").set(config, 36));
		assertThrows(IllegalArgumentException.class,
				() -> AdvancedOptions.get("drop_collection_radius").set(config, 2.5));
		assertThrows(IllegalArgumentException.class,
				() -> AdvancedOptions.get("navigation_stall_timeout_seconds").set(config, 0));
	}

	@Test
	void enforcesCrossFieldOrdering() {
		AdvancedConfig config = new AdvancedConfig();
		assertThrows(IllegalArgumentException.class,
				() -> AdvancedOptions.get("food_resupply_trigger").set(config, config.foodResupplyTarget + 1));
		assertThrows(IllegalArgumentException.class,
				() -> AdvancedOptions.get("portal_exit_max_radius").set(config, config.portalExitMinRadius - 1));
		AdvancedOptions.get("portal_exit_max_radius").set(config, 12);
		AdvancedOptions.get("portal_exit_min_radius").set(config, 10);
		assertTrue(config.portalExitMinRadius <= config.portalExitMaxRadius);
	}

	@Test
	void rejectsUnknownKeys() {
		assertThrows(IllegalArgumentException.class, () -> AdvancedOptions.get("missing"));
	}
}
