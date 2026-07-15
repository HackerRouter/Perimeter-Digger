package hackerrouter.perimeterdigger.client.translation;

import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;

public final class Translator {
	private final String path;

	public Translator(String path) {
		this.path = validate(path);
	}

	public Translator derive(String child) {
		return new Translator(path + "." + validate(child));
	}

	public String key(String name) {
		return path + "." + validate(name);
	}

	public MutableComponent tr(String name, Object... arguments) {
		return net.minecraft.network.chat.Component.translatable(key(name), arguments);
	}

	public LocalizedMessage message(String name, Object... arguments) {
		return LocalizedMessage.translatable(key(name), arguments);
	}

	private static String validate(String value) {
		Objects.requireNonNull(value, "Translation path cannot be null.");
		if (value.isBlank() || value.startsWith(".") || value.endsWith(".") || value.contains("..")) {
			throw new IllegalArgumentException("Invalid translation path: " + value);
		}
		return value;
	}
}
