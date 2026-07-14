package hackerrouter.perimeterdigger.client;

import hackerrouter.perimeterdigger.client.command.PerimeterCommand;
import hackerrouter.perimeterdigger.client.config.WorldConfigManager;
import hackerrouter.perimeterdigger.client.state.AutomationController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class PerimeterDiggerClient implements ClientModInitializer {
	private final WorldConfigManager configs = new WorldConfigManager();
	private final AutomationController controller = new AutomationController();
	private String activeIdentity;

	@Override
	public void onInitializeClient() {
		PerimeterCommand command = new PerimeterCommand(configs, controller);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(command.build()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				if (activeIdentity != null) controller.resetForWorldChange();
				activeIdentity = null;
				return;
			}
			try {
				String identity = configs.identity();
				if (activeIdentity != null && !activeIdentity.equals(identity)) {
					controller.resetForWorldChange();
				}
				activeIdentity = identity;
				controller.tick();
			} catch (RuntimeException ignored) {
			}
		});
	}
}
