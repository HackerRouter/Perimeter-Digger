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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdvancedOptions {
	private static final List<AdvancedOption> OPTIONS = List.of(
			option("tool_durability_threshold", "Durability", "tool threshold", a -> a.toolDurabilityThreshold,
					(a, key, value) -> a.toolDurabilityThreshold = nonNegativeInteger(key, value)),
			option("elytra_durability_threshold", "Durability", "elytra threshold", a -> a.elytraDurabilityThreshold,
					(a, key, value) -> a.elytraDurabilityThreshold = nonNegativeInteger(key, value)),
			option("emergency_flight_durability_threshold", "Durability", "emergency flight threshold", a -> a.emergencyFlightDurabilityThreshold,
					(a, key, value) -> a.emergencyFlightDurabilityThreshold = nonNegativeInteger(key, value)),
			option("food_level_threshold", "Consumables", "food level threshold", a -> a.foodLevelThreshold,
					(a, key, value) -> a.foodLevelThreshold = rangedInteger(key, value, 0, 20)),
			option("health_eating_threshold", "Consumables", "health eating threshold", a -> a.healthEatingThreshold,
					(a, key, value) -> a.healthEatingThreshold = ranged(key, value, 0.0, 20.0)),
			option("food_resupply_trigger", "Consumables", "food trigger", a -> a.foodResupplyTrigger, (a, key, value) -> {
				int result = nonNegativeInteger(key, value);
				if (result > a.foodResupplyTarget) throw new IllegalArgumentException(key + " cannot exceed food_resupply_target.");
				a.foodResupplyTrigger = result;
			}),
			option("food_resupply_target", "Consumables", "food target", a -> a.foodResupplyTarget, (a, key, value) -> {
				int result = nonNegativeInteger(key, value);
				if (result < a.foodResupplyTrigger) throw new IllegalArgumentException(key + " cannot be lower than food_resupply_trigger.");
				a.foodResupplyTarget = result;
			}),
			option("firework_resupply_trigger", "Consumables", "firework trigger", a -> a.fireworkResupplyTrigger, (a, key, value) -> {
				int result = nonNegativeInteger(key, value);
				if (result > a.fireworkResupplyTarget) throw new IllegalArgumentException(key + " cannot exceed firework_resupply_target.");
				a.fireworkResupplyTrigger = result;
			}),
			option("firework_resupply_target", "Consumables", "firework target", a -> a.fireworkResupplyTarget, (a, key, value) -> {
				int result = nonNegativeInteger(key, value);
				if (result < a.fireworkResupplyTrigger) throw new IllegalArgumentException(key + " cannot be lower than firework_resupply_trigger.");
				a.fireworkResupplyTarget = result;
			}),
			option("drop_collection_radius", "Mining and unloading", "drop radius", a -> a.dropCollectionRadius,
					(a, key, value) -> a.dropCollectionRadius = positiveInteger(key, value)),
			option("drop_collection_stable_seconds", "Mining and unloading", "drop stable seconds", a -> a.dropCollectionStableSeconds,
					(a, key, value) -> a.dropCollectionStableSeconds = positive(key, value)),
			option("inventory_reserved_slots", "Mining and unloading", "reserved slots", a -> a.inventoryReservedSlots,
					(a, key, value) -> a.inventoryReservedSlots = rangedInteger(key, value, 0, 35)),
			option("mining_blocks_per_empty_slot", "Mining and unloading", "blocks per empty slot", a -> a.miningBlocksPerEmptySlot,
					(a, key, value) -> a.miningBlocksPerEmptySlot = positiveInteger(key, value)),
			option("unload_landing_search_radius", "Mining and unloading", "unload search radius", a -> a.unloadLandingSearchRadius,
					(a, key, value) -> a.unloadLandingSearchRadius = positiveInteger(key, value)),
			option("unload_edge_inset", "Mining and unloading", "unload edge inset", a -> a.unloadEdgeInset,
					(a, key, value) -> a.unloadEdgeInset = ranged(key, value, 0.0, 0.3)),
			option("elytra_navigation_min_distance", "Navigation", "elytra minimum distance", a -> a.elytraNavigationMinDistance,
					(a, key, value) -> a.elytraNavigationMinDistance = nonNegativeInteger(key, value)),
			option("navigation_stall_timeout_seconds", "Navigation", "stall timeout seconds", a -> a.navigationStallTimeoutSeconds,
					(a, key, value) -> a.navigationStallTimeoutSeconds = positive(key, value)),
			option("navigation_retry_count", "Navigation", "retry count", a -> a.navigationRetryCount,
					(a, key, value) -> a.navigationRetryCount = nonNegativeInteger(key, value)),
			option("flight_retry_count", "Navigation", "flight retry count", a -> a.flightRetryCount,
					(a, key, value) -> a.flightRetryCount = positiveInteger(key, value)),
			option("portal_transition_cost", "Navigation", "portal transition cost", a -> a.portalTransitionCost,
					(a, key, value) -> a.portalTransitionCost = nonNegative(key, value)),
			option("portal_transition_timeout_seconds", "Navigation", "portal transition timeout seconds", a -> a.portalTransitionTimeoutSeconds,
					(a, key, value) -> a.portalTransitionTimeoutSeconds = positive(key, value)),
			option("portal_exit_timeout_seconds", "Portal exit", "timeout seconds", a -> a.portalExitTimeoutSeconds,
					(a, key, value) -> a.portalExitTimeoutSeconds = positive(key, value)),
			option("portal_exit_min_radius", "Portal exit", "minimum radius", a -> a.portalExitMinRadius, (a, key, value) -> {
				int result = positiveInteger(key, value);
				if (result > a.portalExitMaxRadius) throw new IllegalArgumentException(key + " cannot exceed portal_exit_max_radius.");
				a.portalExitMinRadius = result;
			}),
			option("portal_exit_max_radius", "Portal exit", "maximum radius", a -> a.portalExitMaxRadius, (a, key, value) -> {
				int result = positiveInteger(key, value);
				if (result < a.portalExitMinRadius) throw new IllegalArgumentException(key + " cannot be lower than portal_exit_min_radius.");
				a.portalExitMaxRadius = result;
			}),
			option("portal_exit_vertical_radius", "Portal exit", "vertical radius", a -> a.portalExitVerticalRadius,
					(a, key, value) -> a.portalExitVerticalRadius = nonNegativeInteger(key, value)),
			option("repair_experience_stable_seconds", "Interaction", "repair experience stable seconds", a -> a.repairExperienceStableSeconds,
					(a, key, value) -> a.repairExperienceStableSeconds = positive(key, value)),
			option("supply_interaction_timeout_seconds", "Interaction", "supply timeout seconds", a -> a.supplyInteractionTimeoutSeconds,
					(a, key, value) -> a.supplyInteractionTimeoutSeconds = positive(key, value)),
			option("furnace_interaction_timeout_seconds", "Interaction", "furnace timeout seconds", a -> a.furnaceInteractionTimeoutSeconds,
					(a, key, value) -> a.furnaceInteractionTimeoutSeconds = positive(key, value))
	);
	private static final Map<String, AdvancedOption> BY_KEY = index();

	private AdvancedOptions() {
	}

	public static List<AdvancedOption> all() {
		return OPTIONS;
	}

	public static List<String> keys() {
		return OPTIONS.stream().map(AdvancedOption::key).toList();
	}

	public static AdvancedOption get(String key) {
		AdvancedOption option = BY_KEY.get(key);
		if (option == null) throw new IllegalArgumentException("Unknown advanced configuration key: " + key + ".");
		return option;
	}

	private static AdvancedOption option(String key, String group, String label,
			java.util.function.Function<AdvancedConfig, Number> getter, ConfigSetter setter) {
		return new AdvancedOption(key, group, label, getter, (config, value) -> setter.set(config, key, value));
	}

	private static Map<String, AdvancedOption> index() {
		Map<String, AdvancedOption> result = new LinkedHashMap<>();
		for (AdvancedOption option : OPTIONS) {
			if (result.put(option.key(), option) != null) throw new IllegalStateException("Duplicate advanced configuration key: " + option.key());
		}
		return Map.copyOf(result);
	}

	private static int nonNegativeInteger(String key, double value) {
		return rangedInteger(key, value, 0, Integer.MAX_VALUE);
	}

	private static int positiveInteger(String key, double value) {
		return rangedInteger(key, value, 1, Integer.MAX_VALUE);
	}

	private static int rangedInteger(String key, double value, int minimum, int maximum) {
		if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(key + " must be an integer from " + minimum + " to " + maximum + ".");
		}
		return (int) value;
	}

	private static double positive(String key, double value) {
		if (!Double.isFinite(value) || value <= 0.0) throw new IllegalArgumentException(key + " must be a positive finite number.");
		return value;
	}

	private static double nonNegative(String key, double value) {
		if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(key + " must be a non-negative finite number.");
		return value;
	}

	private static double ranged(String key, double value, double minimum, double maximum) {
		if (!Double.isFinite(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(key + " must be from " + minimum + " to " + maximum + ".");
		}
		return value;
	}

	@FunctionalInterface
	private interface ConfigSetter {
		void set(AdvancedConfig config, String key, double value);
	}
}
