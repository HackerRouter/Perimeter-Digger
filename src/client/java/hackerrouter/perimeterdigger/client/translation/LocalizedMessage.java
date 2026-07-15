package hackerrouter.perimeterdigger.client.translation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;

public record LocalizedMessage(String key, String literal, Object[] arguments) {
	public LocalizedMessage {
		arguments = arguments == null ? new Object[0] : Arrays.copyOf(arguments, arguments.length);
	}

	public static LocalizedMessage translatable(String key, Object... arguments) {
		return new LocalizedMessage(key, null, arguments);
	}

	public static LocalizedMessage literal(String text) {
		return new LocalizedMessage(null, text == null ? "" : text, new Object[0]);
	}

	public MutableComponent component() {
		return key == null ? Component.literal(literal) : Component.translatable(key, arguments);
	}

	@Override
	public Object[] arguments() {
		return Arrays.copyOf(arguments, arguments.length);
	}
}
