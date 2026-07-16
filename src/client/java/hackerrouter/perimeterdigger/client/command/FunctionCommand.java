package hackerrouter.perimeterdigger.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import hackerrouter.perimeterdigger.client.config.FunctionConfig;
import hackerrouter.perimeterdigger.client.config.WorldConfigManager;
import hackerrouter.perimeterdigger.client.state.AutomationController;
import hackerrouter.perimeterdigger.client.translation.Translations;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

import static hackerrouter.perimeterdigger.client.command.CommandOutput.category;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.error;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.feedback;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.field;
import static hackerrouter.perimeterdigger.client.command.CommandOutput.separator;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

final class FunctionCommand {
	private static final List<String> KEYS = List.of(
			"collect_drops", "unload", "eat", "durability_recovery", "cross_dimension_repair", "resupply", "elytra_navigation", "sleep"
	);
	private final WorldConfigManager configs;
	private final AutomationController controller;

	FunctionCommand(WorldConfigManager configs, AutomationController controller) {
		this.configs = configs;
		this.controller = controller;
	}

	LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return literal("function")
				.executes(this::show)
				.then(literal("enable").then(argument("functionName", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(KEYS, builder))
						.executes(context -> set(context, true))))
				.then(literal("disable").then(argument("functionName", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(KEYS, builder))
						.executes(context -> set(context, false))));
	}

	private int set(CommandContext<FabricClientCommandSource> context, boolean enabled) {
		try {
			String key = StringArgumentType.getString(context, "functionName");
			FunctionConfig functions = configs.get().functions;
			switch (key) {
				case "collect_drops" -> functions.collectDrops = enabled;
				case "unload" -> functions.unload = enabled;
				case "eat" -> functions.eat = enabled;
				case "durability_recovery" -> functions.durabilityRecovery = enabled;
				case "cross_dimension_repair" -> functions.crossDimensionRepair = enabled;
				case "resupply" -> functions.resupply = enabled;
				case "elytra_navigation" -> functions.elytraNavigation = enabled;
				case "sleep" -> functions.sleep = enabled;
				default -> throw new IllegalArgumentException("Unknown function: " + key + ".");
			}
			configs.save();
			controller.updateFunctions(functions);
			feedback(context, category(Translations.COMMAND.tr("function.title"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(Translations.COMMAND.tr("function." + key), enabled))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int show(CommandContext<FabricClientCommandSource> context) {
		try {
			FunctionConfig functions = configs.get().functions;
			feedback(context, category(Translations.COMMAND.tr("function.list_title"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(Translations.COMMAND.tr("function.collect_drops"), functions.collectDrops)).append(separator())
					.append(field(Translations.COMMAND.tr("function.unload"), functions.unload)).append(separator())
					.append(field(Translations.COMMAND.tr("function.eat"), functions.eat)).append(separator())
					.append(field(Translations.COMMAND.tr("function.durability_recovery"), functions.durabilityRecovery)).append(separator())
					.append(field(Translations.COMMAND.tr("function.cross_dimension_repair"), functions.crossDimensionRepair)).append(separator())
					.append(field(Translations.COMMAND.tr("function.resupply"), functions.resupply)).append(separator())
					.append(field(Translations.COMMAND.tr("function.elytra_navigation"), functions.elytraNavigation)).append(separator())
					.append(field(Translations.COMMAND.tr("function.sleep"), functions.sleep))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}
}
