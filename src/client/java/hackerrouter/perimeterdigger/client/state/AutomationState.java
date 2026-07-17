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

public enum AutomationState {
	IDLE,
	VALIDATING,
	READY,
	NAVIGATING_TO_MINE,
	MINING,
	COLLECTING_DROPS,
	EATING,
	COMPLETE,
	NAVIGATING_TO_UNLOAD,
	APPROACHING_UNLOAD,
	POSITIONING_FOR_UNLOAD,
	UNLOADING,
	NAVIGATING_TO_RESUPPLY,
	RESUPPLYING,
	NAVIGATING_TO_DURABILITY_SUPPLY,
	SWAPPING_DURABILITY_AT_SUPPLY,
	NAVIGATING_TO_BED,
	SLEEPING,
	NAVIGATING_TO_PERIMETER_PORTAL,
	ENTERING_PERIMETER_PORTAL,
	NAVIGATING_TO_REPAIR_PORTAL,
	ENTERING_REPAIR_PORTAL,
	CLEARING_REPAIR_PORTAL,
	NAVIGATING_TO_REPAIR_MACHINE,
	REPAIRING,
	RETURNING_TO_MINE,
	PAUSED,
	ERROR
}
