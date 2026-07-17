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

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import hackerrouter.perimeterdigger.client.config.AdvancedConfig;
import hackerrouter.perimeterdigger.client.config.AdvancedOption;
import hackerrouter.perimeterdigger.client.config.AdvancedOptions;
import hackerrouter.perimeterdigger.client.config.WorldConfigManager;
import hackerrouter.perimeterdigger.client.state.AutomationController;
import hackerrouter.perimeterdigger.client.translation.Translations;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static hackerrouter.perimeterdigger.client.command.CommandOutput.category;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.error;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.feedback;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.field;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.separator;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

final class AdvancedConfigCommand {
	private final WorldConfigManager configs;
	private final AutomationController controller;

	AdvancedConfigCommand(WorldConfigManager configs, AutomationController controller) {
		this.configs = configs;
		this.controller = controller;
	}

	LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("config_advanced")
				.executes(this::show)
				.then(literal("show").executes(this::show))
				.then(literal("set")
						.then(argument("advancedKey", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(AdvancedOptions.keys(), builder))
								.then(argument("advancedValue", DoubleArgumentType.doubleArg(0.0))
										.suggests(this::suggestValue)
										.executes(this::set))));
	}

	private int set(CommandContext<FabricClientCommandSource> context) {
		try {
			String key = StringArgumentType.getString(context, "advancedKey");
			double value = DoubleArgumentType.getDouble(context, "advancedValue");
			AdvancedConfig advanced = configs.get().advanced;
			AdvancedOption option = AdvancedOptions.get(key);
			option.set(advanced, value);
			configs.save();
			controller.updateAdvanced(advanced);
			feedback(context, category(Translations.COMMAND.tr("advanced.title"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(Translations.COMMAND.tr("advanced.option." + key), option.get(advanced)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int show(CommandContext<FabricClientCommandSource> context) {
		try {
			AdvancedConfig advanced = configs.get().advanced;
			Map<String, List<AdvancedOption>> groups = new LinkedHashMap<>();
			for (AdvancedOption option : AdvancedOptions.all()) {
				groups.computeIfAbsent(option.group(), ignored -> new ArrayList<>()).add(option);
			}
			for (Map.Entry<String, List<AdvancedOption>> group : groups.entrySet()) {
				feedback(context, line(group.getKey(), advanced, group.getValue()));
			}
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private CompletableFuture<Suggestions> suggestValue(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
		try {
			String key = StringArgumentType.getString(context, "advancedKey");
			return SharedSuggestionProvider.suggest(new String[]{AdvancedOptions.get(key).get(configs.get().advanced).toString()}, builder);
		} catch (RuntimeException exception) {
			return builder.buildFuture();
		}
	}

	private static MutableComponent line(String name, AdvancedConfig config, List<AdvancedOption> options) {
		String groupKey = name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
		MutableComponent result = category(Translations.COMMAND.tr("advanced.group." + groupKey)).append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
		for (int index = 0; index < options.size(); index++) {
			if (index > 0) result.append(separator());
			AdvancedOption option = options.get(index);
			result.append(field(Translations.COMMAND.tr("advanced.option." + option.key()), option.get(config)));
		}
		return result.append(Component.literal(".").withStyle(ChatFormatting.GRAY));
	}
}
