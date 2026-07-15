package hackerrouter.perimeterdigger.client.config;

import java.util.function.Function;

public record AdvancedOption(
		String key,
		String group,
		String label,
		Function<AdvancedConfig, Number> getter,
		ValueSetter setter
) {
	public Number get(AdvancedConfig config) {
		return getter.apply(config);
	}

	public void set(AdvancedConfig config, double value) {
		setter.set(config, value);
	}

	@FunctionalInterface
	public interface ValueSetter {
		void set(AdvancedConfig config, double value);
	}
}
