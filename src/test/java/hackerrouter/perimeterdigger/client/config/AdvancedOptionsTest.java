/*
 * This file is part of the Perimeter Digger project, licensed under the
 * GNU General Public License v3.0 or later.
 *
 * Copyright (C) 2026  HackerRouter
 *
 * Perimeter Digger is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Perimeter Digger is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Perimeter Digger. If not, see <https://www.gnu.org/licenses/>.
 */

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
