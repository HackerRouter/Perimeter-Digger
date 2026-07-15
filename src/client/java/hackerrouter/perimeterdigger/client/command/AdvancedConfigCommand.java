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
			feedback(context, category("Advanced configuration")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(key, option.get(advanced)))
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
		MutableComponent result = category(name).append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
		for (int index = 0; index < options.size(); index++) {
			if (index > 0) result.append(separator());
			AdvancedOption option = options.get(index);
			result.append(field(option.label(), option.get(config)));
		}
		return result.append(Component.literal(".").withStyle(ChatFormatting.GRAY));
	}
}
