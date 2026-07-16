package hackerrouter.perimeterdigger.client.state;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.IAreaMineProcess;
import baritone.api.process.area.AreaMiningLiquidPolicy;
import baritone.api.process.area.AreaMiningOptions;
import baritone.api.process.area.AreaMiningStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import hackerrouter.perimeterdigger.client.config.PerimeterConfig;
import hackerrouter.perimeterdigger.PerimeterDigger;
import hackerrouter.perimeterdigger.client.config.FunctionConfig;
import hackerrouter.perimeterdigger.client.config.AdvancedConfig;
import hackerrouter.perimeterdigger.client.config.PositionConfig;
import hackerrouter.perimeterdigger.client.config.UnloadingPointConfig;
import hackerrouter.perimeterdigger.client.translation.LocalizedMessage;
import hackerrouter.perimeterdigger.client.translation.Translations;
import hackerrouter.perimeterdigger.client.navigation.InteractionPositionFinder;
import hackerrouter.perimeterdigger.client.navigation.NavigationService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AutomationController {
	private static final int MONITOR_INTERVAL_TICKS = 10;
	private static final int OFFHAND_INVENTORY_SLOT = 40;
	private static final int OFFHAND_MENU_SLOT = 45;
	private static final int CHEST_MENU_SLOT = 6;
	private static final int STATE_HISTORY_LIMIT = 64;
	private AutomationState state = AutomationState.IDLE;
	private AutomationState stateBeforeEating = AutomationState.IDLE;
	private LocalizedMessage detail = Translations.DETAIL.message("stopped");
	private Instant changedAt = Instant.now();
	private IBaritone baritone;
	private IAreaMineProcess miningProcess;
	private ConfiguredColumnarArea area;
	private AreaMiningLiquidPolicy liquidPolicy;
	private String durabilityRecoveryMode = "repair_portal";
	private List<Block> sealingBlocks = List.of();
	private Set<Item> allowedFoods = Set.of();
	private Set<Item> unloadingWhitelist = Set.of();
	private PositionConfig consumableSupplyPoint;
	private PositionConfig durabilitySupplyPoint;
	private PositionConfig bedPoint;
	private PositionConfig perimeterPortalOverworld;
	private PositionConfig perimeterPortalNether;
	private PositionConfig repairPortalOverworld;
	private PositionConfig repairPortalNether;
	private PositionConfig furnaceRowStart;
	private PositionConfig furnaceRowEnd;
	private List<UnloadFlow.NamedPoint> unloadingPoints = List.of();
	private BlockPos miningReturnPoint;
	private ResourceKey<Level> miningReturnDimension;
	private boolean returningByElytra;
	private int returnFlightAttempts;
	private BlockPos activeReturnTarget;
	private ReturnWaypoint activeReturnWaypoint;
	private ReturnWaypoint returnPortalWaypoint;
	private ReturnAction returnAction = ReturnAction.NONE;
	private Integer savedElytraMinimumDurability;
	private Integer savedElytraMinFireworksBeforeLanding;
	private Boolean savedRepairAllowBreak;
	private Boolean savedRepairAllowPlace;
	private final ArrayDeque<InventoryClick> inventoryClicks = new ArrayDeque<>();
	private int tickCounter;
	private AutomationState watchdogState;
	private BlockPos watchdogTarget;
	private Vec3 watchdogPosition;
	private int watchdogStationaryScans;
	private int watchdogRetries;
	private int stableInventoryScans;
	private int lastInventoryItemCount;
	private boolean miningCompletePending;
	private boolean collectDropsEnabled = true;
	private boolean unloadEnabled = true;
	private boolean eatEnabled = true;
	private boolean durabilityRecoveryEnabled = true;
	private boolean crossDimensionRepairEnabled = true;
	private boolean resupplyEnabled = true;
	private boolean elytraNavigationEnabled = true;
	private boolean sleepEnabled;
	private AdvancedConfig advanced = new AdvancedConfig();
	private final ArrayDeque<StateTransition> stateHistory = new ArrayDeque<>();
	private final InteractionPositionFinder interactionPositionFinder = new InteractionPositionFinder(4, 20.25);
	private final InteractionPositionFinder bedInteractionPositionFinder = new InteractionPositionFinder(2, 9.0);
	private final NavigationService navigation = new NavigationService();
	private final SupplyFlow supply = new SupplyFlow();
	private final UnloadFlow unload = new UnloadFlow();
	private final RepairFlow repair = new RepairFlow();
	private final SleepFlow sleep = new SleepFlow();

	public List<String> start(PerimeterConfig config) {
		transition(AutomationState.VALIDATING, Translations.DETAIL.message("validating_mining"));
		List<String> missing = validate(config);
		if (!missing.isEmpty()) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("invalid_configuration", String.join(", ", missing)));
			return missing;
		}
		this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		this.miningProcess = baritone.getAreaMineProcess();
		navigation.bind(baritone);
		this.area = new ConfiguredColumnarArea(config.detectedArea, config.diggingMinY, config.diggingMaxY);
		this.liquidPolicy = policy(config.liquidPolicy);
		this.durabilityRecoveryMode = config.durabilityRecoveryMode;
		this.sealingBlocks = sealingBlocks(config.sealingBlocks);
		this.allowedFoods = foods(config.foods);
		this.unloadingWhitelist = items(config.unloadingWhitelist);
		this.consumableSupplyPoint = config.consumableSupplyPoint;
		this.durabilitySupplyPoint = config.durabilitySupplyPoint;
		this.bedPoint = config.bedPoint;
		updateFunctions(config.functions);
		updateAdvanced(config.advanced);
		loadRepairConfiguration(config);
		this.unloadingPoints = config.unloadingPoints.entrySet().stream()
				.map(entry -> new UnloadFlow.NamedPoint(entry.getKey(), entry.getValue()))
				.toList();
		this.miningCompletePending = false;
		this.unload.debugOnly = false;
		this.supply.debugOnly = false;
		this.repair.debugOnly = false;
		this.sleep.debugOnly = false;
		this.stableInventoryScans = 0;
		this.inventoryClicks.clear();
		BaritoneAPI.getSettings().itemSaver.value = true;
		BaritoneAPI.getSettings().itemSaverThreshold.value = advanced.toolDurabilityThreshold;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.elytraDurabilityThreshold;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		startMiningCycle();
		return List.of();
	}

	public List<String> debugStage5(PerimeterConfig config) {
		transition(AutomationState.VALIDATING, Translations.DETAIL.message("validating_unload_debug"));
		if (config.unloadingPoints.isEmpty()) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("invalid_configuration", "unloading_points"));
			return List.of("unloading_points");
		}
		stopProcesses();
		this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		this.miningProcess = baritone.getAreaMineProcess();
		navigation.bind(baritone);
		this.unloadingWhitelist = items(config.unloadingWhitelist);
		this.unloadingPoints = config.unloadingPoints.entrySet().stream()
				.map(entry -> new UnloadFlow.NamedPoint(entry.getKey(), entry.getValue()))
				.toList();
		this.inventoryClicks.clear();
		this.miningCompletePending = false;
		this.unload.debugOnly = true;
		updateFunctions(config.functions);
		updateAdvanced(config.advanced);
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.elytraDurabilityThreshold;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		requestUnload();
		return state == AutomationState.ERROR ? List.of("unload_start") : List.of();
	}

	public List<String> debugStage6(PerimeterConfig config, boolean durability) {
		SupplyFlow.Kind kind = durability ? SupplyFlow.Kind.DURABILITY : SupplyFlow.Kind.CONSUMABLES;
		PositionConfig point = durability ? config.durabilitySupplyPoint : config.consumableSupplyPoint;
		transition(AutomationState.VALIDATING, Translations.DETAIL.message("validating_supply_debug"));
		if (point == null) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("requested_supply_missing"));
			return List.of(durability ? "durability_supply_point" : "consumable_supply_point");
		}
		stopProcesses();
		this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		this.miningProcess = baritone.getAreaMineProcess();
		navigation.bind(baritone);
		this.allowedFoods = foods(config.foods);
		this.consumableSupplyPoint = config.consumableSupplyPoint;
		this.durabilitySupplyPoint = config.durabilitySupplyPoint;
		updateFunctions(config.functions);
		updateAdvanced(config.advanced);
		this.inventoryClicks.clear();
		this.supply.debugOnly = true;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.elytraDurabilityThreshold;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		if (kind == SupplyFlow.Kind.DURABILITY && captureDurabilitySupplyPlan().targetHealthyCounts().isEmpty()) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("no_debug_supply_target"));
			return List.of("low_durability_item");
		}
		beginSupply(kind, true);
		return state == AutomationState.ERROR ? List.of("supply_start") : List.of();
	}

	public List<String> debugStage7(PerimeterConfig config) {
		transition(AutomationState.VALIDATING, Translations.DETAIL.message("validating_repair_debug"));
		List<String> missing = validateRepairConfiguration(config);
		if (!missing.isEmpty()) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("invalid_repair_configuration", String.join(", ", missing)));
			return missing;
		}
		stopProcesses();
		bindBaritone();
		loadRepairConfiguration(config);
		this.allowedFoods = foods(config.foods);
		updateFunctions(config.functions);
		updateAdvanced(config.advanced);
		this.repair.debugOnly = true;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.elytraDurabilityThreshold;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		beginRepairFlow(true);
		if (state == AutomationState.ERROR) throw new IllegalStateException(detail.component().getString());
		return List.of();
	}

	public List<String> debugSleep(PerimeterConfig config) {
		transition(AutomationState.VALIDATING, Translations.DETAIL.message("validating_sleep_debug"));
		if (config.bedPoint == null) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("bed_point_missing"));
			return List.of("bed_point");
		}
		stopProcesses();
		bindBaritone();
		this.bedPoint = config.bedPoint;
		updateFunctions(config.functions);
		updateAdvanced(config.advanced);
		this.sleep.debugOnly = true;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.elytraDurabilityThreshold;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		if (!isBedTime()) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("sleep_debug_wrong_time"));
			return List.of("night_time");
		}
		beginSleepFlow(true);
		return state == AutomationState.ERROR ? List.of("sleep_start") : List.of();
	}

	public void tick() {
		if (baritone == null || baritone.getPlayerContext().player() == null) return;
		executeInventoryClick();
		if (state == AutomationState.EATING) {
			tickEating();
			return;
		}
		if (state == AutomationState.SLEEPING) {
			tickSleeping();
			return;
		}
		if (state == AutomationState.UNLOADING) {
			tickUnloading();
			return;
		}
		if (state == AutomationState.POSITIONING_FOR_UNLOAD) {
			tickPositioningForUnload();
			return;
		}
		if (state == AutomationState.RESUPPLYING || state == AutomationState.SWAPPING_DURABILITY_AT_SUPPLY) {
			tickSupplyInteraction();
			return;
		}
		if (state == AutomationState.REPAIRING) {
			tickRepairing();
			return;
		}
		if (state == AutomationState.ENTERING_PERIMETER_PORTAL || state == AutomationState.ENTERING_REPAIR_PORTAL) {
			tickEnteringRepairPortal();
			return;
		}
		if (++tickCounter % MONITOR_INTERVAL_TICKS != 0) return;
		if (watchdogNavigationTarget() != null && !baritone.getPlayerContext().player().isFallFlying() && !isInsideNetherPortal()
				&& shouldEat()
				&& eatEnabled && beginEating()) return;
		if (tickNavigationWatchdog()) return;
		if (state == AutomationState.NAVIGATING_TO_UNLOAD) {
			tickNavigateToUnload();
			return;
		}
		if (state == AutomationState.APPROACHING_UNLOAD) {
			tickApproachingUnload();
			return;
		}
		if (state == AutomationState.RETURNING_TO_MINE) {
			tickReturningToMine();
			return;
		}
		if (state == AutomationState.NAVIGATING_TO_RESUPPLY || state == AutomationState.NAVIGATING_TO_DURABILITY_SUPPLY) {
			tickSupplyNavigation();
			return;
		}
		if (state == AutomationState.NAVIGATING_TO_BED) {
			tickBedNavigation();
			return;
		}
		if (state == AutomationState.NAVIGATING_TO_PERIMETER_PORTAL || state == AutomationState.NAVIGATING_TO_REPAIR_PORTAL
				|| state == AutomationState.NAVIGATING_TO_REPAIR_MACHINE) {
			tickRepairNavigation();
			return;
		}
		if (state == AutomationState.CLEARING_REPAIR_PORTAL) {
			tickClearRepairPortal();
			return;
		}
		if (state == AutomationState.IDLE || state == AutomationState.ERROR || state == AutomationState.COMPLETE || state == AutomationState.PAUSED
				) return;
		if (!inventoryClicks.isEmpty()) return;
		ResourceCheck resources = inspectResources();
		if (sleepEnabled && bedPoint != null && isBedTime()) {
			beginSleepFlow(false);
			return;
		}
		if (eatEnabled && shouldEat() && beginEating()) return;
		if (resources.replacement() != null) {
			performReplacement(resources.replacement());
			return;
		}
		if (unloadEnabled && (state == AutomationState.MINING || state == AutomationState.COLLECTING_DROPS) && emptyInventorySlots() == 0) {
			requestUnload();
			return;
		}
		if (resupplyEnabled && consumableSupplyPoint != null
				&& (configuredFoodCount() <= advanced.foodResupplyTrigger
				|| elytraNavigationEnabled && fireworkCount() <= advanced.fireworkResupplyTrigger)) {
			beginSupply(SupplyFlow.Kind.CONSUMABLES, false);
			return;
		}
		if (durabilityRecoveryEnabled && resources.repairRequired()) {
			if (durabilityRecoveryMode.equals("supply_point") && durabilitySupplyPoint != null) {
				beginSupply(SupplyFlow.Kind.DURABILITY, false);
				return;
			} else if (durabilityRecoveryMode.equals("repair_portal") && hasUsableRepairConfiguration()) {
				beginRepairFlow(false);
				return;
			}
		}
		if (resupplyEnabled && consumableSupplyPoint != null
				&& baritone.getPlayerContext().player().getFoodData().getFoodLevel() <= advanced.foodLevelThreshold) {
			beginSupply(SupplyFlow.Kind.CONSUMABLES, false);
			return;
		}
		if (state == AutomationState.MINING) synchronizeMining();
		else if (state == AutomationState.COLLECTING_DROPS) tickDropCollection();
	}

	public void stop() {
		if (baritone != null && baritone.getPlayerContext().player() != null
				&& !(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		stopProcesses();
		baritone = null;
		miningProcess = null;
		navigation.unbind();
		area = null;
		miningReturnPoint = null;
		miningReturnDimension = null;
		activeReturnTarget = null;
		activeReturnWaypoint = null;
		returnPortalWaypoint = null;
		inventoryClicks.clear();
		resetNavigationWatchdog();
		unload.reset();
		clearSleepContext();
		clearSupplyContext();
		clearRepairContext();
		transition(AutomationState.IDLE, Translations.DETAIL.message("stopped"));
	}

	public void clearCachedState() {
		stop();
		stateBeforeEating = AutomationState.IDLE;
		unload.reset();
		stableInventoryScans = 0;
		lastInventoryItemCount = 0;
		miningCompletePending = false;
		tickCounter = 0;
		transition(AutomationState.IDLE, Translations.DETAIL.message("cache_cleared"));
	}

	public void updateFunctions(FunctionConfig functions) {
		FunctionConfig value = functions == null ? new FunctionConfig() : functions;
		collectDropsEnabled = value.collectDrops;
		unloadEnabled = value.unload;
		eatEnabled = value.eat;
		durabilityRecoveryEnabled = value.durabilityRecovery;
		crossDimensionRepairEnabled = value.crossDimensionRepair;
		resupplyEnabled = value.resupply;
		elytraNavigationEnabled = value.elytraNavigation;
		sleepEnabled = value.sleep;
	}

	public void updateAdvanced(AdvancedConfig advanced) {
		this.advanced = advanced == null ? new AdvancedConfig() : advanced;
		if (baritone != null) {
			BaritoneAPI.getSettings().itemSaverThreshold.value = this.advanced.toolDurabilityThreshold;
			BaritoneAPI.getSettings().elytraMinimumDurability.value = this.advanced.elytraDurabilityThreshold;
		}
	}

	public void pause() {
		if (miningProcess == null || state != AutomationState.MINING) throw new IllegalStateException("No running mining task can be paused.");
		miningProcess.pause();
		transition(AutomationState.PAUSED, Translations.DETAIL.message("mining_paused_manually"));
	}

	public void resume() {
		if (miningProcess == null || state != AutomationState.PAUSED) throw new IllegalStateException("No paused mining task can be resumed.");
		AreaMiningStatus status = miningProcess.getAreaMiningStatus();
		if (status.pauseReason() == AreaMiningStatus.PauseReason.BLOCK_LIMIT_REACHED) beginDropCollection(false);
		else {
			miningProcess.resume();
			transition(AutomationState.MINING, Translations.DETAIL.message("mining_resumed"));
		}
	}

	public AutomationState state() {
		return state;
	}

	public net.minecraft.network.chat.MutableComponent detail() {
		BlockPos target = baritone == null ? null : watchdogNavigationTarget();
		return target == null ? detail.component() : detail.component().append(Translations.DETAIL.tr("watchdog", target.toShortString(), watchdogRetries));
	}

	public Instant changedAt() {
		return changedAt;
	}

	public List<StateTransition> stateHistory() {
		return List.copyOf(stateHistory);
	}

	public void clearStateHistory() {
		stateHistory.clear();
	}

	public void handleTickFailure(RuntimeException exception) {
		emergencyCleanup();
		String message = exception.getMessage();
		String summary = exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
		recordTransition(state, AutomationState.ERROR, Translations.DETAIL.message("unhandled_error_short", summary));
		state = AutomationState.ERROR;
		detail = Translations.DETAIL.message("unhandled_error", summary);
		changedAt = Instant.now();
	}

	public void resetForWorldChange() {
		if (baritone != null && baritone.getPlayerContext().player() != null
				&& !(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		stopProcesses();
		baritone = null;
		miningProcess = null;
		navigation.unbind();
		area = null;
		miningReturnPoint = null;
		miningReturnDimension = null;
		activeReturnTarget = null;
		activeReturnWaypoint = null;
		returnPortalWaypoint = null;
		inventoryClicks.clear();
		resetNavigationWatchdog();
		unload.debugOnly = false;
		supply.debugOnly = false;
		sleep.debugOnly = false;
		clearSleepContext();
		clearSupplyContext();
		clearRepairContext();
		transition(AutomationState.IDLE, Translations.DETAIL.message("world_changed"));
	}

	private void synchronizeMining() {
		AreaMiningStatus status = miningProcess.getAreaMiningStatus();
		switch (status.state()) {
			case RUNNING -> synchronize(AutomationState.MINING, progress(status));
			case PAUSED -> {
				if (status.pauseReason() == AreaMiningStatus.PauseReason.BLOCK_LIMIT_REACHED) beginDropCollection(false);
				else synchronize(AutomationState.PAUSED, Translations.DETAIL.message("mining_paused",
						status.pauseReason().name().toLowerCase(Locale.ROOT), progress(status).component()));
			}
			case COMPLETE -> beginDropCollection(true);
			case CANCELLED -> transition(AutomationState.ERROR, Translations.DETAIL.message("mining_cancelled"));
			case IDLE -> transition(AutomationState.ERROR, Translations.DETAIL.message("mining_stopped_unexpectedly"));
		}
	}

	private void startMiningCycle() {
		int emptySlots = emptyInventorySlots();
		if (emptySlots == 0 && unloadEnabled) {
			requestUnload();
			return;
		}
		long blockLimit = !collectDropsEnabled || emptySlots == 0
				? Long.MAX_VALUE
				: Math.max(1L, (long) Math.max(0, emptySlots - advanced.inventoryReservedSlots) * advanced.miningBlocksPerEmptySlot);
		navigation.stopWalking();
		miningProcess.mineArea(area, new AreaMiningOptions(liquidPolicy, sealingBlocks, blockLimit));
		transition(AutomationState.MINING, Translations.DETAIL.message("mining_started", blockLimit, emptySlots));
	}

	private void beginDropCollection(boolean miningComplete) {
		miningCompletePending = miningComplete;
		miningProcess.cancel();
		if (!collectDropsEnabled) {
			navigation.stopWalking();
			if (miningComplete) {
				if (unloadEnabled) requestUnload();
				else transition(AutomationState.COMPLETE, Translations.DETAIL.message("mining_complete_collection_unload_disabled"));
			} else {
				startMiningCycle();
			}
			return;
		}
		disablePlacementForNavigation();
		stableInventoryScans = 0;
		lastInventoryItemCount = inventoryItemCount();
		transition(AutomationState.COLLECTING_DROPS, Translations.DETAIL.message("collecting_drops", advanced.dropCollectionRadius));
		tickDropCollection();
	}

	private void tickDropCollection() {
		if (emptyInventorySlots() == 0) {
			if (unloadEnabled) requestUnload();
			else {
				restoreAllowPlace();
				if (miningCompletePending) transition(AutomationState.COMPLETE, Translations.DETAIL.message("mining_collection_complete_unload_disabled"));
				else startMiningCycle();
			}
			return;
		}
		int itemCount = inventoryItemCount();
		if (itemCount > lastInventoryItemCount) stableInventoryScans = 0;
		else stableInventoryScans++;
		lastInventoryItemCount = itemCount;
		List<Goal> goals = nearbyDropGoals();
		if (stableInventoryScans >= dropStableScanLimit()) {
			navigation.stopWalking();
			if (unloadEnabled) requestUnload();
			else {
				restoreAllowPlace();
				if (miningCompletePending) transition(AutomationState.COMPLETE, Translations.DETAIL.message("mining_collection_complete_unload_disabled"));
				else startMiningCycle();
			}
			return;
		}
		if (goals.isEmpty()) {
			navigation.stopWalking();
			synchronize(AutomationState.COLLECTING_DROPS, Translations.DETAIL.message("waiting_inventory_growth", itemCount, stableInventoryScans, dropStableScanLimit()));
			return;
		}
		navigation.walk(new GoalComposite(goals.toArray(new Goal[0])));
		synchronize(AutomationState.COLLECTING_DROPS, Translations.DETAIL.message("collecting_drop_targets", goals.size(), itemCount, stableInventoryScans, dropStableScanLimit()));
	}

	private List<Goal> nearbyDropGoals() {
		List<Goal> goals = new ArrayList<>();
		double radiusSquared = (double) advanced.dropCollectionRadius * advanced.dropCollectionRadius;
		for (Entity entity : baritone.getPlayerContext().entities()) {
			double dx = entity.getX() - baritone.getPlayerContext().player().getX();
			double dz = entity.getZ() - baritone.getPlayerContext().player().getZ();
			if (entity instanceof ItemEntity && entity.isAlive() && dx * dx + dz * dz <= radiusSquared) {
				goals.add(new GoalBlock(new BetterBlockPos(entity.position().x, entity.position().y + 0.1, entity.position().z)));
			}
		}
		return goals;
	}

	private void bindBaritone() {
		this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		this.miningProcess = baritone.getAreaMineProcess();
		navigation.bind(baritone);
	}

	private void loadRepairConfiguration(PerimeterConfig config) {
		this.perimeterPortalOverworld = config.perimeterPortalOverworld;
		this.perimeterPortalNether = config.perimeterPortalNether;
		this.repairPortalOverworld = config.repairPortalOverworld;
		this.repairPortalNether = config.repairPortalNether;
		this.furnaceRowStart = config.furnaceRowStart;
		this.furnaceRowEnd = config.furnaceRowEnd;
	}

	private void beginRepairFlow(boolean debug) {
		if (crossDimensionRepairEnabled && !baritone.getPlayerContext().world().dimension().equals(Level.OVERWORLD)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("repair_requires_overworld"));
			return;
		}
		if (!validateRepairConfigurationValues()) return;
		repair.previousState = state;
		repair.debugOnly = debug;
		repair.crossDimension = crossDimensionRepairEnabled;
		miningReturnPoint = baritone.getPlayerContext().playerFeet();
		miningReturnDimension = baritone.getPlayerContext().world().dimension();
		returnAction = ReturnAction.AFTER_REPAIR;
		repair.plan = captureRepairPlan(debug);
		if (repair.plan.targetCounts().isEmpty()) {
			transition(AutomationState.ERROR, Translations.DETAIL.message(debug ? "no_debug_repair_target" : "no_repair_target"));
			return;
		}
		repair.furnaces = furnaceRow();
		repair.furnaceIndex = 0;
		stopProcesses();
		applyRepairRestrictions();
		if (repair.crossDimension) {
			repair.stage = RepairFlow.Stage.OUTBOUND_PERIMETER_PORTAL;
			startRepairNavigation(position(perimeterPortalOverworld), AutomationState.NAVIGATING_TO_PERIMETER_PORTAL);
		} else {
			repair.stage = RepairFlow.Stage.REPAIR_MACHINE;
			startRepairNavigation(closestFurnaceStand(repair.furnaces.getFirst()), AutomationState.NAVIGATING_TO_REPAIR_MACHINE);
		}
	}

	private boolean validateRepairConfigurationValues() {
		if (furnaceRowStart == null || furnaceRowEnd == null || crossDimensionRepairEnabled
				&& (perimeterPortalOverworld == null || perimeterPortalNether == null || repairPortalOverworld == null
				|| repairPortalNether == null)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("repair_configuration_incomplete"));
			return false;
		}
		return true;
	}

	private boolean hasUsableRepairConfiguration() {
		if (furnaceRowStart == null || furnaceRowEnd == null || crossDimensionRepairEnabled
				&& (perimeterPortalOverworld == null || perimeterPortalNether == null || repairPortalOverworld == null
				|| repairPortalNether == null)) return false;
		int changedAxes = (furnaceRowStart.x == furnaceRowEnd.x ? 0 : 1)
				+ (furnaceRowStart.y == furnaceRowEnd.y ? 0 : 1)
				+ (furnaceRowStart.z == furnaceRowEnd.z ? 0 : 1);
		return changedAxes <= 1;
	}

	private void startRepairNavigation(BlockPos target, AutomationState navigationState) {
		repair.navigationTarget = target;
		repair.flying = false;
		repair.flightAttempts = 0;
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		if (player.distSqr(target) > navigationFlightDistanceSquared() && canFlyForRepair()) {
			startFlyingForRepair(navigationState);
			return;
		}
		startWalkingForRepair(navigationState);
	}

	private boolean canFlyForRepair() {
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		return elytraNavigationEnabled && navigation.isFlightLoaded() && chest.is(Items.ELYTRA)
				&& remainingDurability(chest) > advanced.emergencyFlightDurabilityThreshold
				&& fireworkCount() > 0;
	}

	private void prepareRepairFlightSettings() {
		if (savedElytraMinimumDurability == null) savedElytraMinimumDurability = BaritoneAPI.getSettings().elytraMinimumDurability.value;
		if (savedElytraMinFireworksBeforeLanding == null) savedElytraMinFireworksBeforeLanding = BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.emergencyFlightDurabilityThreshold;
		BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value = -1;
	}

	private void startFlyingForRepair(AutomationState navigationState) {
		restoreAllowPlace();
		if (navigationState == AutomationState.NAVIGATING_TO_PERIMETER_PORTAL) enableBreakingForNavigation();
		prepareRepairFlightSettings();
		repair.flying = true;
		repair.flightAttempts++;
		navigation.fly(repair.navigationTarget);
		transition(navigationState, Translations.DETAIL.message("flying_repair_target", repair.navigationTarget.toShortString()));
	}

	private void startWalkingForRepair(AutomationState navigationState) {
		repair.flying = false;
		if (navigationState == AutomationState.NAVIGATING_TO_PERIMETER_PORTAL) enableBreakingForNavigation();
		else disablePlacementForNavigation();
		if (repair.stage == RepairFlow.Stage.REPAIR_MACHINE) {
			List<BlockPos> stands = furnaceStandPositions(repair.furnaces.get(repair.furnaceIndex));
			if (stands.isEmpty()) navigation.walk(new GoalBlock(repair.navigationTarget));
			else navigation.walk(new GoalComposite(stands.stream().map(GoalBlock::new).toArray(Goal[]::new)));
		} else {
			navigation.walk(new GoalBlock(repair.navigationTarget));
		}
		transition(navigationState, Translations.DETAIL.message("walking_repair_target", repair.navigationTarget.toShortString()));
	}

	private void tickRepairNavigation() {
		boolean reached = repair.stage == RepairFlow.Stage.REPAIR_MACHINE
				? canReachFurnace(repair.furnaces.get(repair.furnaceIndex))
				: baritone.getPlayerContext().playerFeet().equals(repair.navigationTarget);
		if (reached) {
			navigation.stopFlying();
			navigation.stopWalking();
			restoreRepairNavigationSettings();
			if (repair.stage == RepairFlow.Stage.REPAIR_MACHINE) beginFurnaceInteraction();
			else if (repair.stage == RepairFlow.Stage.RETURN_TO_MACHINE_TAKEOFF) startRepairPortalReturn();
			else beginPortalEntry();
			return;
		}
		if (repair.flying) {
			if (navigation.isFlying()) return;
			restoreRepairNavigationSettings();
			startWalkingForRepair(state);
			return;
		}
		if (navigation.isWalking()) return;
		restoreAllowPlace();
		if (repair.navigationTarget.getY() > baritone.getPlayerContext().playerFeet().y
				&& repair.flightAttempts < advanced.flightRetryCount && canFlyForRepair()) {
			startFlyingForRepair(state);
			return;
		}
		restoreRepairNavigationSettings();
		transition(AutomationState.ERROR, Translations.DETAIL.message("repair_target_unreachable", repair.navigationTarget.toShortString()));
	}

	private void restoreRepairNavigationSettings() {
		restoreAllowPlace();
		restoreElytraMinimumDurability();
		restoreElytraFireworkReserve();
	}

	private void beginPortalEntry() {
		restoreRepairNavigationSettings();
		repair.portalWaitTicks = 0;
		repair.portalExitCandidates = List.of();
		repair.postPortalNavigationTarget = null;
		repair.postPortalNavigationState = null;
		AutomationState portalState = repair.stage == RepairFlow.Stage.OUTBOUND_PERIMETER_PORTAL || repair.stage == RepairFlow.Stage.RETURN_PERIMETER_PORTAL
				? AutomationState.ENTERING_PERIMETER_PORTAL : AutomationState.ENTERING_REPAIR_PORTAL;
		transition(portalState, Translations.DETAIL.message("waiting_repair_portal", repair.navigationTarget.toShortString()));
	}

	private void tickEnteringRepairPortal() {
		if (++repair.portalWaitTicks > ticks(advanced.portalTransitionTimeoutSeconds)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("repair_portal_timeout"));
			return;
		}
		baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
	}

	public void handleWorldChange() {
		if (returnPortalWaypoint != null
				&& (state == AutomationState.ENTERING_PERIMETER_PORTAL || state == AutomationState.ENTERING_REPAIR_PORTAL)) {
			stopProcesses();
			bindBaritone();
			if (repair.stage != null) applyRepairRestrictions();
			restoreRepairNavigationSettings();
			BlockPos arrivalPortal = positionForReturnWaypoint(returnPortalWaypoint.counterpart());
			returnPortalWaypoint = null;
			beginClearRepairPortal(arrivalPortal, null, null);
			return;
		}
		if (repair.stage == null || state != AutomationState.ENTERING_PERIMETER_PORTAL && state != AutomationState.ENTERING_REPAIR_PORTAL) {
			resetForWorldChange();
			return;
		}
		stopProcesses();
		bindBaritone();
		applyRepairRestrictions();
		restoreRepairNavigationSettings();
		repair.portalWaitTicks = 0;
		switch (repair.stage) {
			case OUTBOUND_PERIMETER_PORTAL -> {
				repair.stage = RepairFlow.Stage.OUTBOUND_REPAIR_PORTAL;
				beginClearRepairPortal(position(perimeterPortalNether), position(repairPortalNether), AutomationState.NAVIGATING_TO_REPAIR_PORTAL);
			}
			case OUTBOUND_REPAIR_PORTAL -> {
				repair.stage = RepairFlow.Stage.REPAIR_MACHINE;
				beginClearRepairPortal(position(repairPortalOverworld), null, AutomationState.NAVIGATING_TO_REPAIR_MACHINE);
			}
			case RETURN_REPAIR_PORTAL -> {
				repair.stage = RepairFlow.Stage.RETURN_PERIMETER_PORTAL;
				beginClearRepairPortal(position(repairPortalNether), position(perimeterPortalNether), AutomationState.NAVIGATING_TO_PERIMETER_PORTAL);
			}
			case RETURN_PERIMETER_PORTAL -> {
				repair.stage = RepairFlow.Stage.RETURN_TO_MINE;
				beginClearRepairPortal(position(perimeterPortalOverworld), null, null);
			}
			default -> transition(AutomationState.ERROR, Translations.DETAIL.message("unexpected_repair_portal"));
		}
	}

	private void beginClearRepairPortal(BlockPos portal, BlockPos nextTarget, AutomationState nextState) {
		repair.portalExitOrigin = currentClientPlayerFeet();
		repair.portalExitSearchScans = 0;
		repair.portalExitCandidates = findPortalExitCandidates(repair.portalExitOrigin);
		repair.postPortalNavigationTarget = nextTarget;
		repair.postPortalNavigationState = nextState;
		disablePlacementForNavigation();
		if (!repair.portalExitCandidates.isEmpty()) startPortalExitPath();
		transition(AutomationState.CLEARING_REPAIR_PORTAL, Translations.DETAIL.message(
				repair.portalExitCandidates.isEmpty() ? "waiting_portal_exits" : "walking_clear_portal", portal.toShortString()));
	}

	private void tickClearRepairPortal() {
		if (repair.portalExitCandidates.contains(baritone.getPlayerContext().playerFeet())) {
			navigation.stopWalking();
			restoreAllowPlace();
			repair.portalExitCandidates = List.of();
			if (repair.postPortalNavigationState == AutomationState.NAVIGATING_TO_REPAIR_MACHINE) {
				startRepairNavigation(closestFurnaceStand(repair.furnaces.get(repair.furnaceIndex)), repair.postPortalNavigationState);
			} else if (repair.postPortalNavigationTarget == null) beginReturnToMine();
			else startRepairNavigation(repair.postPortalNavigationTarget, repair.postPortalNavigationState);
			return;
		}
		if (navigation.isWalking()) return;
		if (++repair.portalExitSearchScans > monitorScans(advanced.portalExitTimeoutSeconds)) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, Translations.DETAIL.message("portal_exit_timeout", advanced.portalExitTimeoutSeconds));
			return;
		}
		repair.portalExitOrigin = currentClientPlayerFeet();
		repair.portalExitCandidates = findPortalExitCandidates(repair.portalExitOrigin);
		if (!repair.portalExitCandidates.isEmpty()) {
			startPortalExitPath();
			synchronize(AutomationState.CLEARING_REPAIR_PORTAL, Translations.DETAIL.message("walking_clear_portal_short"));
			return;
		}
		synchronize(AutomationState.CLEARING_REPAIR_PORTAL, Translations.DETAIL.message("waiting_portal_exits_short"));
	}

	private void startPortalExitPath() {
		navigation.walk(new GoalComposite(repair.portalExitCandidates.stream().map(GoalBlock::new).toArray(Goal[]::new)));
	}

	private List<BlockPos> findPortalExitCandidates(BlockPos origin) {
		List<BlockPos> candidates = new ArrayList<>();
		BlockPos player = currentClientPlayerFeet();
		for (int dx = -advanced.portalExitMaxRadius; dx <= advanced.portalExitMaxRadius; dx++) {
			for (int dz = -advanced.portalExitMaxRadius; dz <= advanced.portalExitMaxRadius; dz++) {
				int horizontalDistanceSquared = dx * dx + dz * dz;
				if (horizontalDistanceSquared < advanced.portalExitMinRadius * advanced.portalExitMinRadius
						|| horizontalDistanceSquared > advanced.portalExitMaxRadius * advanced.portalExitMaxRadius) continue;
				for (int dy = -advanced.portalExitVerticalRadius; dy <= advanced.portalExitVerticalRadius; dy++) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (isPortalExitPosition(candidate)) candidates.add(candidate);
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(player::distSqr));
		return candidates;
	}

	private boolean isPortalExitPosition(BlockPos position) {
		var world = Minecraft.getInstance().level;
		if (world == null) return false;
		BlockPos floor = position.below();
		if (!world.isLoaded(floor) || !world.isLoaded(position.above(3))) return false;
		var floorState = world.getBlockState(floor);
		if (!floorState.isFaceSturdy(world, floor, Direction.UP)
				|| !floorState.getFluidState().isEmpty()
				|| floorState.is(Blocks.MAGMA_BLOCK)
				|| floorState.is(Blocks.CAMPFIRE)
				|| floorState.is(Blocks.SOUL_CAMPFIRE)) return false;
		for (int offset = 0; offset <= 3; offset++) {
			BlockPos clearance = position.above(offset);
			if (!world.isLoaded(clearance)) return false;
			var state = world.getBlockState(clearance);
			if (state.is(Blocks.NETHER_PORTAL) || !state.getFluidState().isEmpty()
					|| !state.getCollisionShape(world, clearance).isEmpty()) return false;
		}
		return true;
	}

	private BlockPos currentClientPlayerFeet() {
		if (Minecraft.getInstance().player == null) throw new IllegalStateException("No client player is loaded.");
		return Minecraft.getInstance().player.blockPosition();
	}

	private static BlockPos position(PositionConfig position) {
		return new BlockPos(position.x, position.y, position.z);
	}

	private List<BlockPos> furnaceRow() {
		BlockPos start = position(furnaceRowStart);
		BlockPos end = position(furnaceRowEnd);
		int dx = Integer.compare(end.getX(), start.getX());
		int dy = Integer.compare(end.getY(), start.getY());
		int dz = Integer.compare(end.getZ(), start.getZ());
		int steps = Math.max(Math.abs(end.getX() - start.getX()), Math.max(Math.abs(end.getY() - start.getY()), Math.abs(end.getZ() - start.getZ())));
		List<BlockPos> result = new ArrayList<>();
		for (int index = 0; index <= steps; index++) result.add(start.offset(dx * index, dy * index, dz * index));
		return result;
	}

	private List<BlockPos> furnaceStandPositions(BlockPos furnace) {
		return interactionPositionFinder.find(Minecraft.getInstance().level, furnace);
	}

	private List<BlockPos> interactionStandPositions(BlockPos target) {
		return interactionPositionFinder.find(Minecraft.getInstance().level, target);
	}

	private List<BlockPos> bedStandPositions(BlockPos bed) {
		return bedInteractionPositionFinder.find(Minecraft.getInstance().level, bed);
	}

	private BlockPos closestFurnaceStand(BlockPos furnace) {
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		return furnaceStandPositions(furnace).stream().min(Comparator.comparingDouble(player::distSqr))
				.orElse(furnace.above());
	}

	private Optional<BlockPos> closestInteractionStand(BlockPos target) {
		return interactionPositionFinder.closest(Minecraft.getInstance().level, target, baritone.getPlayerContext().playerFeet());
	}

	private Optional<BlockPos> closestBedStand(BlockPos bed) {
		return bedInteractionPositionFinder.closest(Minecraft.getInstance().level, bed, baritone.getPlayerContext().playerFeet());
	}

	private boolean canReachFurnace(BlockPos furnace) {
		return canReachInteractionTarget(furnace);
	}

	private boolean canReachInteractionTarget(BlockPos target) {
		return interactionPositionFinder.canReach(baritone.getPlayerContext().player(), target);
	}

	private boolean isAtInteractionStand(BlockPos target) {
		return interactionPositionFinder.isAtValidPosition(
				Minecraft.getInstance().level, target, baritone.getPlayerContext().playerFeet());
	}

	private boolean isAtBedStand(BlockPos bed) {
		return bedInteractionPositionFinder.isAtValidPosition(
				Minecraft.getInstance().level, bed, baritone.getPlayerContext().playerFeet());
	}

	private void applyRepairRestrictions() {
		if (savedRepairAllowBreak == null) savedRepairAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
		if (savedRepairAllowPlace == null) savedRepairAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
		BaritoneAPI.getSettings().allowBreak.value = false;
		BaritoneAPI.getSettings().allowPlace.value = false;
	}

	private void restoreRepairRestrictions() {
		if (savedRepairAllowBreak != null) BaritoneAPI.getSettings().allowBreak.value = savedRepairAllowBreak;
		if (savedRepairAllowPlace != null) BaritoneAPI.getSettings().allowPlace.value = savedRepairAllowPlace;
		savedRepairAllowBreak = null;
		savedRepairAllowPlace = null;
	}

	private void beginSupply(SupplyFlow.Kind kind, boolean debug) {
		PositionConfig point = kind == SupplyFlow.Kind.CONSUMABLES ? consumableSupplyPoint : durabilitySupplyPoint;
		if (point == null) {
			stopProcesses();
			restoreAllowPlace();
			transition(AutomationState.ERROR, Translations.DETAIL.message("supply_point_missing", kind.displayName));
			return;
		}
		supply.previousState = state;
		supply.debugOnly = debug;
		miningReturnPoint = baritone.getPlayerContext().playerFeet();
		miningReturnDimension = baritone.getPlayerContext().world().dimension();
		returnAction = ReturnAction.AFTER_SUPPLY;
		supply.kind = kind;
		supply.point = point;
		BlockPos chest = position(point);
		supply.stand = closestInteractionStand(chest).orElse(chest.above());
		supply.flightAttempts = 0;
		supply.durabilityPlan = kind == SupplyFlow.Kind.DURABILITY ? captureDurabilitySupplyPlan() : null;
		if (kind == SupplyFlow.Kind.DURABILITY && supply.durabilityPlan.targetHealthyCounts().isEmpty()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, Translations.DETAIL.message("no_supply_replacement_target"));
			return;
		}
		stopProcesses();
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		if (player.distSqr(supply.stand) > navigationFlightDistanceSquared() && canFlyToSupply()) {
			startFlyingToSupply();
			return;
		}
		startWalkingToSupply();
	}

	private boolean canFlyToDestination() {
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		return elytraNavigationEnabled && navigation.isFlightLoaded() && chest.is(Items.ELYTRA)
				&& remainingDurability(chest) > advanced.elytraDurabilityThreshold
				&& fireworkCount() > BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value;
	}

	private boolean canFlyToSupply() {
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		return elytraNavigationEnabled && navigation.isFlightLoaded() && chest.is(Items.ELYTRA)
				&& remainingDurability(chest) > advanced.emergencyFlightDurabilityThreshold
				&& fireworkCount() > 0;
	}

	private AutomationState supplyNavigationState() {
		return supply.kind == SupplyFlow.Kind.CONSUMABLES
				? AutomationState.NAVIGATING_TO_RESUPPLY
				: AutomationState.NAVIGATING_TO_DURABILITY_SUPPLY;
	}

	private AutomationState supplyInteractionState() {
		return supply.kind == SupplyFlow.Kind.CONSUMABLES
				? AutomationState.RESUPPLYING
				: AutomationState.SWAPPING_DURABILITY_AT_SUPPLY;
	}

	private void startWalkingToSupply() {
		supply.flying = false;
		disablePlacementForNavigation();
		BlockPos chest = position(supply.point);
		if (isAtInteractionStand(chest)) {
			restoreAllowPlace();
			beginSupplyInteraction();
			return;
		}
		List<BlockPos> stands = interactionStandPositions(chest);
		if (!stands.isEmpty()) {
			supply.stand = stands.stream().min(Comparator.comparingDouble(baritone.getPlayerContext().playerFeet()::distSqr)).orElseThrow();
			navigation.walk(new GoalComposite(stands.stream().map(GoalBlock::new).toArray(Goal[]::new)));
		} else {
			supply.stand = chest.above();
			navigation.walk(new GoalBlock(supply.stand));
		}
		transition(supplyNavigationState(), Translations.DETAIL.message("walking_supply", supply.kind.displayName, supply.stand.toShortString()));
	}

	private void tickSupplyNavigation() {
		if (isAtInteractionStand(position(supply.point))) {
			navigation.stopFlying();
			navigation.stopWalking();
			restoreElytraMinimumDurability();
			restoreAllowPlace();
			beginSupplyInteraction();
			return;
		}
		if (supply.flying) {
			if (navigation.isFlying()) return;
			restoreElytraMinimumDurability();
			restoreElytraFireworkReserve();
			startWalkingToSupply();
			return;
		}
		if (navigation.isWalking()) return;
		restoreAllowPlace();
		if (supply.stand.getY() > baritone.getPlayerContext().playerFeet().y
				&& supply.flightAttempts < advanced.flightRetryCount && canFlyToSupply()) {
			startFlyingToSupply();
			return;
		}
		restoreElytraMinimumDurability();
		restoreElytraFireworkReserve();
		transition(AutomationState.ERROR, Translations.DETAIL.message("supply_unreachable", supply.kind.displayName, supply.stand.toShortString()));
	}

	private void startFlyingToSupply() {
		restoreAllowPlace();
		if (savedElytraMinFireworksBeforeLanding == null) {
			savedElytraMinFireworksBeforeLanding = BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value;
		}
		BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value = -1;
		if (savedElytraMinimumDurability == null) savedElytraMinimumDurability = BaritoneAPI.getSettings().elytraMinimumDurability.value;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.emergencyFlightDurabilityThreshold;
		supply.flying = true;
		supply.flightAttempts++;
		navigation.fly(supply.stand);
		transition(supplyNavigationState(), Translations.DETAIL.message("flying_supply", supply.kind.displayName, supply.stand.toShortString()));
	}

	private void disablePlacementForNavigation() {
		navigation.disablePlacement();
	}

	private void enableBreakingForNavigation() {
		navigation.enableBreakingWithoutPlacement();
	}

	private void restoreAllowPlace() {
		navigation.restoreDestinationSettings();
	}

	private void restoreElytraMinimumDurability() {
		if (savedElytraMinimumDurability == null) return;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = savedElytraMinimumDurability;
		savedElytraMinimumDurability = null;
	}

	private void restoreElytraFireworkReserve() {
		if (savedElytraMinFireworksBeforeLanding == null) return;
		BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value = savedElytraMinFireworksBeforeLanding;
		savedElytraMinFireworksBeforeLanding = null;
	}

	private void beginSupplyInteraction() {
		restoreElytraMinimumDurability();
		restoreElytraFireworkReserve();
		supply.phase = supply.kind == SupplyFlow.Kind.DURABILITY ? SupplyFlow.Phase.PREPARING : SupplyFlow.Phase.OPENING;
		supply.interactionTicks = 0;
		transition(supplyInteractionState(), Translations.DETAIL.message("preparing_supply", supply.kind.displayName));
	}

	private void tickSupplyInteraction() {
		if (!inventoryClicks.isEmpty()) return;
		switch (supply.phase) {
			case PREPARING -> prepareDurabilityEquipment();
			case OPENING -> openSupplyChest();
			case TRANSFERRING -> transferSupplyItems();
			case FINALIZING -> finalizeSupplyEquipment();
		}
	}

	private void prepareDurabilityEquipment() {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		if (isLowMonitoredItem(chest)) {
			int empty = firstEmptyInventorySlot();
			if (empty < 0) {
				transition(AutomationState.ERROR, Translations.DETAIL.message("no_slot_chest_item"));
				return;
			}
			queuePickupSwap(CHEST_MENU_SLOT, menuSlot(empty));
			return;
		}
		ItemStack offhand = inventory.getItem(OFFHAND_INVENTORY_SLOT);
		if (isLowMonitoredItem(offhand)) {
			int empty = firstEmptyInventorySlot();
			if (empty < 0) {
				transition(AutomationState.ERROR, Translations.DETAIL.message("no_slot_offhand_item"));
				return;
			}
			queuePickupSwap(OFFHAND_MENU_SLOT, menuSlot(empty));
			return;
		}
		supply.phase = SupplyFlow.Phase.OPENING;
		supply.interactionTicks = 0;
	}

	private void openSupplyChest() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			supply.phase = SupplyFlow.Phase.TRANSFERRING;
			return;
		}
		if (supply.interactionTicks++ > ticks(advanced.supplyInteractionTimeoutSeconds)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("supply_open_timeout", supply.kind.displayName));
			return;
		}
		BlockPos chest = new BlockPos(supply.point.x, supply.point.y, supply.point.z);
		Vec3 hitLocation = new Vec3(supply.point.x + 0.5, supply.point.y + 1.0, supply.point.z + 0.5);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), hitLocation, baritone.getPlayerContext().playerRotations()), true);
		if (supply.interactionTicks % 5 == 1) {
			baritone.getPlayerContext().playerController().processRightClickBlock(
					baritone.getPlayerContext().player(), baritone.getPlayerContext().world(), InteractionHand.MAIN_HAND,
					new BlockHitResult(hitLocation, Direction.UP, chest, false));
		}
	}

	private void transferSupplyItems() {
		if (baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("supply_closed"));
			return;
		}
		if (supply.kind == SupplyFlow.Kind.CONSUMABLES) transferConsumables();
		else transferDurabilityItems();
	}

	private void transferConsumables() {
		if (configuredFoodCount() < advanced.foodResupplyTarget) {
			if (!queueConsumableTransfer(allowedFoods, advanced.foodResupplyTarget - configuredFoodCount(), "configured food")) return;
		}
		if (fireworkCount() < advanced.fireworkResupplyTarget) {
			if (!queueConsumableTransfer(Set.of(Items.FIREWORK_ROCKET), advanced.fireworkResupplyTarget - fireworkCount(), "firework rockets")) return;
		}
		finishSupplyTransfer();
	}

	private boolean queueConsumableTransfer(Set<Item> accepted, int needed, String name) {
		int chestSlots = supplyChestSlots();
		for (int sourceSlot = 0; sourceSlot < chestSlots; sourceSlot++) {
			ItemStack source = baritone.getPlayerContext().player().containerMenu.getSlot(sourceSlot).getItem();
			if (source.isEmpty() || !accepted.contains(source.getItem())) continue;
			int targetInventorySlot = findInventoryTarget(source);
			if (targetInventorySlot < 0) {
				transition(AutomationState.ERROR, Translations.DETAIL.message("no_inventory_space", name));
				return false;
			}
			ItemStack target = baritone.getPlayerContext().player().getInventory().getItem(targetInventorySlot);
			int capacity = target.isEmpty() ? source.getMaxStackSize() : target.getMaxStackSize() - target.getCount();
			int transfer = Math.min(needed, Math.min(source.getCount(), capacity));
			boolean mergeWhole = transfer == source.getCount() || !target.isEmpty() && transfer == capacity;
			queueExactChestTransfer(sourceSlot, chestInventoryMenuSlot(targetInventorySlot, chestSlots), transfer, source.getCount(), mergeWhole);
			synchronize(supplyInteractionState(), Translations.DETAIL.message("supply_transferring", transfer, name));
			return false;
		}
		transition(AutomationState.ERROR, Translations.DETAIL.message("supply_insufficient", name));
		return false;
	}

	private void queueExactChestTransfer(int sourceSlot, int targetSlot, int count, int sourceCount, boolean mergeWhole) {
		inventoryClicks.add(new InventoryClick(sourceSlot, 0, ContainerInput.PICKUP));
		if (mergeWhole) inventoryClicks.add(new InventoryClick(targetSlot, 0, ContainerInput.PICKUP));
		else for (int index = 0; index < count; index++) inventoryClicks.add(new InventoryClick(targetSlot, 1, ContainerInput.PICKUP));
		if (count < sourceCount) inventoryClicks.add(new InventoryClick(sourceSlot, 0, ContainerInput.PICKUP));
	}

	private void transferDurabilityItems() {
		int chestSlots = supplyChestSlots();
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) {
			if (isLowMonitoredItem(inventory.getItem(slot))) {
				inventoryClicks.add(new InventoryClick(chestInventoryMenuSlot(slot, chestSlots), 0, ContainerInput.QUICK_MOVE));
				return;
			}
		}
		for (Map.Entry<Item, Integer> requirement : supply.durabilityPlan.targetHealthyCounts().entrySet()) {
			if (healthyOwnedCount(requirement.getKey()) >= requirement.getValue()) continue;
			for (int chestSlot = 0; chestSlot < chestSlots; chestSlot++) {
				ItemStack source = baritone.getPlayerContext().player().containerMenu.getSlot(chestSlot).getItem();
				if (source.is(requirement.getKey()) && remainingDurability(source) > durabilityThreshold(requirement.getKey())) {
					inventoryClicks.add(new InventoryClick(chestSlot, 0, ContainerInput.QUICK_MOVE));
					return;
				}
			}
			transition(AutomationState.ERROR, Translations.DETAIL.message("no_healthy_replacement", BuiltInRegistries.ITEM.getKey(requirement.getKey())));
			return;
		}
		baritone.getPlayerContext().player().closeContainer();
		supply.phase = SupplyFlow.Phase.FINALIZING;
	}

	private void finalizeSupplyEquipment() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) return;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		if (supply.durabilityPlan.chestItem() != null) {
			ItemStack equipped = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
			if (!equipped.is(supply.durabilityPlan.chestItem()) || remainingDurability(equipped) <= durabilityThreshold(equipped)) {
				int source = findHealthyInventoryItem(supply.durabilityPlan.chestItem());
				if (source < 0) {
					transition(AutomationState.ERROR, Translations.DETAIL.message("equip_elytra_failed"));
					return;
				}
				queuePickupSwap(menuSlot(source), CHEST_MENU_SLOT);
				return;
			}
		}
		if (supply.durabilityPlan.offhandItem() != null) {
			ItemStack offhand = inventory.getItem(OFFHAND_INVENTORY_SLOT);
			if (!offhand.is(supply.durabilityPlan.offhandItem()) || remainingDurability(offhand) <= durabilityThreshold(offhand)) {
				int source = findHealthyInventoryItem(supply.durabilityPlan.offhandItem());
				if (source < 0) {
					transition(AutomationState.ERROR, Translations.DETAIL.message("equip_offhand_failed"));
					return;
				}
				queuePickupSwap(menuSlot(source), OFFHAND_MENU_SLOT);
				return;
			}
		}
		if (supply.durabilityPlan.selectedItem() != null) {
			inventory.setSelectedSlot(supply.durabilityPlan.selectedSlot());
			ItemStack selected = inventory.getItem(supply.durabilityPlan.selectedSlot());
			if (!selected.is(supply.durabilityPlan.selectedItem()) || remainingDurability(selected) <= durabilityThreshold(selected)) {
				int source = findHealthyInventoryItem(supply.durabilityPlan.selectedItem());
				if (source < 0) {
					transition(AutomationState.ERROR, Translations.DETAIL.message("equip_tool_failed"));
					return;
				}
				baritone.getPlayerContext().playerController().windowClick(
						baritone.getPlayerContext().player().inventoryMenu.containerId,
						menuSlot(source), supply.durabilityPlan.selectedSlot(), ContainerInput.SWAP,
						baritone.getPlayerContext().player());
				return;
			}
		}
		finishSupplyTransfer();
	}

	private void finishSupplyTransfer() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		supply.point = null;
		supply.stand = null;
		supply.phase = null;
		beginReturnToMine();
	}

	private void beginSleepFlow(boolean debug) {
		if (bedPoint == null) return;
		sleep.previousState = state;
		sleep.debugOnly = debug;
		miningReturnPoint = baritone.getPlayerContext().playerFeet();
		miningReturnDimension = baritone.getPlayerContext().world().dimension();
		returnAction = ReturnAction.AFTER_SLEEP;
		sleep.bed = position(bedPoint);
		sleep.stand = closestBedStand(sleep.bed).orElse(sleep.bed.above());
		sleep.flying = false;
		sleep.flightAttempts = 0;
		sleep.interactionTicks = 0;
		sleep.entered = false;
		stopProcesses();
		if (baritone.getPlayerContext().playerFeet().distSqr(sleep.stand) > navigationFlightDistanceSquared() && canFlyToSupply()) {
			startFlyingToBed();
		} else {
			startWalkingToBed();
		}
	}

	private void startWalkingToBed() {
		sleep.flying = false;
		disablePlacementForNavigation();
		if (isAtBedStand(sleep.bed)) {
			restoreAllowPlace();
			beginSleeping();
			return;
		}
		List<BlockPos> stands = bedStandPositions(sleep.bed);
		if (!stands.isEmpty()) {
			sleep.stand = stands.stream().min(Comparator.comparingDouble(baritone.getPlayerContext().playerFeet()::distSqr)).orElseThrow();
			navigation.walk(new GoalComposite(stands.stream().map(GoalBlock::new).toArray(Goal[]::new)));
		} else {
			sleep.stand = sleep.bed.above();
			navigation.walk(new GoalBlock(sleep.stand));
		}
		transition(AutomationState.NAVIGATING_TO_BED, Translations.DETAIL.message("walking_bed", sleep.bed.toShortString()));
	}

	private void startFlyingToBed() {
		restoreAllowPlace();
		if (savedElytraMinFireworksBeforeLanding == null) {
			savedElytraMinFireworksBeforeLanding = BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value;
		}
		BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value = -1;
		if (savedElytraMinimumDurability == null) savedElytraMinimumDurability = BaritoneAPI.getSettings().elytraMinimumDurability.value;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = advanced.emergencyFlightDurabilityThreshold;
		sleep.flying = true;
		sleep.flightAttempts++;
		navigation.fly(sleep.stand);
		transition(AutomationState.NAVIGATING_TO_BED, Translations.DETAIL.message("flying_bed", sleep.bed.toShortString()));
	}

	private void tickBedNavigation() {
		if (!isBedTime()) {
			stopProcesses();
			restoreElytraMinimumDurability();
			restoreElytraFireworkReserve();
			restoreAllowPlace();
			beginReturnToMine();
			return;
		}
		if (isAtBedStand(sleep.bed)) {
			stopProcesses();
			restoreElytraMinimumDurability();
			restoreElytraFireworkReserve();
			restoreAllowPlace();
			beginSleeping();
			return;
		}
		if (sleep.flying) {
			if (navigation.isFlying()) return;
			restoreElytraMinimumDurability();
			restoreElytraFireworkReserve();
			startWalkingToBed();
			return;
		}
		if (navigation.isWalking()) return;
		if (sleep.stand.getY() > baritone.getPlayerContext().playerFeet().y
				&& sleep.flightAttempts < advanced.flightRetryCount && canFlyToSupply()) {
			startFlyingToBed();
			return;
		}
		restoreAllowPlace();
		transition(AutomationState.ERROR, Translations.DETAIL.message("bed_unreachable", sleep.bed.toShortString()));
	}

	private void beginSleeping() {
		var world = Minecraft.getInstance().level;
		if (world == null || !world.isLoaded(sleep.bed) || !(world.getBlockState(sleep.bed).getBlock() instanceof BedBlock)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("configured_bed_invalid", sleep.bed.toShortString()));
			return;
		}
		sleep.interactionTicks = 0;
		sleep.entered = false;
		transition(AutomationState.SLEEPING, Translations.DETAIL.message("entering_bed", sleep.bed.toShortString()));
	}

	private void tickSleeping() {
		if (baritone.getPlayerContext().player().isSleeping()) {
			sleep.entered = true;
			synchronize(AutomationState.SLEEPING, Translations.DETAIL.message("sleeping"));
			return;
		}
		if (sleep.entered || !isBedTime()) {
			beginReturnToMine();
			return;
		}
		if (sleep.interactionTicks++ > ticks(advanced.supplyInteractionTimeoutSeconds)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("bed_timeout", sleep.bed.toShortString()));
			return;
		}
		Vec3 hitLocation = Vec3.atCenterOf(sleep.bed);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), hitLocation, baritone.getPlayerContext().playerRotations()), true);
		if (sleep.interactionTicks % 5 == 1) {
			baritone.getPlayerContext().playerController().processRightClickBlock(
					baritone.getPlayerContext().player(), baritone.getPlayerContext().world(), InteractionHand.MAIN_HAND,
					new BlockHitResult(hitLocation, Direction.UP, sleep.bed, false));
		}
	}

	private boolean isBedTime() {
		var world = Minecraft.getInstance().level;
		if (world == null || !world.dimension().equals(Level.OVERWORLD)) return false;
		var clock = world.dimensionType().defaultClock();
		if (clock.isEmpty()) return false;
		long dayTime = Math.floorMod(world.clockManager().getTotalTicks(clock.get()), 24000L);
		return dayTime >= 12542L && dayTime < 23460L;
	}

	private void clearSleepContext() {
		restoreElytraMinimumDurability();
		restoreElytraFireworkReserve();
		restoreAllowPlace();
		sleep.reset();
	}

	private void beginFurnaceInteraction() {
		restoreRepairNavigationSettings();
		if (repair.machineTakeoffPoint == null) repair.machineTakeoffPoint = baritone.getPlayerContext().playerFeet();
		repair.furnacePhase = RepairFlow.FurnacePhase.PREPARING;
		repair.interactionTicks = 0;
		transition(AutomationState.REPAIRING, Translations.DETAIL.message("preparing_furnace", repair.furnaceIndex + 1, repair.furnaces.size()));
	}

	private void tickRepairing() {
		if (!inventoryClicks.isEmpty()) return;
		switch (repair.furnacePhase) {
			case PREPARING -> prepareRepairItem();
			case OPENING -> openRepairFurnace();
			case TAKING_OUTPUT -> takeRepairOutput();
			case WAITING_FOR_REPAIR -> finishRepairFurnace();
		}
	}

	private void prepareRepairItem() {
		selectNextRepairTool();
		repair.furnacePhase = RepairFlow.FurnacePhase.OPENING;
		repair.interactionTicks = 0;
	}

	private boolean selectNextRepairTool() {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		int selected = inventory.getSelectedSlot();
		ItemStack selectedStack = inventory.getItem(selected);
		if (!selectedStack.isEmpty() && selectedStack.getItem().components().has(DataComponents.TOOL)
				&& repairItemNeedsMoreFullStacks(selectedStack.getItem())
				&& remainingDurability(selectedStack) < selectedStack.getMaxDamage()) return false;
		int candidate = -1;
		int durability = Integer.MAX_VALUE;
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.getItem().components().has(DataComponents.TOOL)
					&& repairItemNeedsMoreFullStacks(stack.getItem()) && remainingDurability(stack) < stack.getMaxDamage()
					&& remainingDurability(stack) < durability) {
				candidate = slot;
				durability = remainingDurability(stack);
			}
		}
		boolean changed = candidate >= 0 && candidate != selected;
		if (changed) {
			if (candidate < 9) inventory.setSelectedSlot(candidate);
			else baritone.getPlayerContext().playerController().windowClick(
					baritone.getPlayerContext().player().inventoryMenu.containerId,
					menuSlot(candidate), selected, ContainerInput.SWAP, baritone.getPlayerContext().player());
		}
		baritone.getPlayerContext().playerController().syncHeldItem();
		return changed;
	}

	private void openRepairFurnace() {
		if (baritone.getPlayerContext().player().containerMenu instanceof AbstractFurnaceMenu) {
			repair.furnacePhase = RepairFlow.FurnacePhase.TAKING_OUTPUT;
			return;
		}
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
			return;
		}
		if (repair.interactionTicks++ > ticks(advanced.furnaceInteractionTimeoutSeconds)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("furnace_open_timeout", repair.furnaceIndex + 1));
			return;
		}
		BlockPos furnace = repair.furnaces.get(repair.furnaceIndex);
		Vec3 hitLocation = Vec3.atCenterOf(furnace);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), hitLocation, baritone.getPlayerContext().playerRotations()), true);
		if (repair.interactionTicks % 5 == 1) {
			baritone.getPlayerContext().playerController().processRightClickBlock(
					baritone.getPlayerContext().player(), baritone.getPlayerContext().world(), InteractionHand.MAIN_HAND,
					new BlockHitResult(hitLocation, Direction.UP, furnace, false));
		}
	}

	private void takeRepairOutput() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof AbstractFurnaceMenu menu)) {
			transition(AutomationState.ERROR, Translations.DETAIL.message("furnace_closed"));
			return;
		}
		ItemStack output = menu.getSlot(2).getItem();
		if (output.isEmpty()) {
			baritone.getPlayerContext().player().closeContainer();
			advanceRepairFurnace();
			return;
		}
		inventoryClicks.add(new InventoryClick(2, 1, ContainerInput.THROW));
		repair.furnacePhase = RepairFlow.FurnacePhase.WAITING_FOR_REPAIR;
		repair.durabilitySnapshot = repairDurabilityTotal();
		repair.stableTicks = 0;
			synchronize(AutomationState.REPAIRING, Translations.DETAIL.message("collecting_experience"));
	}

	private void finishRepairFurnace() {
		if (repairTargetsFull()) {
			if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
				baritone.getPlayerContext().player().closeContainer();
			}
			beginRepairReturn();
			return;
		}
		if (selectNextRepairTool()) {
			repair.stableTicks = 0;
			synchronize(AutomationState.REPAIRING, Translations.DETAIL.message("switched_repair_tool"));
			return;
		}
		int durability = repairDurabilityTotal();
		if (durability > repair.durabilitySnapshot) {
			repair.durabilitySnapshot = durability;
			repair.stableTicks = 0;
			return;
		}
		if (++repair.stableTicks < ticks(advanced.repairExperienceStableSeconds)) return;
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		advanceRepairFurnace();
	}

	private void advanceRepairFurnace() {
		if (++repair.furnaceIndex >= repair.furnaces.size()) {
			if (!repairTargetsFull()) transition(AutomationState.ERROR, Translations.DETAIL.message("furnaces_exhausted"));
			else beginRepairReturn();
			return;
		}
		repair.stage = RepairFlow.Stage.REPAIR_MACHINE;
		startRepairNavigation(closestFurnaceStand(repair.furnaces.get(repair.furnaceIndex)), AutomationState.NAVIGATING_TO_REPAIR_MACHINE);
	}

	private RepairFlow.Plan captureRepairPlan(boolean includeAllDamaged) {
		Map<Item, Integer> targets = new LinkedHashMap<>();
		Map<Item, Integer> baselineFull = new LinkedHashMap<>();
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) collectRepairPlanItem(inventory.getItem(slot), targets, baselineFull, includeAllDamaged);
		collectRepairPlanItem(inventory.getItem(OFFHAND_INVENTORY_SLOT), targets, baselineFull, includeAllDamaged);
		collectRepairPlanItem(baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST), targets, baselineFull, includeAllDamaged);
		return new RepairFlow.Plan(Map.copyOf(targets), Map.copyOf(baselineFull));
	}

	private void collectRepairPlanItem(ItemStack stack, Map<Item, Integer> targets, Map<Item, Integer> baselineFull, boolean includeAllDamaged) {
		if (!isMonitoredItem(stack)) return;
		if (remainingDurability(stack) <= durabilityThreshold(stack)
				|| includeAllDamaged && remainingDurability(stack) < stack.getMaxDamage()) targets.merge(stack.getItem(), 1, Integer::sum);
		else if (remainingDurability(stack) == stack.getMaxDamage()) baselineFull.merge(stack.getItem(), 1, Integer::sum);
	}

	private boolean repairTargetsFull() {
		for (Map.Entry<Item, Integer> target : repair.plan.targetCounts().entrySet()) {
			int required = repair.plan.baselineFullCounts().getOrDefault(target.getKey(), 0) + target.getValue();
			if (fullMonitoredItemCount(target.getKey()) < required) return false;
		}
		return true;
	}

	private boolean repairItemNeedsMoreFullStacks(Item item) {
		Integer targets = repair.plan.targetCounts().get(item);
		if (targets == null) return false;
		return fullMonitoredItemCount(item) < repair.plan.baselineFullCounts().getOrDefault(item, 0) + targets;
	}

	private int fullMonitoredItemCount(Item item) {
		int count = 0;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item) && remainingDurability(stack) == stack.getMaxDamage()) count++;
		}
		ItemStack offhand = inventory.getItem(OFFHAND_INVENTORY_SLOT);
		if (offhand.is(item) && remainingDurability(offhand) == offhand.getMaxDamage()) count++;
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		if (chest.is(item) && remainingDurability(chest) == chest.getMaxDamage()) count++;
		return count;
	}

	private int repairDurabilityTotal() {
		int total = 0;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) total += repairDurability(inventory.getItem(slot));
		total += repairDurability(inventory.getItem(OFFHAND_INVENTORY_SLOT));
		total += repairDurability(baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST));
		return total;
	}

	private int repairDurability(ItemStack stack) {
		return stack.isEmpty() || !repair.plan.targetCounts().containsKey(stack.getItem()) ? 0 : remainingDurability(stack);
	}

	private void beginRepairReturn() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		if (repair.machineTakeoffPoint != null && !baritone.getPlayerContext().playerFeet().equals(repair.machineTakeoffPoint)) {
			repair.stage = RepairFlow.Stage.RETURN_TO_MACHINE_TAKEOFF;
			repair.navigationTarget = repair.machineTakeoffPoint;
			repair.flying = false;
			repair.flightAttempts = advanced.flightRetryCount;
			startWalkingForRepair(AutomationState.NAVIGATING_TO_REPAIR_MACHINE);
			return;
		}
		startRepairPortalReturn();
	}

	private void startRepairPortalReturn() {
		repair.stage = RepairFlow.Stage.RETURN_TO_MINE;
		beginReturnToMine();
	}

	private SupplyFlow.DurabilityPlan captureDurabilitySupplyPlan() {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		Map<Item, Integer> lowCounts = new LinkedHashMap<>();
		Map<Item, Integer> healthyCounts = new LinkedHashMap<>();
		for (int slot = 0; slot < 36; slot++) collectDurabilityCount(inventory.getItem(slot), lowCounts, healthyCounts);
		collectDurabilityCount(inventory.getItem(OFFHAND_INVENTORY_SLOT), lowCounts, healthyCounts);
		collectDurabilityCount(baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST), lowCounts, healthyCounts);
		Map<Item, Integer> targets = new LinkedHashMap<>();
		lowCounts.forEach((item, count) -> targets.put(item, count + healthyCounts.getOrDefault(item, 0)));
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		ItemStack offhand = inventory.getItem(OFFHAND_INVENTORY_SLOT);
		int selectedSlot = inventory.getSelectedSlot();
		ItemStack selected = inventory.getItem(selectedSlot);
		return new SupplyFlow.DurabilityPlan(
				targets,
				isLowMonitoredItem(chest) ? chest.getItem() : null,
				isLowMonitoredItem(offhand) ? offhand.getItem() : null,
				isLowMonitoredItem(selected) ? selected.getItem() : null,
				selectedSlot
		);
	}

	private void collectDurabilityCount(ItemStack stack, Map<Item, Integer> lowCounts, Map<Item, Integer> healthyCounts) {
		if (!isMonitoredItem(stack)) return;
		Map<Item, Integer> target = remainingDurability(stack) <= durabilityThreshold(stack) ? lowCounts : healthyCounts;
		target.merge(stack.getItem(), 1, Integer::sum);
	}

	private boolean isMonitoredItem(ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem()
				&& (stack.getItem().components().has(DataComponents.TOOL)
				|| elytraNavigationEnabled && stack.is(Items.ELYTRA));
	}

	private boolean isLowMonitoredItem(ItemStack stack) {
		return isMonitoredItem(stack) && remainingDurability(stack) <= durabilityThreshold(stack);
	}

	private int configuredFoodCount() {
		return inventoryCount(allowedFoods);
	}

	private int fireworkCount() {
		return inventoryCount(Set.of(Items.FIREWORK_ROCKET));
	}

	private int inventoryCount(Set<Item> accepted) {
		int count = 0;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (accepted.contains(stack.getItem())) count += stack.getCount();
		}
		return count;
	}

	private int firstEmptyInventorySlot() {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).isEmpty()) return slot;
		return -1;
	}

	private int findInventoryTarget(ItemStack source) {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack target = inventory.getItem(slot);
			if (ItemStack.isSameItemSameComponents(source, target) && target.getCount() < target.getMaxStackSize()) return slot;
		}
		return firstEmptyInventorySlot();
	}

	private int healthyOwnedCount(Item item) {
		int count = healthyInventoryCount(item);
		ItemStack offhand = baritone.getPlayerContext().player().getInventory().getItem(OFFHAND_INVENTORY_SLOT);
		if (offhand.is(item) && remainingDurability(offhand) > durabilityThreshold(item)) count++;
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		if (chest.is(item) && remainingDurability(chest) > durabilityThreshold(item)) count++;
		return count;
	}

	private int healthyInventoryCount(Item item) {
		int count = 0;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item) && remainingDurability(stack) > durabilityThreshold(item)) count++;
		}
		return count;
	}

	private int findHealthyInventoryItem(Item item) {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item) && remainingDurability(stack) > durabilityThreshold(item)) return slot;
		}
		return -1;
	}

	private int supplyChestSlots() {
		int slots = baritone.getPlayerContext().player().containerMenu.slots.size() - 36;
		if (slots <= 0) throw new IllegalStateException("The opened container has no supply storage slots.");
		return slots;
	}

	private void clearSupplyContext() {
		restoreAllowPlace();
		restoreElytraMinimumDurability();
		restoreElytraFireworkReserve();
		supply.reset();
		returnAction = ReturnAction.NONE;
	}

	private void clearRepairContext() {
		restoreRepairNavigationSettings();
		restoreRepairRestrictions();
		repair.reset();
	}

	private static int chestInventoryMenuSlot(int inventorySlot, int chestSlots) {
		return chestSlots + (inventorySlot < 9 ? 27 + inventorySlot : inventorySlot - 9);
	}

	private void requestUnload() {
		if (!unload.debugOnly && !unloadEnabled) {
			navigation.stopWalking();
			restoreAllowPlace();
			if (miningCompletePending) transition(AutomationState.COMPLETE, Translations.DETAIL.message("mining_complete_unload_disabled"));
			else startMiningCycle();
			return;
		}
		if (!unload.debugOnly) {
			miningReturnPoint = baritone.getPlayerContext().playerFeet();
			miningReturnDimension = baritone.getPlayerContext().world().dimension();
			returnAction = ReturnAction.AFTER_UNLOAD;
		}
		stopProcesses();
		if (unloadingPoints.isEmpty()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, Translations.DETAIL.message("unload_point_missing"));
			return;
		}
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		unload.point = unloadingPoints.stream()
				.min(Comparator.comparingLong(point -> horizontalDistanceSquared(player, point.point())))
				.orElseThrow();
		UnloadFlow.Candidate preferred = findUnloadCandidates(unload.point.point(), player.y).stream().findFirst().orElse(null);
		BlockPos flightTarget = preferred == null
				? new BlockPos(unload.point.point().x, player.y, unload.point.point().z)
				: preferred.position();
		unload.flightAttempts = 0;
		if (player.distSqr(flightTarget) <= navigationFlightDistanceSquared() || !canFlyToDestination()) {
			beginUnloadApproach();
			return;
		}
		startFlyingToUnload(flightTarget);
	}

	private void tickNavigateToUnload() {
		if (navigation.isFlying()) {
			synchronize(AutomationState.NAVIGATING_TO_UNLOAD, Translations.DETAIL.message("flying_unload", unload.point.name()));
			return;
		}
		beginUnloadApproach();
	}

	private void beginUnloadApproach() {
		navigation.stopFlying();
		disablePlacementForNavigation();
		int currentY = baritone.getPlayerContext().playerFeet().y;
		unload.candidates = findUnloadCandidates(unload.point.point(), currentY);
		unload.candidateIndex = 0;
		if (unload.candidates.isEmpty()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, Translations.DETAIL.message("unload_no_loaded_stand", unload.point.name()));
			return;
		}
		pathToUnloadCandidate();
	}

	private void pathToUnloadCandidate() {
		UnloadFlow.Candidate candidate = unload.candidates.get(unload.candidateIndex);
		navigation.walk(new GoalBlock(candidate.position()));
		transition(AutomationState.APPROACHING_UNLOAD, Translations.DETAIL.message("walking_unload_edge", candidate.position().toShortString(), unload.point.name()));
	}

	private void tickApproachingUnload() {
		UnloadFlow.Candidate candidate = unload.candidates.get(unload.candidateIndex);
		if (candidate.position().equals(baritone.getPlayerContext().playerFeet())) {
			navigation.stopWalking();
			restoreAllowPlace();
			unload.edgePosition = unloadEdgePosition(candidate.position(), unload.point.point());
			transition(AutomationState.POSITIONING_FOR_UNLOAD, Translations.DETAIL.message("positioning_unload", unload.point.name()));
			return;
		}
		if (navigation.isWalking()) return;
		if (candidate.position().getY() > baritone.getPlayerContext().playerFeet().y
				&& unload.flightAttempts < advanced.flightRetryCount && canFlyToDestination()) {
			startFlyingToUnload(candidate.position());
			return;
		}
		if (++unload.candidateIndex >= unload.candidates.size()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, Translations.DETAIL.message("unload_no_reachable_stand", unload.point.name()));
			return;
		}
		pathToUnloadCandidate();
	}

	private void startFlyingToUnload(BlockPos target) {
		restoreAllowPlace();
		unload.flightAttempts++;
		navigation.fly(target);
		transition(AutomationState.NAVIGATING_TO_UNLOAD, Translations.DETAIL.message("flying_unload_xz", unload.point.name(), unload.point.point().x, unload.point.point().z));
	}

	private void tickPositioningForUnload() {
		Vec3 playerPosition = baritone.getPlayerContext().player().position();
		double dx = unload.edgePosition.x - playerPosition.x;
		double dz = unload.edgePosition.z - playerPosition.z;
		double distanceSquared = dx * dx + dz * dz;
		baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
		if (distanceSquared > 0.0016) {
			Vec3 lookTarget = new Vec3(unload.edgePosition.x, baritone.getPlayerContext().playerHead().y, unload.edgePosition.z);
			baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
					baritone.getPlayerContext().playerHead(), lookTarget, baritone.getPlayerContext().playerRotations()), false);
			baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
			synchronize(AutomationState.POSITIONING_FOR_UNLOAD, Translations.DETAIL.message("positioning_unload", unload.point.name()));
			return;
		}
		baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
		unload.settleTicks = 5;
		transition(AutomationState.UNLOADING, Translations.DETAIL.message("facing_unload", unload.point.name()));
	}

	private void tickUnloading() {
		UnloadingPointConfig point = unload.point.point();
		Vec3 target = new Vec3(point.x + 0.5, point.minY + 0.5, point.z + 0.5);
		baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
		baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), target, baritone.getPlayerContext().playerRotations()).withPitch(90.0F), false);
		if (!inventoryClicks.isEmpty()) return;
		if (unload.settleTicks > 0) {
			unload.settleTicks--;
			return;
		}
		int disposableSlot = firstDisposableSlot();
		if (disposableSlot >= 0) {
			inventoryClicks.add(new InventoryClick(menuSlot(disposableSlot), 1, ContainerInput.THROW));
			unload.settleTicks = 1;
			synchronize(AutomationState.UNLOADING, Translations.DETAIL.message("dropping_unload", unload.point.name()));
			return;
		}
		boolean debug = unload.debugOnly;
		unload.reset();
		baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
		if (debug) {
			transition(AutomationState.COMPLETE, Translations.DETAIL.message("unload_debug_complete"));
		} else if (miningCompletePending) transition(AutomationState.COMPLETE, Translations.DETAIL.message("mining_unload_complete"));
		else beginReturnToMine();
	}

	private void beginReturnToMine() {
		if (miningReturnPoint == null) {
			startMiningCycle();
			return;
		}
		activeReturnWaypoint = chooseReturnWaypoint();
		activeReturnTarget = positionForReturnWaypoint(activeReturnWaypoint);
		returnFlightAttempts = 0;
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		if (player.distSqr(activeReturnTarget) > navigationFlightDistanceSquared() && canFlyForReturn()) {
			startFlyingBackToMine();
			return;
		}
		startWalkingBackToMine();
	}

	private ReturnWaypoint chooseReturnWaypoint() {
		ResourceKey<Level> currentDimension = baritone.getPlayerContext().world().dimension();
		BlockPos current = baritone.getPlayerContext().playerFeet();
		if (repair.stage != null && !repair.crossDimension) {
			if (!currentDimension.equals(miningReturnDimension)) {
				throw new IllegalStateException("Local repair cannot return across dimensions.");
			}
			return ReturnWaypoint.MINE;
		}
		List<ReturnRouteCandidate> candidates = new ArrayList<>();
		if (currentDimension.equals(miningReturnDimension)) {
			candidates.add(new ReturnRouteCandidate(ReturnWaypoint.MINE, distance(current, miningReturnPoint)));
		}
		if (hasReturnPortalConfiguration()) {
			if (miningReturnDimension.equals(Level.OVERWORLD)) {
				if (currentDimension.equals(Level.OVERWORLD)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_OVERWORLD,
						distance(current, position(repairPortalOverworld)) + advanced.portalTransitionCost
								+ distance(position(repairPortalNether), position(perimeterPortalNether)) + advanced.portalTransitionCost
									+ distance(position(perimeterPortalOverworld), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_OVERWORLD,
						distance(current, position(perimeterPortalOverworld)) + advanced.portalTransitionCost
								+ distance(position(perimeterPortalNether), position(repairPortalNether)) + advanced.portalTransitionCost
									+ distance(position(repairPortalOverworld), miningReturnPoint)));
				} else if (currentDimension.equals(Level.NETHER)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_NETHER,
						distance(current, position(repairPortalNether)) + advanced.portalTransitionCost
									+ distance(position(repairPortalOverworld), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_NETHER,
						distance(current, position(perimeterPortalNether)) + advanced.portalTransitionCost
									+ distance(position(perimeterPortalOverworld), miningReturnPoint)));
				}
			} else if (miningReturnDimension.equals(Level.NETHER)) {
				if (currentDimension.equals(Level.NETHER)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_NETHER,
						distance(current, position(repairPortalNether)) + advanced.portalTransitionCost
								+ distance(position(repairPortalOverworld), position(perimeterPortalOverworld)) + advanced.portalTransitionCost
									+ distance(position(perimeterPortalNether), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_NETHER,
						distance(current, position(perimeterPortalNether)) + advanced.portalTransitionCost
								+ distance(position(perimeterPortalOverworld), position(repairPortalOverworld)) + advanced.portalTransitionCost
									+ distance(position(repairPortalNether), miningReturnPoint)));
				} else if (currentDimension.equals(Level.OVERWORLD)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_OVERWORLD,
						distance(current, position(repairPortalOverworld)) + advanced.portalTransitionCost
									+ distance(position(repairPortalNether), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_OVERWORLD,
						distance(current, position(perimeterPortalOverworld)) + advanced.portalTransitionCost
									+ distance(position(perimeterPortalNether), miningReturnPoint)));
				}
			}
		}
		return candidates.stream().min(Comparator.comparingDouble(ReturnRouteCandidate::cost))
				.map(ReturnRouteCandidate::waypoint)
				.orElseThrow(() -> new IllegalStateException("No return route exists from the current dimension to the mining departure point."));
	}

	private boolean hasReturnPortalConfiguration() {
		return perimeterPortalOverworld != null && perimeterPortalNether != null
				&& repairPortalOverworld != null && repairPortalNether != null;
	}

	private static double distance(BlockPos first, BlockPos second) {
		return Math.sqrt(first.distSqr(second));
	}

	private BlockPos positionForReturnWaypoint(ReturnWaypoint waypoint) {
		return switch (waypoint) {
			case MINE -> miningReturnPoint;
			case REPAIR_OVERWORLD -> position(repairPortalOverworld);
			case REPAIR_NETHER -> position(repairPortalNether);
			case PERIMETER_OVERWORLD -> position(perimeterPortalOverworld);
			case PERIMETER_NETHER -> position(perimeterPortalNether);
		};
	}

	private boolean canFlyForReturn() {
		return repair.stage != null ? canFlyForRepair() : canFlyToDestination();
	}

	private void startWalkingBackToMine() {
		returningByElytra = false;
		if (activeReturnWaypoint == ReturnWaypoint.PERIMETER_OVERWORLD || activeReturnWaypoint == ReturnWaypoint.PERIMETER_NETHER) {
			enableBreakingForNavigation();
		} else {
			disablePlacementForNavigation();
		}
		if (baritone.getPlayerContext().playerFeet().equals(activeReturnTarget)) {
			restoreAllowPlace();
			finishReturnWaypoint();
			return;
		}
		navigation.walk(new GoalBlock(activeReturnTarget));
		transition(AutomationState.RETURNING_TO_MINE, Translations.DETAIL.message("walking_return", activeReturnWaypoint.displayName, activeReturnTarget.toShortString()));
	}

	private void tickReturningToMine() {
		if (baritone.getPlayerContext().playerFeet().equals(activeReturnTarget)) {
			navigation.stopFlying();
			navigation.stopWalking();
			restoreRepairNavigationSettings();
			restoreAllowPlace();
			finishReturnWaypoint();
			return;
		}
		if (returningByElytra) {
			if (navigation.isFlying()) {
				synchronize(AutomationState.RETURNING_TO_MINE, Translations.DETAIL.message("flying_return", activeReturnWaypoint.displayName, activeReturnTarget.toShortString()));
				return;
			}
			restoreRepairNavigationSettings();
			startWalkingBackToMine();
			return;
		}
		if (navigation.isWalking()) return;
		if (returnFlightAttempts < advanced.flightRetryCount && canFlyForReturn()) {
			startFlyingBackToMine();
			return;
		}
		restoreAllowPlace();
		transition(AutomationState.ERROR, Translations.DETAIL.message("return_unreachable", activeReturnTarget.toShortString()));
	}

	private void startFlyingBackToMine() {
		restoreAllowPlace();
		if (activeReturnWaypoint == ReturnWaypoint.PERIMETER_OVERWORLD || activeReturnWaypoint == ReturnWaypoint.PERIMETER_NETHER) {
			enableBreakingForNavigation();
		}
		if (repair.stage != null) prepareRepairFlightSettings();
		returningByElytra = true;
		returnFlightAttempts++;
		navigation.fly(activeReturnTarget);
		transition(AutomationState.RETURNING_TO_MINE, Translations.DETAIL.message("flying_return", activeReturnWaypoint.displayName, activeReturnTarget.toShortString()));
	}

	private void finishReturnWaypoint() {
		if (activeReturnWaypoint == ReturnWaypoint.MINE) {
			finishReturnToMine();
			return;
		}
		returnPortalWaypoint = activeReturnWaypoint;
		repair.portalWaitTicks = 0;
		AutomationState portalState = activeReturnWaypoint == ReturnWaypoint.REPAIR_OVERWORLD || activeReturnWaypoint == ReturnWaypoint.REPAIR_NETHER
				? AutomationState.ENTERING_REPAIR_PORTAL : AutomationState.ENTERING_PERIMETER_PORTAL;
		transition(portalState, Translations.DETAIL.message("waiting_return_portal", activeReturnTarget.toShortString()));
	}

	private void finishReturnToMine() {
		restoreAllowPlace();
		miningReturnPoint = null;
		miningReturnDimension = null;
		activeReturnTarget = null;
		activeReturnWaypoint = null;
		returnPortalWaypoint = null;
		returningByElytra = false;
		ReturnAction completedAction = returnAction;
		boolean supplyDebug = supply.debugOnly;
		AutomationState supplyPrevious = supply.previousState;
		boolean repairDebug = repair.debugOnly;
		AutomationState repairPrevious = repair.previousState;
		boolean sleepDebug = sleep.debugOnly;
		AutomationState sleepPrevious = sleep.previousState;
		returnAction = ReturnAction.NONE;
		if (completedAction == ReturnAction.AFTER_SUPPLY) clearSupplyContext();
		if (completedAction == ReturnAction.AFTER_SUPPLY && supplyDebug) {
			transition(AutomationState.COMPLETE, Translations.DETAIL.message("supply_debug_complete"));
		} else if (completedAction == ReturnAction.AFTER_SUPPLY && supplyPrevious == AutomationState.COLLECTING_DROPS) {
			beginDropCollection(miningCompletePending);
		} else if (completedAction == ReturnAction.AFTER_REPAIR && repairDebug) {
			clearRepairContext();
			transition(AutomationState.COMPLETE, Translations.DETAIL.message("repair_debug_complete"));
		} else if (completedAction == ReturnAction.AFTER_REPAIR) {
			clearRepairContext();
			if (repairPrevious == AutomationState.COLLECTING_DROPS) beginDropCollection(miningCompletePending);
			else startMiningCycle();
		} else if (completedAction == ReturnAction.AFTER_SLEEP) {
			clearSleepContext();
			if (sleepDebug) transition(AutomationState.COMPLETE, Translations.DETAIL.message("sleep_debug_complete"));
			else if (sleepPrevious == AutomationState.COLLECTING_DROPS) beginDropCollection(miningCompletePending);
			else startMiningCycle();
		} else {
			startMiningCycle();
		}
	}

	private boolean tickNavigationWatchdog() {
		BlockPos target = watchdogNavigationTarget();
		if (target == null) {
			resetNavigationWatchdog();
			return false;
		}
		Vec3 position = baritone.getPlayerContext().player().position();
		if (state != watchdogState || !target.equals(watchdogTarget)) {
			watchdogState = state;
			watchdogTarget = target;
			watchdogPosition = position;
			watchdogStationaryScans = 0;
			watchdogRetries = 0;
			return false;
		}
		if (watchdogPosition == null || position.distanceToSqr(watchdogPosition) > 0.0625) {
			watchdogPosition = position;
			watchdogStationaryScans = 0;
			return false;
		}
		if (++watchdogStationaryScans < monitorScans(advanced.navigationStallTimeoutSeconds)) return false;
		watchdogStationaryScans = 0;
		if (++watchdogRetries > advanced.navigationRetryCount) {
			stopProcesses();
			transition(AutomationState.ERROR, Translations.DETAIL.message("navigation_stalled", advanced.navigationRetryCount, target.toShortString()));
			return true;
		}
		restartWatchedNavigation();
		return true;
	}

	private BlockPos watchdogNavigationTarget() {
		return switch (state) {
			case RETURNING_TO_MINE -> activeReturnTarget;
			case NAVIGATING_TO_RESUPPLY, NAVIGATING_TO_DURABILITY_SUPPLY -> supply.stand;
			case NAVIGATING_TO_BED -> sleep.stand;
			case NAVIGATING_TO_PERIMETER_PORTAL, NAVIGATING_TO_REPAIR_PORTAL, NAVIGATING_TO_REPAIR_MACHINE -> repair.navigationTarget;
			case NAVIGATING_TO_UNLOAD -> unload.point == null ? null
					: new BlockPos(unload.point.point().x, baritone.getPlayerContext().playerFeet().y, unload.point.point().z);
			case APPROACHING_UNLOAD -> unload.candidateIndex < unload.candidates.size() ? unload.candidates.get(unload.candidateIndex).position() : null;
			default -> null;
		};
	}

	private boolean isManagedNavigationState(AutomationState candidate) {
		return candidate == AutomationState.RETURNING_TO_MINE
				|| candidate == AutomationState.NAVIGATING_TO_RESUPPLY
				|| candidate == AutomationState.NAVIGATING_TO_DURABILITY_SUPPLY
				|| candidate == AutomationState.NAVIGATING_TO_BED
				|| candidate == AutomationState.NAVIGATING_TO_PERIMETER_PORTAL
				|| candidate == AutomationState.NAVIGATING_TO_REPAIR_PORTAL
				|| candidate == AutomationState.NAVIGATING_TO_REPAIR_MACHINE
				|| candidate == AutomationState.NAVIGATING_TO_UNLOAD
				|| candidate == AutomationState.APPROACHING_UNLOAD;
	}

	private boolean isInsideNetherPortal() {
		BlockPos feet = currentClientPlayerFeet();
		var world = Minecraft.getInstance().level;
		return world != null && (world.getBlockState(feet).is(Blocks.NETHER_PORTAL) || world.getBlockState(feet.above()).is(Blocks.NETHER_PORTAL));
	}

	private void restartWatchedNavigation() {
		stopProcesses();
		switch (state) {
			case RETURNING_TO_MINE -> beginReturnToMine();
			case NAVIGATING_TO_RESUPPLY, NAVIGATING_TO_DURABILITY_SUPPLY -> {
				BetterBlockPos player = baritone.getPlayerContext().playerFeet();
				if (player.distSqr(supply.stand) > navigationFlightDistanceSquared() && canFlyToSupply()) startFlyingToSupply();
				else startWalkingToSupply();
			}
			case NAVIGATING_TO_BED -> {
				BetterBlockPos player = baritone.getPlayerContext().playerFeet();
				if (player.distSqr(sleep.stand) > navigationFlightDistanceSquared() && canFlyToSupply()) startFlyingToBed();
				else startWalkingToBed();
			}
			case NAVIGATING_TO_PERIMETER_PORTAL, NAVIGATING_TO_REPAIR_PORTAL, NAVIGATING_TO_REPAIR_MACHINE -> {
				if (repair.stage == RepairFlow.Stage.RETURN_TO_MACHINE_TAKEOFF) {
					repair.flying = false;
					repair.flightAttempts = advanced.flightRetryCount;
					startWalkingForRepair(state);
				} else startRepairNavigation(repair.navigationTarget, state);
			}
			case NAVIGATING_TO_UNLOAD -> beginUnloadApproach();
			case APPROACHING_UNLOAD -> pathToUnloadCandidate();
			default -> {
			}
		}
	}

	private void resetNavigationWatchdog() {
		watchdogState = null;
		watchdogTarget = null;
		watchdogPosition = null;
		watchdogStationaryScans = 0;
		watchdogRetries = 0;
	}

	private int firstDisposableSlot() {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && !shouldKeepWhenUnloading(stack)) return slot;
		}
		return -1;
	}

	private boolean shouldKeepWhenUnloading(ItemStack stack) {
		return stack.isDamageableItem()
				|| stack.is(Items.ELYTRA)
				|| stack.is(Items.FIREWORK_ROCKET)
				|| stack.getItem().components().has(DataComponents.TOOL)
				|| stack.getItem().components().has(DataComponents.EQUIPPABLE)
				|| stack.getItem().components().has(DataComponents.FOOD)
				|| unloadingWhitelist.contains(stack.getItem());
	}

	private List<UnloadFlow.Candidate> findUnloadCandidates(UnloadingPointConfig point, int preferredY) {
		List<UnloadFlow.Candidate> candidates = new ArrayList<>();
		int minY = baritone.getPlayerContext().world().dimensionType().minY() + 1;
		int maxY = baritone.getPlayerContext().world().dimensionType().minY()
				+ baritone.getPlayerContext().world().dimensionType().height() - 2;
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		for (int dx = -advanced.unloadLandingSearchRadius; dx <= advanced.unloadLandingSearchRadius; dx++) {
			for (int dz = -advanced.unloadLandingSearchRadius; dz <= advanced.unloadLandingSearchRadius; dz++) {
				int horizontalDistanceSquared = dx * dx + dz * dz;
				if (horizontalDistanceSquared == 0 || horizontalDistanceSquared > advanced.unloadLandingSearchRadius * advanced.unloadLandingSearchRadius) continue;
				int x = point.x + dx;
				int z = point.z + dz;
				BlockPos position = closestSafeStandingPosition(x, z, preferredY, minY, maxY);
				if (position != null) {
					candidates.add(new UnloadFlow.Candidate(position, horizontalDistanceSquared, Math.abs(position.getY() - preferredY), player.distSqr(position)));
				}
			}
		}
		candidates.sort(Comparator.comparingInt(UnloadFlow.Candidate::horizontalDistanceSquared)
				.thenComparingInt(UnloadFlow.Candidate::yDifference)
				.thenComparing((first, second) -> Integer.compare(second.position().getY(), first.position().getY()))
				.thenComparingDouble(UnloadFlow.Candidate::playerDistanceSquared));
		return candidates;
	}

	private BlockPos closestSafeStandingPosition(int x, int z, int preferredY, int minY, int maxY) {
		int centerY = Math.max(minY, Math.min(maxY, preferredY));
		int maxDifference = Math.max(centerY - minY, maxY - centerY);
		for (int difference = 0; difference <= maxDifference; difference++) {
			BlockPos upper = new BlockPos(x, centerY + difference, z);
			if (upper.getY() <= maxY && isSafeStandingPosition(upper)) return upper;
			if (difference > 0) {
				BlockPos lower = new BlockPos(x, centerY - difference, z);
				if (lower.getY() >= minY && isSafeStandingPosition(lower)) return lower;
			}
		}
		return null;
	}

	private boolean isSafeStandingPosition(BlockPos position) {
		BlockPos floor = position.below();
		BlockPos head = position.above();
		if (!baritone.getPlayerContext().world().isLoaded(floor) || !baritone.getPlayerContext().world().isLoaded(head)) return false;
		var world = baritone.getPlayerContext().world();
		var floorState = world.getBlockState(floor);
		var feetState = world.getBlockState(position);
		var headState = world.getBlockState(head);
		return floorState.isFaceSturdy(world, floor, Direction.UP)
				&& !floorState.is(Blocks.MAGMA_BLOCK)
				&& !floorState.is(Blocks.CAMPFIRE)
				&& !floorState.is(Blocks.SOUL_CAMPFIRE)
				&& !feetState.is(Blocks.POWDER_SNOW)
				&& !(feetState.getBlock() instanceof BaseFireBlock)
				&& floorState.getFluidState().isEmpty()
				&& feetState.getFluidState().isEmpty()
				&& headState.getFluidState().isEmpty()
				&& feetState.getCollisionShape(world, position).isEmpty()
				&& headState.getCollisionShape(world, head).isEmpty();
	}

	private Vec3 unloadEdgePosition(BlockPos standingBlock, UnloadingPointConfig point) {
		double centerX = standingBlock.getX() + 0.5;
		double centerZ = standingBlock.getZ() + 0.5;
		double dx = point.x + 0.5 - centerX;
		double dz = point.z + 0.5 - centerZ;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance == 0.0) return new Vec3(centerX, standingBlock.getY(), centerZ);
		double unitX = dx / distance;
		double unitZ = dz / distance;
		double maxOffset = 0.5 + advanced.unloadEdgeInset;
		double xScale = Math.abs(unitX) < 1.0E-6 ? Double.POSITIVE_INFINITY : maxOffset / Math.abs(unitX);
		double zScale = Math.abs(unitZ) < 1.0E-6 ? Double.POSITIVE_INFINITY : maxOffset / Math.abs(unitZ);
		double scale = Math.min(xScale, zScale);
		return new Vec3(centerX + unitX * scale, standingBlock.getY(), centerZ + unitZ * scale);
	}

	private static long horizontalDistanceSquared(BetterBlockPos player, UnloadingPointConfig point) {
		long dx = (long) player.x - point.x;
		long dz = (long) player.z - point.z;
		return dx * dx + dz * dz;
	}

	private ResourceCheck inspectResources() {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		List<StackSlot> tools = new ArrayList<>();
		List<StackSlot> elytras = new ArrayList<>();
		for (int slot = 0; slot < 36; slot++) classify(new StackSlot(inventory.getItem(slot), slot, menuSlot(slot), false, false), tools, elytras);
		classify(new StackSlot(inventory.getItem(OFFHAND_INVENTORY_SLOT), OFFHAND_INVENTORY_SLOT, OFFHAND_MENU_SLOT, true, false), tools, elytras);
		classify(new StackSlot(baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST), -1, CHEST_MENU_SLOT, false, true), tools, elytras);
		ResourceCheck toolsResult = inspectGroup(tools);
		if (toolsResult.repairRequired() || toolsResult.replacement() != null) return toolsResult;
		return inspectGroup(elytras);
	}

	private void classify(StackSlot slot, List<StackSlot> tools, List<StackSlot> elytras) {
		if (slot.stack().isEmpty() || !slot.stack().isDamageableItem()) return;
		if (slot.stack().is(Items.ELYTRA)) {
			if (elytraNavigationEnabled) elytras.add(slot);
		}
		else if (slot.stack().getItem().components().has(DataComponents.TOOL)) tools.add(slot);
	}

	private ResourceCheck inspectGroup(List<StackSlot> group) {
		Replacement replacement = null;
		for (StackSlot low : group) {
			if (remainingDurability(low.stack()) > durabilityThreshold(low.stack())) continue;
			StackSlot healthy = group.stream()
					.filter(candidate -> candidate != low && candidate.stack().getItem() == low.stack().getItem())
					.filter(candidate -> remainingDurability(candidate.stack()) > durabilityThreshold(candidate.stack()))
					.max((first, second) -> Integer.compare(remainingDurability(first.stack()), remainingDurability(second.stack())))
					.orElse(null);
			if (healthy == null) return new ResourceCheck(true, BuiltInRegistries.ITEM.getKey(low.stack().getItem()).toString(), null);
			if (replacement == null && (low.chest() || low.offhand() || low.inventorySlot() == baritone.getPlayerContext().player().getInventory().getSelectedSlot())) {
				replacement = new Replacement(low, healthy);
			}
		}
		return new ResourceCheck(false, "", replacement);
	}

	private void performReplacement(Replacement replacement) {
		StackSlot low = replacement.low();
		StackSlot healthy = replacement.healthy();
		if (low.chest() || low.offhand()) {
			queuePickupSwap(healthy.menuSlot(), low.menuSlot());
			synchronize(state, Translations.DETAIL.message("replacing_item", BuiltInRegistries.ITEM.getKey(low.stack().getItem())));
			return;
		}
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		if (healthy.inventorySlot() < 9) inventory.setSelectedSlot(healthy.inventorySlot());
		else {
			baritone.getPlayerContext().playerController().windowClick(
					baritone.getPlayerContext().player().inventoryMenu.containerId,
					healthy.menuSlot(), inventory.getSelectedSlot(), ContainerInput.SWAP,
					baritone.getPlayerContext().player());
		}
		synchronize(state, Translations.DETAIL.message("replaced_item", BuiltInRegistries.ITEM.getKey(low.stack().getItem())));
	}

	private boolean beginEating() {
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		int foodSlot = -1;
		for (int slot = 0; slot < 36; slot++) {
			if (allowedFoods.contains(inventory.getItem(slot).getItem())) {
				foodSlot = slot;
				break;
			}
		}
		if (foodSlot < 0) return false;
		stateBeforeEating = state;
		if (state == AutomationState.MINING && miningProcess != null) miningProcess.pause();
		if (state == AutomationState.COLLECTING_DROPS) navigation.stopWalking();
		if (watchdogNavigationTarget() != null) stopProcesses();
		int hotbarSlot = foodSlot < 9 ? foodSlot : 7;
		if (foodSlot >= 9) {
			baritone.getPlayerContext().playerController().windowClick(
					baritone.getPlayerContext().player().inventoryMenu.containerId,
					menuSlot(foodSlot), hotbarSlot, ContainerInput.SWAP,
					baritone.getPlayerContext().player());
		}
		inventory.setSelectedSlot(hotbarSlot);
		baritone.getPlayerContext().playerController().syncHeldItem();
		Minecraft.getInstance().options.keyUse.setDown(true);
		baritone.getPlayerContext().playerController().processRightClick(
				baritone.getPlayerContext().player(), baritone.getPlayerContext().world(), InteractionHand.MAIN_HAND);
		transition(AutomationState.EATING, Translations.DETAIL.message("eating"));
		return true;
	}

	private boolean shouldEat() {
		int foodLevel = baritone.getPlayerContext().player().getFoodData().getFoodLevel();
		return foodLevel <= advanced.foodLevelThreshold
				|| foodLevel < 20 && baritone.getPlayerContext().player().getHealth() <= advanced.healthEatingThreshold;
	}

	private void tickEating() {
		if (!shouldEat()) {
			baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
			Minecraft.getInstance().options.keyUse.setDown(false);
			if (stateBeforeEating == AutomationState.MINING && miningProcess != null) {
				miningProcess.resume();
				transition(AutomationState.MINING, Translations.DETAIL.message("eating_complete_mining"));
			} else if (stateBeforeEating == AutomationState.COLLECTING_DROPS) {
				transition(AutomationState.COLLECTING_DROPS, Translations.DETAIL.message("eating_complete_collecting"));
			} else if (isManagedNavigationState(stateBeforeEating)) {
				transition(stateBeforeEating, Translations.DETAIL.message("eating_complete_navigation"));
				watchdogStationaryScans = 0;
				restartWatchedNavigation();
			} else transition(stateBeforeEating, Translations.DETAIL.message("eating_complete"));
			return;
		}
		Minecraft.getInstance().options.keyUse.setDown(true);
	}

	private void executeInventoryClick() {
		InventoryClick click = inventoryClicks.poll();
		if (click == null) return;
		baritone.getPlayerContext().playerController().windowClick(
				baritone.getPlayerContext().player().containerMenu.containerId,
				click.slot(), click.button(), click.type(), baritone.getPlayerContext().player());
	}

	private void queuePickupSwap(int sourceMenuSlot, int targetMenuSlot) {
		inventoryClicks.add(new InventoryClick(sourceMenuSlot, 0, ContainerInput.PICKUP));
		inventoryClicks.add(new InventoryClick(targetMenuSlot, 0, ContainerInput.PICKUP));
		inventoryClicks.add(new InventoryClick(sourceMenuSlot, 0, ContainerInput.PICKUP));
	}

	private int emptyInventorySlots() {
		int empty = 0;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).isEmpty()) empty++;
		return empty;
	}

	private int inventoryItemCount() {
		int count = 0;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) count += inventory.getItem(slot).getCount();
		return count;
	}

	private void stopProcesses() {
		Minecraft.getInstance().options.keyUse.setDown(false);
		if (baritone != null) {
			baritone.getInputOverrideHandler().clearAllKeys();
		}
		if (miningProcess != null) miningProcess.cancel();
		navigation.stop();
		if (baritone != null) {
			baritone.getInputOverrideHandler().clearAllKeys();
			if (Minecraft.getInstance().gameMode != null) {
				baritone.getPlayerContext().playerController().resetBlockRemoving();
			}
		}
	}

	private void emergencyCleanup() {
		safeCleanup("release use key", () -> Minecraft.getInstance().options.keyUse.setDown(false));
		safeCleanup("release attack key", () -> Minecraft.getInstance().options.keyAttack.setDown(false));
		safeCleanup("release sneak key", () -> Minecraft.getInstance().options.keyShift.setDown(false));
		safeCleanup("clear Baritone inputs", () -> {
			if (baritone != null) baritone.getInputOverrideHandler().clearAllKeys();
		});
		safeCleanup("cancel mining", () -> {
			if (miningProcess != null) miningProcess.cancel();
		});
		safeCleanup("cancel walking", () -> {
			navigation.stopWalking();
		});
		safeCleanup("cancel elytra flight", () -> {
			navigation.stopFlying();
		});
		safeCleanup("stop block breaking", () -> {
			if (baritone != null && Minecraft.getInstance().gameMode != null) {
				baritone.getPlayerContext().playerController().resetBlockRemoving();
			}
		});
		safeCleanup("close container", () -> {
			if (baritone != null && baritone.getPlayerContext().player() != null
					&& !(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
				baritone.getPlayerContext().player().closeContainer();
			}
		});
		safeCleanup("restore destination navigation settings", this::restoreAllowPlace);
		safeCleanup("restore elytra durability setting", this::restoreElytraMinimumDurability);
		safeCleanup("restore elytra firework setting", this::restoreElytraFireworkReserve);
		safeCleanup("restore repair restrictions", this::restoreRepairRestrictions);
		inventoryClicks.clear();
		resetNavigationWatchdog();
	}

	private static void safeCleanup(String action, Runnable cleanup) {
		try {
			cleanup.run();
		} catch (RuntimeException exception) {
			PerimeterDigger.LOGGER.error("Failed to {} after an automation error", action, exception);
		}
	}

	private void transition(AutomationState next, LocalizedMessage nextDetail) {
		if (next == AutomationState.ERROR) {
			restoreAllowPlace();
			if (repair.stage != null) {
				restoreRepairNavigationSettings();
				restoreRepairRestrictions();
			}
		}
		recordTransition(state, next, nextDetail);
		state = next;
		detail = nextDetail;
		changedAt = Instant.now();
	}

	private void synchronize(AutomationState next, LocalizedMessage nextDetail) {
		if (state != next) {
			recordTransition(state, next, nextDetail);
			changedAt = Instant.now();
		}
		state = next;
		detail = nextDetail;
	}

	private void recordTransition(AutomationState previous, AutomationState next, LocalizedMessage transitionDetail) {
		String dimension = "unavailable";
		String position = "unavailable";
		try {
			if (baritone != null && baritone.getPlayerContext().world() != null) {
				dimension = baritone.getPlayerContext().world().dimension().identifier().toString();
			}
			if (baritone != null && baritone.getPlayerContext().player() != null) {
				position = baritone.getPlayerContext().playerFeet().toShortString();
			}
		} catch (RuntimeException ignored) {
		}
		stateHistory.addLast(new StateTransition(Instant.now(), previous, next, transitionDetail, dimension, position));
		while (stateHistory.size() > STATE_HISTORY_LIMIT) stateHistory.removeFirst();
	}

	private List<String> validate(PerimeterConfig config) {
		List<String> invalid = new ArrayList<>();
		if (config.detectedArea == null || config.detectedArea.scanlines == null || config.detectedArea.scanlines.isEmpty()) invalid.add("detected_area");
		if (config.diggingMinY == null) invalid.add("digging_min_y");
		if (config.diggingMaxY == null) invalid.add("digging_max_y");
		if (config.diggingMinY != null && config.diggingMaxY != null && config.diggingMinY > config.diggingMaxY) invalid.add("valid_digging_y_range");
		if (!List.of("avoid", "replace", "seal_boundary").contains(config.liquidPolicy)) invalid.add("liquid_policy");
		if (!List.of("repair_portal", "supply_point").contains(config.durabilityRecoveryMode)) invalid.add("durability_recovery_mode");
		return invalid;
	}

	private List<String> validateRepairConfiguration(PerimeterConfig config) {
		List<String> invalid = new ArrayList<>();
		boolean crossDimension = config.functions == null || config.functions.crossDimensionRepair;
		if (crossDimension && config.perimeterPortalOverworld == null) invalid.add("perimeter_portal_overworld");
		if (crossDimension && config.perimeterPortalNether == null) invalid.add("perimeter_portal_nether");
		if (crossDimension && config.repairPortalOverworld == null) invalid.add("repair_portal_overworld");
		if (crossDimension && config.repairPortalNether == null) invalid.add("repair_portal_nether");
		if (config.furnaceRowStart == null) invalid.add("furnace_row_start");
		if (config.furnaceRowEnd == null) invalid.add("furnace_row_end");
		if (config.furnaceRowStart != null && config.furnaceRowEnd != null) {
			int changedAxes = (config.furnaceRowStart.x == config.furnaceRowEnd.x ? 0 : 1)
					+ (config.furnaceRowStart.y == config.furnaceRowEnd.y ? 0 : 1)
					+ (config.furnaceRowStart.z == config.furnaceRowEnd.z ? 0 : 1);
			if (changedAxes > 1) invalid.add("axis_aligned_furnace_row");
		}
		return invalid;
	}

	private static AreaMiningLiquidPolicy policy(String value) {
		return switch (value) {
			case "avoid" -> AreaMiningLiquidPolicy.AVOID;
			case "replace" -> AreaMiningLiquidPolicy.REPLACE;
			case "seal_boundary" -> AreaMiningLiquidPolicy.SEAL_BOUNDARY;
			default -> throw new IllegalArgumentException("Unknown liquid policy: " + value);
		};
	}

	private static List<Block> sealingBlocks(List<String> values) {
		List<Block> blocks = new ArrayList<>();
		for (String value : values) {
			Identifier id = Identifier.tryParse(value);
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null) throw new IllegalArgumentException("Unknown sealing block: " + value);
			blocks.add(block);
		}
		return List.copyOf(blocks);
	}

	private static Set<Item> foods(List<String> values) {
		return items(values);
	}

	private static Set<Item> items(List<String> values) {
		Set<Item> foods = new HashSet<>();
		for (String value : values) {
			Identifier id = Identifier.tryParse(value);
			Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null) throw new IllegalArgumentException("Unknown food item: " + value);
			foods.add(item);
		}
		return Set.copyOf(foods);
	}

	private static int remainingDurability(ItemStack stack) {
		return stack.getMaxDamage() - stack.getDamageValue();
	}

	private int durabilityThreshold(ItemStack stack) {
		return durabilityThreshold(stack.getItem());
	}

	private int durabilityThreshold(Item item) {
		return item == Items.ELYTRA ? advanced.elytraDurabilityThreshold : advanced.toolDurabilityThreshold;
	}

	private int dropStableScanLimit() {
		return monitorScans(advanced.dropCollectionStableSeconds);
	}

	private double navigationFlightDistanceSquared() {
		return (double) advanced.elytraNavigationMinDistance * advanced.elytraNavigationMinDistance;
	}

	private static int ticks(double seconds) {
		return Math.max(1, (int) Math.ceil(seconds * 20.0));
	}

	private static int monitorScans(double seconds) {
		return Math.max(1, (int) Math.ceil(seconds * 20.0 / MONITOR_INTERVAL_TICKS));
	}

	private static int menuSlot(int inventorySlot) {
		return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
	}

	private static LocalizedMessage progress(AreaMiningStatus status) {
		Object remaining = status.knownRemainingBlocks() < 0L ? Translations.VALUE.tr("scanning") : status.knownRemainingBlocks();
		return Translations.DETAIL.message("mining_progress", status.minedBlocks(), status.blockLimit(), remaining, status.estimatedTotalBlocks());
	}

	private record StackSlot(ItemStack stack, int inventorySlot, int menuSlot, boolean offhand, boolean chest) {
	}

	private record Replacement(StackSlot low, StackSlot healthy) {
	}

	private record ResourceCheck(boolean repairRequired, String repairItem, Replacement replacement) {
	}

	private record InventoryClick(int slot, int button, ContainerInput type) {
	}

	private record ReturnRouteCandidate(ReturnWaypoint waypoint, double cost) {
	}

	private enum ReturnAction {
		NONE,
		AFTER_UNLOAD,
		AFTER_SUPPLY,
		AFTER_REPAIR,
		AFTER_SLEEP
	}

	private enum ReturnWaypoint {
		MINE("mining departure point"),
		REPAIR_OVERWORLD("Overworld repair portal"),
		REPAIR_NETHER("Nether repair portal"),
		PERIMETER_OVERWORLD("Overworld perimeter portal"),
		PERIMETER_NETHER("Nether perimeter portal");

		private final String displayName;

		ReturnWaypoint(String displayName) {
			this.displayName = displayName;
		}

		private ReturnWaypoint counterpart() {
			return switch (this) {
				case REPAIR_OVERWORLD -> REPAIR_NETHER;
				case REPAIR_NETHER -> REPAIR_OVERWORLD;
				case PERIMETER_OVERWORLD -> PERIMETER_NETHER;
				case PERIMETER_NETHER -> PERIMETER_OVERWORLD;
				case MINE -> throw new IllegalStateException("The mining point has no portal counterpart.");
			};
		}
	}

}
