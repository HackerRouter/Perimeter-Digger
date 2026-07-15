package hackerrouter.perimeterdigger.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import hackerrouter.perimeterdigger.client.config.FunctionConfig;
import hackerrouter.perimeterdigger.client.config.AdvancedConfig;
import hackerrouter.perimeterdigger.client.config.PerimeterConfig;
import hackerrouter.perimeterdigger.client.config.PositionConfig;
import hackerrouter.perimeterdigger.client.config.ScanlineConfig;
import hackerrouter.perimeterdigger.client.config.UnloadingPointConfig;
import hackerrouter.perimeterdigger.client.config.WorldConfigManager;
import hackerrouter.perimeterdigger.client.detect.BoundaryDetector;
import hackerrouter.perimeterdigger.client.state.AutomationController;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
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
	private static final List<String> ADVANCED_KEYS = List.of(
			"tool_durability_threshold", "elytra_durability_threshold", "emergency_flight_durability_threshold",
			"food_level_threshold", "health_eating_threshold", "food_resupply_trigger", "food_resupply_target",
			"firework_resupply_trigger", "firework_resupply_target", "drop_collection_radius",
			"drop_collection_stable_seconds", "inventory_reserved_slots", "mining_blocks_per_empty_slot",
			"elytra_navigation_min_distance", "unload_landing_search_radius", "unload_edge_inset",
			"navigation_stall_timeout_seconds", "navigation_retry_count", "portal_transition_cost",
			"portal_transition_timeout_seconds", "portal_exit_timeout_seconds", "portal_exit_min_radius",
			"portal_exit_max_radius", "portal_exit_vertical_radius", "repair_experience_stable_seconds",
			"supply_interaction_timeout_seconds", "furnace_interaction_timeout_seconds", "flight_retry_count"
	);
	private static final List<String> FUNCTION_KEYS = List.of(
			"collect_drops",
			"unload",
			"eat",
			"durability_recovery",
			"resupply",
			"elytra_navigation",
			"sleep"
	);
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
						.then(literal("clear").executes(this::clearStatus)))
				.then(literal("reload").executes(this::reload));
		root.then(buildDetect());
		root.then(buildPlan());
		root.then(buildConfig());
		root.then(buildAdvancedConfig());
		root.then(buildFunction());
		root.then(buildDebug());
		return root;
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> buildAdvancedConfig() {
		return literal("config_advanced")
				.executes(this::showAdvancedConfig)
				.then(literal("show").executes(this::showAdvancedConfig))
				.then(literal("set")
						.then(argument("advancedKey", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(ADVANCED_KEYS, builder))
								.then(argument("advancedValue", DoubleArgumentType.doubleArg(0.0))
										.suggests(this::suggestAdvancedValue)
										.executes(this::setAdvancedConfig))));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> buildFunction() {
		return literal("function")
				.executes(this::showFunctions)
				.then(literal("enable")
						.then(argument("functionName", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(FUNCTION_KEYS, builder))
								.executes(context -> setFunction(context, true))))
				.then(literal("disable")
						.then(argument("functionName", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(FUNCTION_KEYS, builder))
								.executes(context -> setFunction(context, false))));
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
			feedback(context, "Set " + key + " to " + position + ".");
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
				return error(context, "Unknown boundary block.");
			}
			int y = IntegerArgumentType.getInteger(context, "boundaryY");
			feedback(context, "Detecting the enclosed area in loaded render chunks...");
			DetectedAreaConfig area = detector.detect(block, id, y);
			configs.get().detectedArea = area;
			configs.save();
			feedback(context, "Detected " + area.columnCount + " mining columns across " + area.scanlines.size() + " scanline ranges. Bounds: X=" + area.minX + ".." + area.maxX + ", Z=" + area.minZ + ".." + area.maxZ + ".");
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
				return error(context, "minY must not be greater than maxY.");
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
			feedback(context, "Planned an inclusive rectangle with " + area.columnCount + " columns. Bounds: X=" + minX + ".." + maxX + ", Z=" + minZ + ".." + maxZ + ", Y=" + minY + ".." + maxY + ".");
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
			feedback(context, "Set " + (minimum ? "digging_min_y" : "digging_max_y") + " to " + y + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int setLiquidPolicy(CommandContext<FabricClientCommandSource> context, String policy) {
		try {
			configs.get().liquidPolicy = policy;
			configs.save();
			feedback(context, "Set liquid_policy to " + policy + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int setDurabilityRecoveryMode(CommandContext<FabricClientCommandSource> context, String mode) {
		try {
			configs.get().durabilityRecoveryMode = mode;
			configs.save();
			feedback(context, "Set durability_recovery_mode to " + mode + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int addSealingBlock(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = BlockIdentifierArgument.getIdentifier(context, "block");
			Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null || block.defaultBlockState().isAir()) return error(context, "Unknown or unusable sealing block.");
			String value = id.toString();
			if (!configs.get().sealingBlocks.contains(value)) configs.get().sealingBlocks.add(value);
			configs.save();
			feedback(context, "Added sealing block " + value + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeSealingBlock(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = BlockIdentifierArgument.getIdentifier(context, "block");
			String value = id.toString();
			if (!configs.get().sealingBlocks.contains(value)) return error(context, "Sealing block is not configured: " + value);
			configs.get().sealingBlocks.remove(value);
			configs.save();
			feedback(context, "Removed sealing block " + value + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int addFood(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null || !item.components().has(net.minecraft.core.component.DataComponents.CONSUMABLE)) return error(context, "Unknown or non-consumable food item.");
			String value = id.toString();
			if (!configs.get().foods.contains(value)) configs.get().foods.add(value);
			configs.save();
			feedback(context, "Added food " + value + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeFood(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			String value = id.toString();
			if (!configs.get().foods.remove(value)) return error(context, "Food is not configured: " + value);
			configs.save();
			feedback(context, "Removed food " + value + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int addUnloadingWhitelist(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			if (BuiltInRegistries.ITEM.getOptional(id).isEmpty()) return error(context, "Unknown item.");
			String value = id.toString();
			if (!configs.get().unloadingWhitelist.contains(value)) configs.get().unloadingWhitelist.add(value);
			configs.save();
			feedback(context, "Added unloading whitelist item " + value + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeUnloadingWhitelist(CommandContext<FabricClientCommandSource> context) {
		try {
			Identifier id = ItemIdentifierArgument.getIdentifier(context, "item");
			String value = id.toString();
			if (!configs.get().unloadingWhitelist.remove(value)) return error(context, "Item is not in the unloading whitelist: " + value);
			configs.save();
			feedback(context, "Removed unloading whitelist item " + value + ".");
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
			feedback(context, "Set unloading point " + name + " to X=" + position.x + ", minY=" + position.y + ", Z=" + position.z + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int removeUnloadingPoint(CommandContext<FabricClientCommandSource> context) {
		try {
			String name = normalizedName(StringArgumentType.getString(context, "name"));
			if (configs.get().unloadingPoints.remove(name) == null) {
				return error(context, "Unknown unloading point: " + name);
			}
			configs.save();
			feedback(context, "Removed unloading point " + name + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int clearDetectedArea(CommandContext<FabricClientCommandSource> context) {
		try {
			configs.get().detectedArea = null;
			configs.save();
			feedback(context, "Cleared the detected area.");
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
			feedback(context, "Started Baritone area mining.");
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
			feedback(context, "Started unloading debug flow.");
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
			feedback(context, "Started " + (durability ? "durability" : "consumables") + " supply debug flow.");
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
			feedback(context, "Started repair debug flow.");
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
			feedback(context, "Started sleep debug flow.");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int stop(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.stop();
			feedback(context, "Stopped perimeter mining.");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int pause(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.pause();
			feedback(context, "Paused perimeter mining.");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int resume(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.resume();
			feedback(context, "Resumed perimeter mining.");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int status(CommandContext<FabricClientCommandSource> context) {
		feedback(context, "State: " + controller.state() + ". Detail: " + controller.detail() + ".");
		return 1;
	}

	private int clearStatus(CommandContext<FabricClientCommandSource> context) {
		try {
			controller.clearCachedState();
			feedback(context, "Cleared cached automation state. Saved configuration was not changed.");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int reload(CommandContext<FabricClientCommandSource> context) {
		try {
			configs.reload();
			controller.resetForWorldChange();
			feedback(context, "Reloaded configuration for " + configs.identity() + ".");
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int setFunction(CommandContext<FabricClientCommandSource> context, boolean enabled) {
		try {
			String name = StringArgumentType.getString(context, "functionName");
			PerimeterConfig config = configs.get();
			FunctionConfig functions = config.functions;
			switch (name) {
				case "collect_drops" -> functions.collectDrops = enabled;
				case "unload" -> functions.unload = enabled;
				case "eat" -> functions.eat = enabled;
				case "durability_recovery" -> functions.durabilityRecovery = enabled;
				case "resupply" -> functions.resupply = enabled;
				case "elytra_navigation" -> functions.elytraNavigation = enabled;
				case "sleep" -> functions.sleep = enabled;
				default -> throw new IllegalArgumentException("Unknown function: " + name + ".");
			}
			configs.save();
			controller.updateFunctions(functions);
			feedback(context, category("Function")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal("=").withStyle(ChatFormatting.GRAY))
					.append(valueComponent(enabled))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int showFunctions(CommandContext<FabricClientCommandSource> context) {
		try {
			FunctionConfig functions = configs.get().functions;
			feedback(context, category("Functions")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field("collect drops", functions.collectDrops)).append(separator())
					.append(field("unload", functions.unload)).append(separator())
					.append(field("eat", functions.eat)).append(separator())
					.append(field("durability recovery", functions.durabilityRecovery)).append(separator())
					.append(field("resupply", functions.resupply)).append(separator())
					.append(field("elytra navigation", functions.elytraNavigation)).append(separator())
					.append(field("sleep", functions.sleep))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int setAdvancedConfig(CommandContext<FabricClientCommandSource> context) {
		try {
			String key = StringArgumentType.getString(context, "advancedKey");
			double value = DoubleArgumentType.getDouble(context, "advancedValue");
			AdvancedConfig advanced = configs.get().advanced;
			setAdvancedValue(advanced, key, value);
			configs.save();
			controller.updateAdvanced(advanced);
			feedback(context, category("Advanced configuration")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field(key, advancedValue(advanced, key)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private int showAdvancedConfig(CommandContext<FabricClientCommandSource> context) {
		try {
			AdvancedConfig a = configs.get().advanced;
			feedback(context, advancedLine("Durability",
					"tool threshold", a.toolDurabilityThreshold,
					"elytra threshold", a.elytraDurabilityThreshold,
					"emergency flight threshold", a.emergencyFlightDurabilityThreshold));
			feedback(context, advancedLine("Consumables",
					"food level threshold", a.foodLevelThreshold,
					"health eating threshold", a.healthEatingThreshold,
					"food trigger", a.foodResupplyTrigger,
					"food target", a.foodResupplyTarget,
					"firework trigger", a.fireworkResupplyTrigger,
					"firework target", a.fireworkResupplyTarget));
			feedback(context, advancedLine("Mining and unloading",
					"drop radius", a.dropCollectionRadius,
					"drop stable seconds", a.dropCollectionStableSeconds,
					"reserved slots", a.inventoryReservedSlots,
					"blocks per empty slot", a.miningBlocksPerEmptySlot,
					"unload search radius", a.unloadLandingSearchRadius,
					"unload edge inset", a.unloadEdgeInset));
			feedback(context, advancedLine("Navigation",
					"elytra minimum distance", a.elytraNavigationMinDistance,
					"stall timeout seconds", a.navigationStallTimeoutSeconds,
					"retry count", a.navigationRetryCount,
					"flight retry count", a.flightRetryCount,
					"portal transition cost", a.portalTransitionCost,
					"portal transition timeout seconds", a.portalTransitionTimeoutSeconds));
			feedback(context, advancedLine("Portal exit",
					"timeout seconds", a.portalExitTimeoutSeconds,
					"minimum radius", a.portalExitMinRadius,
					"maximum radius", a.portalExitMaxRadius,
					"vertical radius", a.portalExitVerticalRadius));
			feedback(context, advancedLine("Interaction",
					"repair experience stable seconds", a.repairExperienceStableSeconds,
					"supply timeout seconds", a.supplyInteractionTimeoutSeconds,
					"furnace timeout seconds", a.furnaceInteractionTimeoutSeconds));
			return 1;
		} catch (RuntimeException exception) {
			return error(context, exception.getMessage());
		}
	}

	private CompletableFuture<Suggestions> suggestAdvancedValue(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
		try {
			String key = StringArgumentType.getString(context, "advancedKey");
			return SharedSuggestionProvider.suggest(new String[]{advancedValue(configs.get().advanced, key).toString()}, builder);
		} catch (RuntimeException exception) {
			return builder.buildFuture();
		}
	}

	private static void setAdvancedValue(AdvancedConfig a, String key, double value) {
		switch (key) {
			case "tool_durability_threshold" -> a.toolDurabilityThreshold = nonNegativeInteger(key, value);
			case "elytra_durability_threshold" -> a.elytraDurabilityThreshold = nonNegativeInteger(key, value);
			case "emergency_flight_durability_threshold" -> a.emergencyFlightDurabilityThreshold = nonNegativeInteger(key, value);
			case "food_level_threshold" -> a.foodLevelThreshold = rangedInteger(key, value, 0, 20);
			case "health_eating_threshold" -> a.healthEatingThreshold = ranged(key, value, 0.0, 20.0);
			case "food_resupply_trigger" -> {
				int result = nonNegativeInteger(key, value);
				if (result > a.foodResupplyTarget) throw new IllegalArgumentException(key + " cannot exceed food_resupply_target.");
				a.foodResupplyTrigger = result;
			}
			case "food_resupply_target" -> {
				int result = nonNegativeInteger(key, value);
				if (result < a.foodResupplyTrigger) throw new IllegalArgumentException(key + " cannot be lower than food_resupply_trigger.");
				a.foodResupplyTarget = result;
			}
			case "firework_resupply_trigger" -> {
				int result = nonNegativeInteger(key, value);
				if (result > a.fireworkResupplyTarget) throw new IllegalArgumentException(key + " cannot exceed firework_resupply_target.");
				a.fireworkResupplyTrigger = result;
			}
			case "firework_resupply_target" -> {
				int result = nonNegativeInteger(key, value);
				if (result < a.fireworkResupplyTrigger) throw new IllegalArgumentException(key + " cannot be lower than firework_resupply_trigger.");
				a.fireworkResupplyTarget = result;
			}
			case "drop_collection_radius" -> a.dropCollectionRadius = positiveInteger(key, value);
			case "drop_collection_stable_seconds" -> a.dropCollectionStableSeconds = positive(key, value);
			case "inventory_reserved_slots" -> a.inventoryReservedSlots = rangedInteger(key, value, 0, 35);
			case "mining_blocks_per_empty_slot" -> a.miningBlocksPerEmptySlot = positiveInteger(key, value);
			case "elytra_navigation_min_distance" -> a.elytraNavigationMinDistance = nonNegativeInteger(key, value);
			case "unload_landing_search_radius" -> a.unloadLandingSearchRadius = positiveInteger(key, value);
			case "unload_edge_inset" -> a.unloadEdgeInset = ranged(key, value, 0.0, 0.3);
			case "navigation_stall_timeout_seconds" -> a.navigationStallTimeoutSeconds = positive(key, value);
			case "navigation_retry_count" -> a.navigationRetryCount = nonNegativeInteger(key, value);
			case "portal_transition_cost" -> a.portalTransitionCost = ranged(key, value, 0.0, Double.MAX_VALUE);
			case "portal_transition_timeout_seconds" -> a.portalTransitionTimeoutSeconds = positive(key, value);
			case "portal_exit_timeout_seconds" -> a.portalExitTimeoutSeconds = positive(key, value);
			case "portal_exit_min_radius" -> {
				int result = positiveInteger(key, value);
				if (result > a.portalExitMaxRadius) throw new IllegalArgumentException(key + " cannot exceed portal_exit_max_radius.");
				a.portalExitMinRadius = result;
			}
			case "portal_exit_max_radius" -> {
				int result = positiveInteger(key, value);
				if (result < a.portalExitMinRadius) throw new IllegalArgumentException(key + " cannot be lower than portal_exit_min_radius.");
				a.portalExitMaxRadius = result;
			}
			case "portal_exit_vertical_radius" -> a.portalExitVerticalRadius = nonNegativeInteger(key, value);
			case "repair_experience_stable_seconds" -> a.repairExperienceStableSeconds = positive(key, value);
			case "supply_interaction_timeout_seconds" -> a.supplyInteractionTimeoutSeconds = positive(key, value);
			case "furnace_interaction_timeout_seconds" -> a.furnaceInteractionTimeoutSeconds = positive(key, value);
			case "flight_retry_count" -> a.flightRetryCount = positiveInteger(key, value);
			default -> throw new IllegalArgumentException("Unknown advanced configuration key: " + key + ".");
		}
	}

	private static Object advancedValue(AdvancedConfig a, String key) {
		return switch (key) {
			case "tool_durability_threshold" -> a.toolDurabilityThreshold;
			case "elytra_durability_threshold" -> a.elytraDurabilityThreshold;
			case "emergency_flight_durability_threshold" -> a.emergencyFlightDurabilityThreshold;
			case "food_level_threshold" -> a.foodLevelThreshold;
			case "health_eating_threshold" -> a.healthEatingThreshold;
			case "food_resupply_trigger" -> a.foodResupplyTrigger;
			case "food_resupply_target" -> a.foodResupplyTarget;
			case "firework_resupply_trigger" -> a.fireworkResupplyTrigger;
			case "firework_resupply_target" -> a.fireworkResupplyTarget;
			case "drop_collection_radius" -> a.dropCollectionRadius;
			case "drop_collection_stable_seconds" -> a.dropCollectionStableSeconds;
			case "inventory_reserved_slots" -> a.inventoryReservedSlots;
			case "mining_blocks_per_empty_slot" -> a.miningBlocksPerEmptySlot;
			case "elytra_navigation_min_distance" -> a.elytraNavigationMinDistance;
			case "unload_landing_search_radius" -> a.unloadLandingSearchRadius;
			case "unload_edge_inset" -> a.unloadEdgeInset;
			case "navigation_stall_timeout_seconds" -> a.navigationStallTimeoutSeconds;
			case "navigation_retry_count" -> a.navigationRetryCount;
			case "portal_transition_cost" -> a.portalTransitionCost;
			case "portal_transition_timeout_seconds" -> a.portalTransitionTimeoutSeconds;
			case "portal_exit_timeout_seconds" -> a.portalExitTimeoutSeconds;
			case "portal_exit_min_radius" -> a.portalExitMinRadius;
			case "portal_exit_max_radius" -> a.portalExitMaxRadius;
			case "portal_exit_vertical_radius" -> a.portalExitVerticalRadius;
			case "repair_experience_stable_seconds" -> a.repairExperienceStableSeconds;
			case "supply_interaction_timeout_seconds" -> a.supplyInteractionTimeoutSeconds;
			case "furnace_interaction_timeout_seconds" -> a.furnaceInteractionTimeoutSeconds;
			case "flight_retry_count" -> a.flightRetryCount;
			default -> throw new IllegalArgumentException("Unknown advanced configuration key: " + key + ".");
		};
	}

	private int showConfig(CommandContext<FabricClientCommandSource> context) {
		try {
			PerimeterConfig config = configs.get();
			feedback(context, category("Configuration")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field("digging Y", rangeValue(config.diggingMinY, config.diggingMaxY))).append(separator())
					.append(field("detected columns", config.detectedArea == null ? "unset" : config.detectedArea.columnCount)).append(separator())
					.append(field("unloading points", config.unloadingPoints.size()))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, category("Liquid")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field("policy", config.liquidPolicy)).append(separator())
					.append(field("sealing blocks", listValue(config.sealingBlocks)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, category("Resources")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field("foods", listValue(config.foods))).append(separator())
					.append(field("durability recovery", config.durabilityRecoveryMode))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, Component.literal("Navigation: allowSprint=true, allowParkour=true, breaking enabled while navigating to perimeter portals and placement disabled for all non-mining destinations.")
					.withStyle(ChatFormatting.GRAY));
			feedback(context, category("Unloading")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field("whitelist", listValue(config.unloadingWhitelist)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			feedback(context, category("Locations")
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(field("consumable supply chest", config.consumableSupplyPoint)).append(separator())
					.append(field("durability supply chest", config.durabilitySupplyPoint)).append(separator())
					.append(field("bed", config.bedPoint)).append(separator())
					.append(field("perimeter portals", pairValue(config.perimeterPortalOverworld, config.perimeterPortalNether))).append(separator())
					.append(field("repair portals", pairValue(config.repairPortalOverworld, config.repairPortalNether))).append(separator())
					.append(field("furnace row", pairValue(config.furnaceRowStart, config.furnaceRowEnd)))
					.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			if (config.unloadingPoints.isEmpty()) {
				feedback(context, category("Unloading points")
						.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
						.append(valueComponent("none"))
						.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
			} else {
				feedback(context, category("Unloading points").append(Component.literal(":").withStyle(ChatFormatting.GRAY)));
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
		return Component.literal(value).withStyle(ChatFormatting.AQUA);
	}

	private static MutableComponent advancedLine(String name, Object... entries) {
		MutableComponent result = category(name).append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
		for (int index = 0; index < entries.length; index += 2) {
			if (index > 0) result.append(separator());
			result.append(field(entries[index].toString(), entries[index + 1]));
		}
		return result.append(Component.literal(".").withStyle(ChatFormatting.GRAY));
	}

	private static int nonNegativeInteger(String key, double value) {
		return rangedInteger(key, value, 0, Integer.MAX_VALUE);
	}

	private static int positiveInteger(String key, double value) {
		return rangedInteger(key, value, 1, Integer.MAX_VALUE);
	}

	private static int rangedInteger(String key, double value, int minimum, int maximum) {
		if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(key + " must be an integer from " + minimum + " to " + maximum + ".");
		}
		return (int) value;
	}

	private static double positive(String key, double value) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(key + " must be a positive finite number.");
		}
		return value;
	}

	private static double ranged(String key, double value, double minimum, double maximum) {
		if (!Double.isFinite(value) || value < minimum || value > maximum) {
			throw new IllegalArgumentException(key + " must be from " + minimum + " to " + maximum + ".");
		}
		return value;
	}

	private static MutableComponent field(String name, Object value) {
		return field(name, valueComponent(value));
	}

	private static MutableComponent field(String name, MutableComponent value) {
		return Component.literal(name).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("=").withStyle(ChatFormatting.GRAY))
				.append(value);
	}

	private static MutableComponent valueComponent(Object value) {
		if (value == null) return clickableValue("unset", ChatFormatting.RED);
		if (value instanceof Boolean flag) {
			return clickableValue(Boolean.toString(flag), flag ? ChatFormatting.GREEN : ChatFormatting.RED);
		}
		String text = value.toString();
		if (text.equalsIgnoreCase("unset") || text.equalsIgnoreCase("none") || text.equalsIgnoreCase("false")) {
			return clickableValue(text, ChatFormatting.RED);
		}
		return clickableValue(text, ChatFormatting.GREEN);
	}

	private static MutableComponent clickableValue(String value, ChatFormatting color) {
		return Component.literal(value).withStyle(color)
				.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(value)));
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
		return Component.literal(", ").withStyle(ChatFormatting.GRAY);
	}

	private static void feedback(CommandContext<FabricClientCommandSource> context, String message) {
		context.getSource().sendFeedback(Component.literal(message));
	}

	private static void feedback(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message);
	}

	private static int error(CommandContext<FabricClientCommandSource> context, String message) {
		context.getSource().sendFeedback(Component.literal("Error: " + (message == null ? "Unknown error." : message)));
		return 0;
	}
}
