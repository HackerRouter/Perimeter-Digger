package hackerrouter.perimeterdigger.client;

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
			} catch (RuntimeException ignored) {
			}
		});
	}
}
