package hackerrouter.perimeterdigger.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import hackerrouter.perimeterdigger.client.config.DetectedAreaConfig;
import hackerrouter.perimeterdigger.client.config.PerimeterConfig;
import hackerrouter.perimeterdigger.client.config.PositionConfig;
import hackerrouter.perimeterdigger.client.config.ScanlineConfig;
import hackerrouter.perimeterdigger.client.config.UnloadingPointConfig;
import hackerrouter.perimeterdigger.client.config.WorldConfigManager;
import hackerrouter.perimeterdigger.client.detect.BoundaryDetector;
import hackerrouter.perimeterdigger.client.state.AutomationController;
import hackerrouter.perimeterdigger.client.state.StateTransition;
import hackerrouter.perimeterdigger.client.translation.Translations;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class PerimeterCommand {
	private static final List<String> POINT_KEYS = List.of(
			"consumable_supply_point",
			"durability_supply_point",
			"bed_point",
			"perimeter_portal_overworld",
			"perimeter_portal_nether",
			"repair_portal_overworld",
			"repair_portal_nether",
			"furnace_row_start",
			"furnace_row_end"
	);
	private final WorldConfigManager configs;
	private final AutomationController controller;
	private final BoundaryDetector detector = new BoundaryDetector();

	public PerimeterCommand(WorldConfigManager configs, AutomationController controller) {
		this.configs = configs;
		this.controller = controller;
	}

	public LiteralArgumentBuilder<FabricClientCommandSource> build() {
		LiteralArgumentBuilder<FabricClientCommandSource> root = literal("perimeterdig")
				.executes(this::status)
				.then(literal("start").executes(this::start))
				.then(literal("stop").executes(this::stop))
				.then(literal("pause").executes(this::pause))
				.then(literal("resume").executes(this::resume))
				.then(literal("status")
						.executes(this::status)
						.then(literal("clear").executes(this::clearStatus))
						.then(literal("history")
								.executes(context -> showStatusHistory(context, 16))
								.then(literal("all").executes(context -> showStatusHistory(context, 64)))
								.then(literal("clear").executes(this::clearStatusHistory))))
				.then(literal("reload").executes(this::reload));
		root.then(buildDetect());
		root.then(buildPlan());
		root.then(buildConfig());
		root.then(new AdvancedConfigCommand(configs, controller).build());
		root.then(new FunctionCommand(configs, controller).build());
		root.then(buildDebug());
		return root;
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> buildDebug() {
		return literal("debug")
				.then(literal("unload").executes(this::debugUnload))
				.then(literal("supply")
						.then(literal("consumables").executes(context -> debugSupply(context, false)))
						.then(literal("durability").executes(context -> debugSupply(context, true))))
				.then(literal("repair").executes(this::debugRepair))
				.then(literal("sleep").executes(this::debugSleep));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> buildPlan() {
		return literal("plan")
				.then(literal("rectangle")
						.then(argument("x0", IntegerArgumentType.integer())
								.suggests(coordinate(source -> source.getPlayer().getBlockX()))
								.then(argument("z0", IntegerArgumentType.integer())
										.suggests(coordinate(source -> source.getPlayer().getBlockZ()))
										.then(argument("x1", IntegerArgumentType.integer())
												.suggests(coordinate(source -> source.getPlayer().getBlockX()))
												.then(argument("z1", IntegerArgumentType.integer())
														.suggests(coordinate(source -> source.getPlayer().getBlockZ()))
														.then(argument("minY", IntegerArgumentType.integer())
																.suggests(coordinate(source -> source.getPlayer().getBlockY()))
																.then(argument("maxY", IntegerArgumentType.integer())
																		.suggests(coordinate(source -> source.getPlayer().getBlockY()))
																		.executes(this::planRectangle))))))));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> buildDetect() {
		return literal("detect")
				.then(argument("boundaryBlock", BlockIdentifierArgument.blockIdentifier())
						.suggests(this::suggestBlocks)
						.then(argument("boundaryY", IntegerArgumentType.integer())
								.suggests(coordinate(source -> source.getPlayer().getBlockY()))
								.executes(this::detect)));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> buildConfig() {
		LiteralArgumentBuilder<FabricClientCommandSource> config = literal("config")
				.executes(this::showConfig)
				.then(literal("show").executes(this::showConfig))
				.then(literal("clear")
						.then(literal("detected_area").executes(this::clearDetectedArea)));

		LiteralArgumentBuilder<FabricClientCommandSource> set = literal("set");
		set.then(literal("digging_min_y")
				.then(argument("y", IntegerArgumentType.integer())
						.suggests(coordinate(source -> source.getPlayer().getBlockY()))
						.executes(context -> setDiggingY(context, true))));
		set.then(literal("digging_max_y")
				.then(argument("y", IntegerArgumentType.integer())
						.suggests(coordinate(source -> source.getPlayer().getBlockY()))
						.executes(context -> setDiggingY(context, false))));
		LiteralArgumentBuilder<FabricClientCommandSource> liquidPolicy = literal("liquid_policy");
		for (String policy : List.of("avoid", "replace", "seal_boundary")) {
			liquidPolicy.then(literal(policy).executes(context -> setLiquidPolicy(context, policy)));
		}
		set.then(liquidPolicy);
		LiteralArgumentBuilder<FabricClientCommandSource> durabilityRecoveryMode = literal("durability_recovery_mode");
		for (String mode : List.of("repair_portal", "supply_point")) {
			durabilityRecoveryMode.then(literal(mode).executes(context -> setDurabilityRecoveryMode(context, mode)));
		}
		set.then(durabilityRecoveryMode);
		for (String key : POINT_KEYS) {
			set.then(pointSetter(key, this::setPoint));
		}
		set.then(literal("unloading_point")
				.then(argument("name", StringArgumentType.word())
						.suggests(unloadingNames(true))
						.then(coordinateArguments(context -> setUnloadingPoint(context)))));
		config.then(set);
		config.then(literal("add")
				.then(literal("sealing_block")
						.then(argument("block", BlockIdentifierArgument.blockIdentifier())
								.suggests(this::suggestBlocks)
								.executes(this::addSealingBlock)))
				.then(literal("food")
						.then(argument("item", ItemIdentifierArgument.itemIdentifier())
								.suggests(this::suggestItems)
								.executes(this::addFood)))
				.then(literal("unloading_whitelist")
						.then(argument("item", ItemIdentifierArgument.itemIdentifier())
								.suggests(this::suggestItems)
								.executes(this::addUnloadingWhitelist))));
		LiteralArgumentBuilder<FabricClientCommandSource> remove = literal("remove");
		remove.then(literal("unloading_point")
						.then(argument("name", StringArgumentType.word())
								.suggests(unloadingNames(false))
								.executes(this::removeUnloadingPoint)));
		remove.then(literal("sealing_block")
				.then(argument("block", BlockIdentifierArgument.blockIdentifier())
						.suggests(sealingBlockNames())
						.executes(this::removeSealingBlock)));
		remove.then(literal("food")
				.then(argument("item", ItemIdentifierArgument.itemIdentifier())
						.suggests(foodNames())
						.executes(this::removeFood)));
		remove.then(literal("unloading_whitelist")
				.then(argument("item", ItemIdentifierArgument.itemIdentifier())
						.suggests(unloadingWhitelistNames())
						.executes(this::removeUnloadingWhitelist)));
		config.then(remove);
		return config;
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> pointSetter(String key, BiConsumer<String, PositionConfig> setter) {
		return literal(key).then(coordinateArguments(context -> {
			PositionConfig position = position(context);
			setter.accept(key, position);
			configs.save();
			feedback(context, Translations.COMMAND.tr("set_value", key, position));
			return 1;
		}));
	}

	private RequiredArgumentBuilder<FabricClientCommandSource, Integer> coordinateArguments(com.mojang.brigadier.Command<FabricClientCommandSource> command) {
		return argument("x", IntegerArgumentType.integer())
				.suggests(coordinate(source -> source.getPlayer().getBlockX()))
				.then(argument("y", IntegerArgumentType.integer())
						.suggests(coordinate(source -> source.getPlayer().getBlockY()))
						.then(argument("z", IntegerArgumentType.integer())
								.suggests(coordinate(source -> source.getPlayer().getBlockZ()))
								.executes(command)));
	}

	private int detect(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = BlockIdentifierArgument.getIdentifier(context, "boundaryBlock");
			Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null) {
				return error(context, Translations.COMMAND.tr("error_unknown_boundary"));
			}
			int y = IntegerArgumentType.getInteger(context, "boundaryY");
			feedback(context, Translations.COMMAND.tr("detecting"));
			DetectedAreaConfig area = detector.detect(block, id, y);
			configs.get().detectedArea = area;
			configs.save();
			feedback(context, Translations.COMMAND.tr("detected", area.columnCount, area.scanlines.size(), area.minX, area.maxX, area.minZ, area.maxZ));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int planRectangle(CommandContext<FabricClientCommandSource> context) {
		try {
			int x0 = IntegerArgumentType.getInteger(context, "x0");
			int z0 = IntegerArgumentType.getInteger(context, "z0");
			int x1 = IntegerArgumentType.getInteger(context, "x1");
			int z1 = IntegerArgumentType.getInteger(context, "z1");
			int minY = IntegerArgumentType.getInteger(context, "minY");
			int maxY = IntegerArgumentType.getInteger(context, "maxY");
			if (minY > maxY) {
				return error(context, Translations.COMMAND.tr("error_inverted_y"));
			}
			int minX = Math.min(x0, x1);
			int maxX = Math.max(x0, x1);
			int minZ = Math.min(z0, z1);
			int maxZ = Math.max(z0, z1);
			DetectedAreaConfig area = new DetectedAreaConfig();
			area.boundaryBlock = "rectangle";
			area.boundaryY = maxY;
			area.minX = minX;
			area.maxX = maxX;
			area.minZ = minZ;
			area.maxZ = maxZ;
			area.columnCount = ((long) maxX - minX + 1L) * ((long) maxZ - minZ + 1L);
			for (int z = minZ; ; z++) {
				area.scanlines.add(new ScanlineConfig(z, minX, maxX));
				if (z == maxZ) break;
			}
			PerimeterConfig config = configs.get();
			config.detectedArea = area;
			config.diggingMinY = minY;
			config.diggingMaxY = maxY;
			configs.save();
			feedback(context, Translations.COMMAND.tr("planned_rectangle", area.columnCount, minX, maxX, minZ, maxZ, minY, maxY));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int setDiggingY(CommandContext<FabricClientCommandSource> context, boolean minimum) {
		try {
			int y = IntegerArgumentType.getInteger(context, "y");
			PerimeterConfig config = configs.get();
			if (minimum) config.diggingMinY = y;
			else config.diggingMaxY = y;
			configs.save();
			feedback(context, Translations.COMMAND.tr("set_value", minimum ? "digging_min_y" : "digging_max_y", y));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int setLiquidPolicy(CommandContext<FabricClientCommandSource> context, String policy) {
		try {
			configs.get().liquidPolicy = policy;
			configs.save();
			feedback(context, Translations.COMMAND.tr("set_value", "liquid_policy", policy));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int setDurabilityRecoveryMode(CommandContext<FabricClientCommandSource> context, String mode) {
		try {
			configs.get().durabilityRecoveryMode = mode;
			configs.save();
			feedback(context, Translations.COMMAND.tr("set_value", "durability_recovery_mode", mode));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int addSealingBlock(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = BlockIdentifierArgument.getIdentifier(context, "block");
			Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null || block.defaultBlockState().isAir()) return error(context, Translations.COMMAND.tr("error_invalid_sealing_block"));
			String value = id.toString();
			if (!configs.get().sealingBlocks.contains(value)) configs.get().sealingBlocks.add(value);
			configs.save();
			feedback(context, Translations.COMMAND.tr("added_value", configText("sealing_block"), value));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeSealingBlock(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = BlockIdentifierArgument.getIdentifier(context, "block");
			String value = id.toString();
			if (!configs.get().sealingBlocks.contains(value)) return error(context, Translations.COMMAND.tr("error_sealing_block_not_configured", value));
			configs.get().sealingBlocks.remove(value);
			configs.save();
			feedback(context, Translations.COMMAND.tr("removed_value", configText("sealing_block"), value));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int addFood(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null || !item.components().has(net.minecraft.core.component.DataComponents.CONSUMABLE)) return error(context, Translations.COMMAND.tr("error_invalid_food"));
			String value = id.toString();
			if (!configs.get().foods.contains(value)) configs.get().foods.add(value);
			configs.save();
			feedback(context, Translations.COMMAND.tr("added_value", configText("food"), value));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeFood(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			String value = id.toString();
			if (!configs.get().foods.remove(value)) return error(context, Translations.COMMAND.tr("error_food_not_configured", value));
			configs.save();
			feedback(context, Translations.COMMAND.tr("removed_value", configText("food"), value));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int addUnloadingWhitelist(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			if (BuiltInRegistries.ITEM.getOptional(id).isEmpty()) return error(context, Translations.COMMAND.tr("error_unknown_item"));
			String value = id.toString();
			if (!configs.get().unloadingWhitelist.contains(value)) configs.get().unloadingWhitelist.add(value);
			configs.save();
			feedback(context, Translations.COMMAND.tr("added_value", configText("unloading_whitelist_item"), value));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeUnloadingWhitelist(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			String value = id.toString();
			if (!configs.get().unloadingWhitelist.remove(value)) return error(context, Translations.COMMAND.tr("error_not_whitelisted", value));
			configs.save();
			feedback(context, Translations.COMMAND.tr("removed_value", configText("unloading_whitelist_item"), value));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private void setPoint(String key, PositionConfig position) {
		PerimeterConfig config = configs.get();
		switch (key) {
			case "consumable_supply_point" -> config.consumableSupplyPoint = position;
			case "durability_supply_point" -> config.durabilitySupplyPoint = position;
			case "bed_point" -> config.bedPoint = position;
			case "perimeter_portal_overworld" -> config.perimeterPortalOverworld = position;
			case "perimeter_portal_nether" -> config.perimeterPortalNether = position;
			case "repair_portal_overworld" -> config.repairPortalOverworld = position;
			case "repair_portal_nether" -> config.repairPortalNether = position;
			case "furnace_row_start" -> config.furnaceRowStart = position;
			case "furnace_row_end" -> config.furnaceRowEnd = position;
			default -> throw new IllegalArgumentException("Unknown point key: " + key);
		}
	}

	private int setUnloadingPoint(CommandContext<FabricClientCommandSource> context) {
		try {
			String name = normalizedName(StringArgumentType.getString(context, "name"));
			PositionConfig position = position(context);
			configs.get().unloadingPoints.put(name, new UnloadingPointConfig(position.x, position.y, position.z));
			configs.save();
			feedback(context, Translations.COMMAND.tr("set_unloading_point", name, position.x, position.y, position.z));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeUnloadingPoint(CommandContext<FabricClientCommandSource> context) {
		try {
			String name = normalizedName(StringArgumentType.getString(context, "name"));
			if (configs.get().unloadingPoints.remove(name) == null) {
				return error(context, Translations.COMMAND.tr("error_unknown_unloading_point", name));
			}
			configs.save();
			feedback(context, Translations.COMMAND.tr("removed_value", configText("unloading_point"), name));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int clearDetectedArea(CommandContext<FabricClientCommandSource> context) {
		try {
			configs.get().detectedArea = null;
			configs.save();
			feedback(context, Translations.COMMAND.tr("area_cleared"));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int start(CommandContext<FabricClientCommandSource> context) {
		try {
			List<String> missing = controller.start(configs.get());
			if (!missing.isEmpty()) {
				return error(context, "Cannot start. Missing or invalid: " + String.join(", ", missing) + ".");
			}
			feedback(context, Translations.COMMAND.tr("mining_started"));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int debugUnload(CommandContext<FabricClientCommandSource> context) {
		try {
			List<String> missing = controller.debugStage5(configs.get());
			if (!missing.isEmpty()) {
				return error(context, "Cannot start unloading debug. Missing or invalid: " + String.join(", ", missing) + ".");
			}
			feedback(context, Translations.COMMAND.tr("debug_started", configText("unloading")));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int debugSupply(CommandContext<FabricClientCommandSource> context, boolean durability) {
		try {
			List<String> missing = controller.debugStage6(configs.get(), durability);
			if (!missing.isEmpty()) {
				return error(context, "Cannot start supply debug. Missing or invalid: " + String.join(", ", missing) + ".");
			}
			feedback(context, Translations.COMMAND.tr("debug_started", configText(durability ? "durability_supply" : "consumable_supply")));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int debugRepair(CommandContext<FabricClientCommandSource> context) {
		try {
			List<String> missing = controller.debugStage7(configs.get());
			if (!missing.isEmpty()) {
				return error(context, "Cannot start repair debug. Missing or invalid: " + String.join(", ", missing) + ".");
			}
			feedback(context, Translations.COMMAND.tr("debug_started", configText("repair")));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int debugSleep(CommandContext<FabricClientCommandSource> context) {
		try {
			List<String> missing = controller.debugSleep(configs.get());
			if (!missing.isEmpty()) {
				return error(context, "Cannot start sleep debug. Missing or invalid: " + String.join(", ", missing) + ".");
			}
			feedback(context, Translations.COMMAND.tr("debug_started", configText("sleep")));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int stop(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.stop();
			feedback(context, Translations.COMMAND.tr("stopped"));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int pause(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.pause();
			feedback(context, Translations.COMMAND.tr("paused"));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int resume(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.resume();
			feedback(context, Translations.COMMAND.tr("resumed"));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int status(CommandContext<FabricClientCommandSource> context) {
		feedback(context, Translations.COMMAND.tr("status", Translations.state(controller.state()), controller.detail()));
		return 1;
	}

	private int showStatusHistory(CommandContext<FabricClientCommandSource> context, int limit) {
		List<StateTransition> history = controller.stateHistory();
		if (history.isEmpty()) {
			feedback(context, category(Translations.COMMAND.tr("history.title"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(valueComponent("none"))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			return 1;
		}
		feedback(context, Translations.COMMAND.tr("history.summary", Math.min(limit, history.size()), history.size()).withStyle(ChatFormatting.GRAY));
		for (int index = history.size() - 1, shown = 0; index >= 0 && shown < limit; index--, shown++) {
			StateTransition entry = history.get(index);
			feedback(context, Component.literal("- ").withStyle(ChatFormatting.GRAY)
					.append(valueComponent(entry.timestamp()))
					.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
					.append(Translations.state(entry.previousState()).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(" -> ").withStyle(ChatFormatting.GRAY))
					.append(Translations.state(entry.nextState()).withStyle(ChatFormatting.AQUA))
					.append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
					.append(valueComponent(entry.position()))
					.append(Component.literal(" in ").withStyle(ChatFormatting.GRAY))
					.append(valueComponent(entry.dimension()))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(entry.detail().component().withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private int clearStatusHistory(CommandContext<FabricClientCommandSource> context) {
		controller.clearStateHistory();
		feedback(context, Translations.COMMAND.tr("history.cleared"));
		return 1;
	}

	private int clearStatus(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.clearCachedState();
			feedback(context, Translations.COMMAND.tr("cache_cleared"));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int reload(CommandContext<FabricClientCommandSource> context) {
		try {
			configs.reload();
			controller.resetForWorldChange();
			feedback(context, Translations.COMMAND.tr("reloaded", configs.identity()));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int showConfig(CommandContext<FabricClientCommandSource> context) {
		try {
			PerimeterConfig config = configs.get();
			feedback(context, category(configText("title"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(configText("digging_y"), rangeValue(config.diggingMinY, config.diggingMaxY))).append(separator())
					.append(field(configText("detected_columns"), config.detectedArea == null ? "unset" : config.detectedArea.columnCount)).append(separator())
					.append(field(configText("unloading_points"), config.unloadingPoints.size()))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, category(configText("liquid"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(configText("policy"), config.liquidPolicy)).append(separator())
					.append(field(configText("sealing_blocks"), listValue(config.sealingBlocks)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, category(configText("resources"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(configText("foods"), listValue(config.foods))).append(separator())
					.append(field(configText("durability_recovery"), config.durabilityRecoveryMode))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, configText("navigation_note").withStyle(ChatFormatting.GRAY));
			feedback(context, category(configText("unloading"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(configText("whitelist"), listValue(config.unloadingWhitelist)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, category(configText("locations"))
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(configText("consumable_supply_chest"), config.consumableSupplyPoint)).append(separator())
					.append(field(configText("durability_supply_chest"), config.durabilitySupplyPoint)).append(separator())
					.append(field(configText("bed"), config.bedPoint)).append(separator())
					.append(field(configText("perimeter_portals"), pairValue(config.perimeterPortalOverworld, config.perimeterPortalNether))).append(separator())
					.append(field(configText("repair_portals"), pairValue(config.repairPortalOverworld, config.repairPortalNether))).append(separator())
					.append(field(configText("furnace_row"), pairValue(config.furnaceRowStart, config.furnaceRowEnd)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			if (config.unloadingPoints.isEmpty()) {
				feedback(context, category(configText("unloading_points"))
						.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
						.append(valueComponent("none"))
						.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			} else {
				feedback(context, category(configText("unloading_points")).append(Component.literal(":").withStyle(ChatFormatting.GRAY)));
				config.unloadingPoints.forEach((name, point) -> feedback(context, Component.literal("- ").withStyle(ChatFormatting.GRAY)
						.append(field(name, point))));
			}
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private SuggestionProvider<FabricClientCommandSource> unloadingNames(boolean allowNew) {
		return (context, builder) -> {
			try {
				List<String> names = new java.util.ArrayList<>(configs.get().unloadingPoints.keySet());
				if (allowNew && names.isEmpty()) names.add("main");
				return SharedSuggestionProvider.suggest(names, builder);
			} catch (RuntimeException exception) {
				return builder.buildFuture();
			}
		};
	}

	private SuggestionProvider<FabricClientCommandSource> sealingBlockNames() {
		return (context, builder) -> {
			try {
				return SharedSuggestionProvider.suggest(configs.get().sealingBlocks, builder);
			} catch (RuntimeException exception) {
				return builder.buildFuture();
			}
		};
	}

	private SuggestionProvider<FabricClientCommandSource> foodNames() {
		return (context, builder) -> {
			try {
				return SharedSuggestionProvider.suggest(configs.get().foods, builder);
			} catch (RuntimeException exception) {
				return builder.buildFuture();
			}
		};
	}

	private SuggestionProvider<FabricClientCommandSource> unloadingWhitelistNames() {
		return (context, builder) -> {
			try {
				return SharedSuggestionProvider.suggest(configs.get().unloadingWhitelist, builder);
			} catch (RuntimeException exception) {
				return builder.buildFuture();
			}
		};
	}

	private CompletableFuture<Suggestions> suggestBlocks(
			CommandContext<FabricClientCommandSource> context,
			SuggestionsBuilder builder
	) {
		String input = builder.getRemaining().toLowerCase(Locale.ROOT);
		int separator = input.indexOf(':');
		String namespace = separator >= 0 ? input.substring(0, separator) : null;
		String path = separator >= 0 ? input.substring(separator + 1) : input;
		List<Suggestion> matches = BuiltInRegistries.BLOCK.keySet().stream()
				.filter(id -> namespace == null
						? id.getPath().startsWith(path)
						: id.getNamespace().startsWith(namespace) && id.getPath().startsWith(path))
				.sorted(Comparator
						.comparingInt((Identifier id) -> suggestionRank(id, path))
						.thenComparing(Identifier::toString))
				.map(id -> new Suggestion(StringRange.between(builder.getStart(), builder.getInput().length()), id.toString()))
				.toList();
		return CompletableFuture.completedFuture(new Suggestions(
				StringRange.between(builder.getStart(), builder.getInput().length()),
				matches
		));
	}

	private CompletableFuture<Suggestions> suggestItems(
			CommandContext<FabricClientCommandSource> context,
			SuggestionsBuilder builder
	) {
		String input = builder.getRemaining().toLowerCase(Locale.ROOT);
		int separator = input.indexOf(':');
		String namespace = separator >= 0 ? input.substring(0, separator) : null;
		String path = separator >= 0 ? input.substring(separator + 1) : input;
		List<Suggestion> matches = BuiltInRegistries.ITEM.keySet().stream()
				.filter(id -> namespace == null
						? id.getPath().startsWith(path)
						: id.getNamespace().startsWith(namespace) && id.getPath().startsWith(path))
				.sorted(Comparator.comparing(Identifier::toString))
				.map(id -> new Suggestion(StringRange.between(builder.getStart(), builder.getInput().length()), id.toString()))
				.toList();
		return CompletableFuture.completedFuture(new Suggestions(
				StringRange.between(builder.getStart(), builder.getInput().length()), matches));
	}

	private static int suggestionRank(Identifier id, String path) {
		if (!path.isEmpty() && id.getPath().equals(path + "_block")) return 0;
		if (id.getPath().equals(path)) return 1;
		return 2;
	}

	private static SuggestionProvider<FabricClientCommandSource> coordinate(ToIntFunction<FabricClientCommandSource> value) {
		return (context, builder) -> SharedSuggestionProvider.suggest(new String[]{Integer.toString(value.applyAsInt(context.getSource()))}, builder);
	}

	private static PositionConfig position(CommandContext<FabricClientCommandSource> context) {
		return new PositionConfig(
				IntegerArgumentType.getInteger(context, "x"),
				IntegerArgumentType.getInteger(context, "y"),
				IntegerArgumentType.getInteger(context, "z")
		);
	}

	private static String normalizedName(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		if (!normalized.matches("[a-z0-9_-]{1,64}")) {
			throw new IllegalArgumentException("Names may only contain a-z, 0-9, underscore, and hyphen.");
		}
		return normalized;
	}

	private static MutableComponent category(String value) {
		return CommandOutput.category(value);
	}

	private static MutableComponent configText(String key) {
		return Translations.COMMAND.tr("config." + key);
	}

	private static MutableComponent category(Component value) {
		return CommandOutput.category(value);
	}

	private static MutableComponent field(String name, Object value) {
		return CommandOutput.field(name, value);
	}

	private static MutableComponent field(Component name, Object value) {
		return CommandOutput.field(name, value);
	}

	private static MutableComponent field(String name, MutableComponent value) {
		return CommandOutput.field(name, value);
	}

	private static MutableComponent valueComponent(Object value) {
		return CommandOutput.valueComponent(value);
	}

	private static MutableComponent listValue(List<String> values) {
		return valueComponent(values.isEmpty() ? "none" : String.join(",", values));
	}

	private static MutableComponent rangeValue(Object minimum, Object maximum) {
		return Component.empty().append(valueComponent(minimum))
				.append(Component.literal("..").withStyle(ChatFormatting.GRAY))
				.append(valueComponent(maximum));
	}

	private static MutableComponent pairValue(Object first, Object second) {
		return Component.empty().append(valueComponent(first))
				.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
				.append(valueComponent(second));
	}

	private static MutableComponent separator() {
		return CommandOutput.separator();
	}

	private static void feedback(CommandContext<FabricClientCommandSource> context, String message) {
		context.getSource().sendFeedback(Component.literal(message));
	}

	private static void feedback(CommandContext<FabricClientCommandSource> context, Component message) {
		CommandOutput.feedback(context, message);
	}

	private static int error(CommandContext<FabricClientCommandSource> context, String message) {
		return CommandOutput.error(context, message);
	}

	private static int error(CommandContext<FabricClientCommandSource> context, Component message) {
		return CommandOutput.error(context, message);
	}
}
