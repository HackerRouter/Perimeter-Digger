package hackerrouter.perimeterdigger.client.command;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CommandOutputTest {
	@Test
	void preservesPrebuiltComponentValues() {
		MutableComponent value = Component.literal("unset")
				.append(Component.literal(" / "))
				.append(Component.literal("unset"));
		MutableComponent field = CommandOutput.field(Component.literal("portals"), value);
		assertEquals("portals=unset / unset", field.getString());
		assertFalse(field.getString().contains("siblings="));
	}
}
