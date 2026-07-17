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
