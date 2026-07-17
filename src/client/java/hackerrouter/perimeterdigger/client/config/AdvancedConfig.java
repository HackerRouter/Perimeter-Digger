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

public final class AdvancedConfig {
	public int toolDurabilityThreshold = 32;
	public int elytraDurabilityThreshold = 32;
	public int emergencyFlightDurabilityThreshold = 5;
	public int foodLevelThreshold = 14;
	public double healthEatingThreshold = 16.0;
	public int foodResupplyTrigger = 1;
	public int foodResupplyTarget = 64;
	public int fireworkResupplyTrigger = 10;
	public int fireworkResupplyTarget = 128;
	public int dropCollectionRadius = 8;
	public double dropCollectionStableSeconds = 1.5;
	public int inventoryReservedSlots = 1;
	public int miningBlocksPerEmptySlot = 64;
	public int elytraNavigationMinDistance = 32;
	public int unloadLandingSearchRadius = 16;
	public double unloadEdgeInset = 0.2;
	public double navigationStallTimeoutSeconds = 10.0;
	public int navigationRetryCount = 2;
	public double portalTransitionCost = 16.0;
	public double portalTransitionTimeoutSeconds = 20.0;
	public double portalExitTimeoutSeconds = 20.0;
	public int portalExitMinRadius = 3;
	public int portalExitMaxRadius = 8;
	public int portalExitVerticalRadius = 4;
	public double repairExperienceStableSeconds = 1.5;
	public double supplyInteractionTimeoutSeconds = 2.0;
	public double furnaceInteractionTimeoutSeconds = 2.0;
	public int flightRetryCount = 2;
}
