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
