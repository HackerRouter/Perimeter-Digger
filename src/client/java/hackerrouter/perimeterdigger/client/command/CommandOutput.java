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

package hackerrouter.perimeterdigger.client.command;

import com.mojang.brigadier.context.CommandContext;
import hackerrouter.perimeterdigger.client.translation.Translations;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class CommandOutput {
	private CommandOutput() {
	}

	static MutableComponent category(String value) {
		return Component.literal(value).withStyle(ChatFormatting.AQUA);
	}

	static MutableComponent category(Component value) {
		return Component.empty().append(value).withStyle(ChatFormatting.AQUA);
	}

	static MutableComponent field(String name, Object value) {
		return field(name, valueComponent(value));
	}

	static MutableComponent field(Component name, Object value) {
		return field(name, valueComponent(value));
	}

	static MutableComponent field(Component name, MutableComponent value) {
		return Component.empty().append(name.copy().withStyle(ChatFormatting.YELLOW))
				.append(Component.literal("=").withStyle(ChatFormatting.GRAY))
				.append(value);
	}

	static MutableComponent field(String name, MutableComponent value) {
		return Component.literal(name).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("=").withStyle(ChatFormatting.GRAY))
				.append(value);
	}

	static MutableComponent valueComponent(Object value) {
		if (value == null) return clickableValue("unset", Translations.VALUE.tr("unset"), ChatFormatting.RED);
		if (value instanceof Boolean flag) {
			return clickableValue(Boolean.toString(flag), Translations.VALUE.tr(Boolean.toString(flag)), flag ? ChatFormatting.GREEN : ChatFormatting.RED);
		}
		String text = value.toString();
		if (text.equalsIgnoreCase("unset") || text.equalsIgnoreCase("none")) {
			return clickableValue(text, Translations.VALUE.tr(text.toLowerCase()), ChatFormatting.RED);
		}
		return clickableValue(text, Component.literal(text), ChatFormatting.GREEN);
	}

	static MutableComponent separator() {
		return Component.literal(", ").withStyle(ChatFormatting.GRAY);
	}

	static void feedback(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message);
	}

	static int error(CommandContext<FabricClientCommandSource> context, String message) {
		return error(context, message == null ? Translations.COMMAND.tr("unknown_error") : Component.literal(message));
	}

	static int error(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(Translations.COMMAND.tr("error", message));
		return 0;
	}

	private static MutableComponent clickableValue(String value, Component display, ChatFormatting color) {
		return Component.empty().append(display).withStyle(color)
				.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(value)));
	}
}
