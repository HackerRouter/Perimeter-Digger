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
