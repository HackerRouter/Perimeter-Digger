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
