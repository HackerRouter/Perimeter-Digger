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

package hackerrouter.perimeterdigger.client;

import hackerrouter.perimeterdigger.PerimeterDigger;
import hackerrouter.perimeterdigger.client.command.PerimeterCommand;
import hackerrouter.perimeterdigger.client.config.WorldConfigManager;
import hackerrouter.perimeterdigger.client.state.AutomationController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class PerimeterDiggerClient implements ClientModInitializer {
	private final WorldConfigManager configs = new WorldConfigManager();
	private final AutomationController controller = new AutomationController();
	private String activeIdentity;
	private String activeDimension;

	@Override
	public void onInitializeClient() {
		PerimeterCommand command = new PerimeterCommand(configs, controller);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(command.build()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			controller.resetForWorldChange();
			activeIdentity = null;
			activeDimension = null;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) return;
			try {
				String identity = configs.identity();
				String dimension = client.level.dimension().identifier().toString();
				if (activeIdentity != null && (!activeIdentity.equals(identity) || !dimension.equals(activeDimension))) {
					controller.handleWorldChange();
				}
				activeIdentity = identity;
				activeDimension = dimension;
				controller.tick();
			} catch (RuntimeException exception) {
				PerimeterDigger.LOGGER.error("Unhandled perimeter automation tick failure", exception);
				controller.handleTickFailure(exception);
			}
		});
	}
}
