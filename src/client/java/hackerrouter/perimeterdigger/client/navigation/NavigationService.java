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

package hackerrouter.perimeterdigger.client.navigation;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IElytraProcess;
import net.minecraft.core.BlockPos;

public final class NavigationService {
	private ICustomGoalProcess walking;
	private IElytraProcess flight;
	private Boolean savedAllowBreak;
	private Boolean savedAllowPlace;

	public void bind(IBaritone baritone) {
		if (walking != null || flight != null) {
			stop();
			restoreDestinationSettings();
		}
		walking = baritone.getCustomGoalProcess();
		flight = baritone.getElytraProcess();
	}

	public void unbind() {
		stop();
		restoreDestinationSettings();
		walking = null;
		flight = null;
	}

	public void walk(Goal goal) {
		requireWalking().setGoalAndPath(goal);
	}

	public void fly(BlockPos target) {
		requireFlight().pathTo(target);
	}

	public boolean isWalking() {
		return walking != null && walking.isActive();
	}

	public boolean isFlying() {
		return flight != null && flight.isActive();
	}

	public boolean isFlightLoaded() {
		return flight != null && flight.isLoaded();
	}

	public void stopWalking() {
		if (walking != null) walking.onLostControl();
	}

	public void stopFlying() {
		if (flight != null && flight.isActive()) flight.onLostControl();
	}

	public void stop() {
		stopWalking();
		stopFlying();
	}

	public void disablePlacement() {
		if (savedAllowPlace == null) savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
		BaritoneAPI.getSettings().allowPlace.value = false;
	}

	public void enableBreakingWithoutPlacement() {
		if (savedAllowBreak == null) savedAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
		if (savedAllowPlace == null) savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
		BaritoneAPI.getSettings().allowBreak.value = true;
		BaritoneAPI.getSettings().allowPlace.value = false;
	}

	public void restoreDestinationSettings() {
		if (savedAllowBreak != null) BaritoneAPI.getSettings().allowBreak.value = savedAllowBreak;
		if (savedAllowPlace != null) BaritoneAPI.getSettings().allowPlace.value = savedAllowPlace;
		savedAllowBreak = null;
		savedAllowPlace = null;
	}

	private ICustomGoalProcess requireWalking() {
		if (walking == null) throw new IllegalStateException("Navigation service is not bound to Baritone.");
		return walking;
	}

	private IElytraProcess requireFlight() {
		if (flight == null) throw new IllegalStateException("Navigation service is not bound to Baritone.");
		return flight;
	}
}
