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
