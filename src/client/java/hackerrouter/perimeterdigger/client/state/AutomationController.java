package hackerrouter.perimeterdigger.client.state;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.IAreaMineProcess;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.area.AreaMiningLiquidPolicy;
import baritone.api.process.area.AreaMiningOptions;
import baritone.api.process.area.AreaMiningStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import hackerrouter.perimeterdigger.client.config.PerimeterConfig;
import hackerrouter.perimeterdigger.client.config.PositionConfig;
import hackerrouter.perimeterdigger.client.config.UnloadingPointConfig;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
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
import java.util.Set;

public final class AutomationController {
	private static final int MONITOR_INTERVAL_TICKS = 10;
	private static final int TOOL_DURABILITY_THRESHOLD = 32;
	private static final int ELYTRA_DURABILITY_THRESHOLD = 32;
	private static final int DURABILITY_SUPPLY_FLIGHT_THRESHOLD = 5;
	private static final int DROP_COLLECTION_RADIUS = 8;
	private static final int FOOD_LEVEL_THRESHOLD = 14;
	private static final int FOOD_RESUPPLY_TRIGGER = 1;
	private static final int FOOD_RESUPPLY_TARGET = 64;
	private static final int FIREWORK_RESUPPLY_TRIGGER = 10;
	private static final int FIREWORK_RESUPPLY_TARGET = 128;
	private static final int DROP_STABLE_SCAN_LIMIT = 3;
	private static final int REPAIR_EXPERIENCE_STABLE_TICKS = 30;
	private static final double FURNACE_INTERACTION_DISTANCE_SQUARED = 20.25;
	private static final int UNLOAD_LANDING_SEARCH_RADIUS = 16;
	private static final int UNLOAD_WALK_DISTANCE = 32;
	private static final double PORTAL_TRANSITION_DISTANCE_COST = 16.0;
	private static final int OFFHAND_INVENTORY_SLOT = 40;
	private static final int OFFHAND_MENU_SLOT = 45;
	private static final int CHEST_MENU_SLOT = 6;
	private AutomationState state = AutomationState.IDLE;
	private AutomationState stateBeforeEating = AutomationState.IDLE;
	private String detail = "Stopped";
	private Instant changedAt = Instant.now();
	private IBaritone baritone;
	private IAreaMineProcess miningProcess;
	private ICustomGoalProcess customGoalProcess;
	private IElytraProcess elytraProcess;
	private ConfiguredColumnarArea area;
	private AreaMiningLiquidPolicy liquidPolicy;
	private String durabilityRecoveryMode = "repair_portal";
	private List<Block> sealingBlocks = List.of();
	private Set<Item> allowedFoods = Set.of();
	private Set<Item> unloadingWhitelist = Set.of();
	private PositionConfig consumableSupplyPoint;
	private PositionConfig durabilitySupplyPoint;
	private PositionConfig perimeterPortalOverworld;
	private PositionConfig perimeterPortalNether;
	private PositionConfig repairPortalOverworld;
	private PositionConfig repairPortalNether;
	private PositionConfig furnaceRowStart;
	private PositionConfig furnaceRowEnd;
	private List<NamedUnloadingPoint> unloadingPoints = List.of();
	private NamedUnloadingPoint unloadingPoint;
	private BlockPos miningReturnPoint;
	private ResourceKey<Level> miningReturnDimension;
	private boolean returningByElytra;
	private int returnFlightAttempts;
	private BlockPos activeReturnTarget;
	private ReturnWaypoint activeReturnWaypoint;
	private ReturnWaypoint returnPortalWaypoint;
	private ReturnAction returnAction = ReturnAction.NONE;
	private AutomationState stateBeforeSupply = AutomationState.IDLE;
	private SupplyKind supplyKind;
	private SupplyPhase supplyPhase;
	private PositionConfig activeSupplyPoint;
	private BlockPos activeSupplyStand;
	private boolean supplyFlying;
	private int supplyFlightAttempts;
	private Integer savedElytraMinimumDurability;
	private Integer savedElytraMinFireworksBeforeLanding;
	private Boolean savedAllowBreak;
	private Boolean savedAllowPlace;
	private int supplyInteractionTicks;
	private boolean debugStage6Only;
	private DurabilitySupplyPlan durabilitySupplyPlan;
	private RepairStage repairStage;
	private BlockPos repairNavigationTarget;
	private boolean repairFlying;
	private int repairFlightAttempts;
	private int portalWaitTicks;
	private List<BlockPos> portalExitCandidates = List.of();
	private BlockPos portalExitOrigin;
	private int portalExitSearchScans;
	private BlockPos postPortalNavigationTarget;
	private AutomationState postPortalNavigationState;
	private List<BlockPos> repairFurnaces = List.of();
	private int repairFurnaceIndex;
	private RepairFurnacePhase repairFurnacePhase;
	private int repairInteractionTicks;
	private BlockPos repairMachineTakeoffPoint;
	private int repairDurabilitySnapshot;
	private int repairStableTicks;
	private RepairPlan repairPlan;
	private Boolean savedRepairAllowBreak;
	private Boolean savedRepairAllowPlace;
	private boolean debugStage7Only;
	private List<UnloadCandidate> unloadCandidates = List.of();
	private Vec3 unloadEdgePosition;
	private int unloadCandidateIndex;
	private int unloadFlightAttempts;
	private int unloadSettleTicks;
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
	private boolean debugUnloadOnly;

	public List<String> start(PerimeterConfig config) {
		transition(AutomationState.VALIDATING, "Validating mining configuration");
		List<String> missing = validate(config);
		if (!missing.isEmpty()) {
			transition(AutomationState.ERROR, "Missing or invalid configuration: " + String.join(", ", missing));
			return missing;
		}
		this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		this.miningProcess = baritone.getAreaMineProcess();
		this.customGoalProcess = baritone.getCustomGoalProcess();
		this.elytraProcess = baritone.getElytraProcess();
		this.area = new ConfiguredColumnarArea(config.detectedArea, config.diggingMinY, config.diggingMaxY);
		this.liquidPolicy = policy(config.liquidPolicy);
		this.durabilityRecoveryMode = config.durabilityRecoveryMode;
		this.sealingBlocks = sealingBlocks(config.sealingBlocks);
		this.allowedFoods = foods(config.foods);
		this.unloadingWhitelist = items(config.unloadingWhitelist);
		this.consumableSupplyPoint = config.consumableSupplyPoint;
		this.durabilitySupplyPoint = config.durabilitySupplyPoint;
		loadRepairConfiguration(config);
		this.unloadingPoints = config.unloadingPoints.entrySet().stream()
				.map(entry -> new NamedUnloadingPoint(entry.getKey(), entry.getValue()))
				.toList();
		this.miningCompletePending = false;
		this.debugUnloadOnly = false;
		this.debugStage6Only = false;
		this.debugStage7Only = false;
		this.stableInventoryScans = 0;
		this.inventoryClicks.clear();
		BaritoneAPI.getSettings().itemSaver.value = true;
		BaritoneAPI.getSettings().itemSaverThreshold.value = TOOL_DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = ELYTRA_DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		startMiningCycle();
		return List.of();
	}

	public List<String> debugStage5(PerimeterConfig config) {
		transition(AutomationState.VALIDATING, "Validating stage 5 debug configuration");
		if (config.unloadingPoints.isEmpty()) {
			transition(AutomationState.ERROR, "Missing or invalid configuration: unloading_points");
			return List.of("unloading_points");
		}
		stopProcesses();
		this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		this.miningProcess = baritone.getAreaMineProcess();
		this.customGoalProcess = baritone.getCustomGoalProcess();
		this.elytraProcess = baritone.getElytraProcess();
		this.unloadingWhitelist = items(config.unloadingWhitelist);
		this.unloadingPoints = config.unloadingPoints.entrySet().stream()
				.map(entry -> new NamedUnloadingPoint(entry.getKey(), entry.getValue()))
				.toList();
		this.inventoryClicks.clear();
		this.miningCompletePending = false;
		this.debugUnloadOnly = true;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = ELYTRA_DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		requestUnload();
		return state == AutomationState.ERROR ? List.of("stage5_start") : List.of();
	}

	public List<String> debugStage6(PerimeterConfig config, boolean durability) {
		SupplyKind kind = durability ? SupplyKind.DURABILITY : SupplyKind.CONSUMABLES;
		PositionConfig point = durability ? config.durabilitySupplyPoint : config.consumableSupplyPoint;
		transition(AutomationState.VALIDATING, "Validating stage 6 debug configuration");
		if (point == null) {
			transition(AutomationState.ERROR, "Missing stage 6 supply point.");
			return List.of(durability ? "durability_supply_point" : "consumable_supply_point");
		}
		stopProcesses();
		this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		this.miningProcess = baritone.getAreaMineProcess();
		this.customGoalProcess = baritone.getCustomGoalProcess();
		this.elytraProcess = baritone.getElytraProcess();
		this.allowedFoods = foods(config.foods);
		this.consumableSupplyPoint = config.consumableSupplyPoint;
		this.durabilitySupplyPoint = config.durabilitySupplyPoint;
		this.inventoryClicks.clear();
		this.debugStage6Only = true;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = ELYTRA_DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		if (kind == SupplyKind.DURABILITY && captureDurabilitySupplyPlan().targetHealthyCounts().isEmpty()) {
			transition(AutomationState.ERROR, "No low-durability tool or elytra was found for stage 6 debug.");
			return List.of("low_durability_item");
		}
		beginSupply(kind, true);
		return state == AutomationState.ERROR ? List.of("stage6_start") : List.of();
	}

	public List<String> debugStage7(PerimeterConfig config) {
		transition(AutomationState.VALIDATING, "Validating stage 7 debug configuration");
		List<String> missing = validateRepairConfiguration(config);
		if (!missing.isEmpty()) {
			transition(AutomationState.ERROR, "Missing or invalid stage 7 configuration: " + String.join(", ", missing) + ".");
			return missing;
		}
		stopProcesses();
		bindBaritone();
		loadRepairConfiguration(config);
		this.allowedFoods = foods(config.foods);
		this.debugStage7Only = true;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = ELYTRA_DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		BaritoneAPI.getSettings().allowSprint.value = true;
		BaritoneAPI.getSettings().allowParkour.value = true;
		beginRepairFlow(true);
		if (state == AutomationState.ERROR) throw new IllegalStateException(detail);
		return List.of();
	}

	public void tick() {
		if (baritone == null || baritone.getPlayerContext().player() == null) return;
		executeInventoryClick();
		if (state == AutomationState.EATING) {
			tickEating();
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
				&& baritone.getPlayerContext().player().getFoodData().getFoodLevel() <= FOOD_LEVEL_THRESHOLD
				&& beginEating()) return;
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
		if (baritone.getPlayerContext().player().getFoodData().getFoodLevel() <= FOOD_LEVEL_THRESHOLD && beginEating()) return;
		if (resources.replacement() != null) {
			performReplacement(resources.replacement());
			return;
		}
		if ((state == AutomationState.MINING || state == AutomationState.COLLECTING_DROPS) && emptyInventorySlots() == 0) {
			requestUnload();
			return;
		}
		if (consumableSupplyPoint != null
				&& (configuredFoodCount() <= FOOD_RESUPPLY_TRIGGER || fireworkCount() <= FIREWORK_RESUPPLY_TRIGGER)) {
			beginSupply(SupplyKind.CONSUMABLES, false);
			return;
		}
		if (resources.repairRequired()) {
			if (durabilityRecoveryMode.equals("supply_point") && durabilitySupplyPoint != null) {
				beginSupply(SupplyKind.DURABILITY, false);
				return;
			} else if (durabilityRecoveryMode.equals("repair_portal") && hasUsableRepairConfiguration()) {
				beginRepairFlow(false);
				return;
			}
		}
		if (consumableSupplyPoint != null
				&& baritone.getPlayerContext().player().getFoodData().getFoodLevel() <= FOOD_LEVEL_THRESHOLD) {
			beginSupply(SupplyKind.CONSUMABLES, false);
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
		customGoalProcess = null;
		elytraProcess = null;
		area = null;
		miningReturnPoint = null;
		miningReturnDimension = null;
		activeReturnTarget = null;
		activeReturnWaypoint = null;
		returnPortalWaypoint = null;
		inventoryClicks.clear();
		resetNavigationWatchdog();
		debugUnloadOnly = false;
		debugStage6Only = false;
		clearSupplyContext();
		clearRepairContext();
		transition(AutomationState.IDLE, "Stopped");
	}

	public void clearCachedState() {
		stop();
		stateBeforeEating = AutomationState.IDLE;
		stateBeforeSupply = AutomationState.IDLE;
		unloadingPoint = null;
		unloadCandidates = List.of();
		unloadEdgePosition = null;
		unloadCandidateIndex = 0;
		unloadFlightAttempts = 0;
		unloadSettleTicks = 0;
		stableInventoryScans = 0;
		lastInventoryItemCount = 0;
		miningCompletePending = false;
		tickCounter = 0;
		transition(AutomationState.IDLE, "Cached automation state cleared");
	}

	public void pause() {
		if (miningProcess == null || state != AutomationState.MINING) throw new IllegalStateException("No running mining task can be paused.");
		miningProcess.pause();
		transition(AutomationState.PAUSED, "Mining paused manually");
	}

	public void resume() {
		if (miningProcess == null || state != AutomationState.PAUSED) throw new IllegalStateException("No paused mining task can be resumed.");
		AreaMiningStatus status = miningProcess.getAreaMiningStatus();
		if (status.pauseReason() == AreaMiningStatus.PauseReason.BLOCK_LIMIT_REACHED) beginDropCollection(false);
		else {
			miningProcess.resume();
			transition(AutomationState.MINING, "Mining resumed");
		}
	}

	public AutomationState state() {
		return state;
	}

	public String detail() {
		BlockPos target = baritone == null ? null : watchdogNavigationTarget();
		return target == null ? detail : detail + " Target=" + target.toShortString() + ", retries=" + watchdogRetries + ".";
	}

	public Instant changedAt() {
		return changedAt;
	}

	public void resetForWorldChange() {
		if (baritone != null && baritone.getPlayerContext().player() != null
				&& !(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		stopProcesses();
		baritone = null;
		miningProcess = null;
		customGoalProcess = null;
		elytraProcess = null;
		area = null;
		miningReturnPoint = null;
		miningReturnDimension = null;
		activeReturnTarget = null;
		activeReturnWaypoint = null;
		returnPortalWaypoint = null;
		inventoryClicks.clear();
		resetNavigationWatchdog();
		debugUnloadOnly = false;
		debugStage6Only = false;
		clearSupplyContext();
		clearRepairContext();
		transition(AutomationState.IDLE, "World changed");
	}

	private void synchronizeMining() {
		AreaMiningStatus status = miningProcess.getAreaMiningStatus();
		switch (status.state()) {
			case RUNNING -> synchronize(AutomationState.MINING, progress(status));
			case PAUSED -> {
				if (status.pauseReason() == AreaMiningStatus.PauseReason.BLOCK_LIMIT_REACHED) beginDropCollection(false);
				else synchronize(AutomationState.PAUSED, "Mining paused: " + status.pauseReason().name().toLowerCase(Locale.ROOT) + ". " + progress(status));
			}
			case COMPLETE -> beginDropCollection(true);
			case CANCELLED -> transition(AutomationState.ERROR, "The Baritone mining task was cancelled unexpectedly.");
			case IDLE -> transition(AutomationState.ERROR, "The Baritone mining task stopped unexpectedly.");
		}
	}

	private void startMiningCycle() {
		int emptySlots = emptyInventorySlots();
		if (emptySlots == 0) {
			requestUnload();
			return;
		}
		long blockLimit = emptySlots == 1 ? 1L : (long) (emptySlots - 1) * 64L;
		customGoalProcess.onLostControl();
		miningProcess.mineArea(area, new AreaMiningOptions(liquidPolicy, sealingBlocks, blockLimit));
		transition(AutomationState.MINING, "Mining cycle started. Block limit: " + blockLimit + ". Empty slots: " + emptySlots + ".");
	}

	private void beginDropCollection(boolean miningComplete) {
		miningCompletePending = miningComplete;
		miningProcess.cancel();
		disablePlacementForNavigation();
		stableInventoryScans = 0;
		lastInventoryItemCount = inventoryItemCount();
		transition(AutomationState.COLLECTING_DROPS, "Collecting drops within an 8-block horizontal radius.");
		tickDropCollection();
	}

	private void tickDropCollection() {
		if (emptyInventorySlots() == 0) {
			requestUnload();
			return;
		}
		int itemCount = inventoryItemCount();
		if (itemCount > lastInventoryItemCount) stableInventoryScans = 0;
		else stableInventoryScans++;
		lastInventoryItemCount = itemCount;
		List<Goal> goals = nearbyDropGoals();
		if (stableInventoryScans >= DROP_STABLE_SCAN_LIMIT) {
			customGoalProcess.onLostControl();
			requestUnload();
			return;
		}
		if (goals.isEmpty()) {
			customGoalProcess.onLostControl();
			synchronize(AutomationState.COLLECTING_DROPS, "Waiting for inventory growth. Items: " + itemCount + ". Stable scans: " + stableInventoryScans + "/" + DROP_STABLE_SCAN_LIMIT + ".");
			return;
		}
		customGoalProcess.setGoalAndPath(new GoalComposite(goals.toArray(new Goal[0])));
		synchronize(AutomationState.COLLECTING_DROPS, "Collecting " + goals.size() + " nearby drop targets. Items: " + itemCount + ". Stable scans: " + stableInventoryScans + "/" + DROP_STABLE_SCAN_LIMIT + ".");
	}

	private List<Goal> nearbyDropGoals() {
		List<Goal> goals = new ArrayList<>();
		double radiusSquared = (double) DROP_COLLECTION_RADIUS * DROP_COLLECTION_RADIUS;
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
		this.customGoalProcess = baritone.getCustomGoalProcess();
		this.elytraProcess = baritone.getElytraProcess();
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
		if (!baritone.getPlayerContext().world().dimension().equals(Level.OVERWORLD)) {
			transition(AutomationState.ERROR, "Stage 7 must start in the Overworld.");
			return;
		}
		if (!validateRepairConfigurationValues()) return;
		stateBeforeSupply = state;
		debugStage7Only = debug;
		miningReturnPoint = baritone.getPlayerContext().playerFeet();
		miningReturnDimension = baritone.getPlayerContext().world().dimension();
		returnAction = ReturnAction.AFTER_REPAIR;
		repairPlan = captureRepairPlan(debug);
		if (repairPlan.targetCounts().isEmpty()) {
			transition(AutomationState.ERROR, debug
					? "No damaged tool or elytra requires stage 7 debug repair."
					: "No low-durability tool or elytra requires stage 7 repair.");
			return;
		}
		repairFurnaces = furnaceRow();
		repairFurnaceIndex = 0;
		stopProcesses();
		applyRepairRestrictions();
		repairStage = RepairStage.OUTBOUND_PERIMETER_PORTAL;
		startRepairNavigation(position(perimeterPortalOverworld), AutomationState.NAVIGATING_TO_PERIMETER_PORTAL);
	}

	private boolean validateRepairConfigurationValues() {
		if (perimeterPortalOverworld == null || perimeterPortalNether == null || repairPortalOverworld == null
				|| repairPortalNether == null || furnaceRowStart == null || furnaceRowEnd == null) {
			transition(AutomationState.ERROR, "Stage 7 portal or furnace configuration is incomplete.");
			return false;
		}
		return true;
	}

	private boolean hasUsableRepairConfiguration() {
		if (perimeterPortalOverworld == null || perimeterPortalNether == null || repairPortalOverworld == null
				|| repairPortalNether == null || furnaceRowStart == null || furnaceRowEnd == null) return false;
		int changedAxes = (furnaceRowStart.x == furnaceRowEnd.x ? 0 : 1)
				+ (furnaceRowStart.y == furnaceRowEnd.y ? 0 : 1)
				+ (furnaceRowStart.z == furnaceRowEnd.z ? 0 : 1);
		return changedAxes <= 1;
	}

	private void startRepairNavigation(BlockPos target, AutomationState navigationState) {
		repairNavigationTarget = target;
		repairFlying = false;
		repairFlightAttempts = 0;
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		if (player.distSqr(target) > (double) UNLOAD_WALK_DISTANCE * UNLOAD_WALK_DISTANCE && canFlyForRepair()) {
			startFlyingForRepair(navigationState);
			return;
		}
		startWalkingForRepair(navigationState);
	}

	private boolean canFlyForRepair() {
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		return elytraProcess.isLoaded() && chest.is(Items.ELYTRA)
				&& remainingDurability(chest) > DURABILITY_SUPPLY_FLIGHT_THRESHOLD
				&& fireworkCount() > 0;
	}

	private void prepareRepairFlightSettings() {
		if (savedElytraMinimumDurability == null) savedElytraMinimumDurability = BaritoneAPI.getSettings().elytraMinimumDurability.value;
		if (savedElytraMinFireworksBeforeLanding == null) savedElytraMinFireworksBeforeLanding = BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = DURABILITY_SUPPLY_FLIGHT_THRESHOLD;
		BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value = -1;
	}

	private void startFlyingForRepair(AutomationState navigationState) {
		restoreAllowPlace();
		if (navigationState == AutomationState.NAVIGATING_TO_PERIMETER_PORTAL) enableBreakingForNavigation();
		prepareRepairFlightSettings();
		repairFlying = true;
		repairFlightAttempts++;
		elytraProcess.pathTo(repairNavigationTarget);
		transition(navigationState, "Flying to stage 7 target " + repairNavigationTarget.toShortString() + ".");
	}

	private void startWalkingForRepair(AutomationState navigationState) {
		repairFlying = false;
		if (navigationState == AutomationState.NAVIGATING_TO_PERIMETER_PORTAL) enableBreakingForNavigation();
		else disablePlacementForNavigation();
		if (repairStage == RepairStage.REPAIR_MACHINE) {
			customGoalProcess.setGoalAndPath(new GoalComposite(furnaceStandPositions(repairFurnaces.get(repairFurnaceIndex)).stream()
					.map(GoalBlock::new).toArray(Goal[]::new)));
		} else {
			customGoalProcess.setGoalAndPath(new GoalBlock(repairNavigationTarget));
		}
		transition(navigationState, "Walking to stage 7 target " + repairNavigationTarget.toShortString() + ".");
	}

	private void tickRepairNavigation() {
		boolean reached = repairStage == RepairStage.REPAIR_MACHINE
				? canReachFurnace(repairFurnaces.get(repairFurnaceIndex))
				: baritone.getPlayerContext().playerFeet().equals(repairNavigationTarget);
		if (reached) {
			if (elytraProcess.isActive()) elytraProcess.onLostControl();
			customGoalProcess.onLostControl();
			restoreRepairNavigationSettings();
			if (repairStage == RepairStage.REPAIR_MACHINE) beginFurnaceInteraction();
			else if (repairStage == RepairStage.RETURN_TO_MACHINE_TAKEOFF) startRepairPortalReturn();
			else beginPortalEntry();
			return;
		}
		if (repairFlying) {
			if (elytraProcess.isActive()) return;
			restoreRepairNavigationSettings();
			startWalkingForRepair(state);
			return;
		}
		if (customGoalProcess.isActive()) return;
		restoreAllowPlace();
		if (repairNavigationTarget.getY() > baritone.getPlayerContext().playerFeet().y
				&& repairFlightAttempts < 2 && canFlyForRepair()) {
			startFlyingForRepair(state);
			return;
		}
		restoreRepairNavigationSettings();
		transition(AutomationState.ERROR, "Stage 7 target is unreachable: " + repairNavigationTarget.toShortString() + ".");
	}

	private void restoreRepairNavigationSettings() {
		restoreAllowPlace();
		restoreElytraMinimumDurability();
		restoreElytraFireworkReserve();
	}

	private void beginPortalEntry() {
		restoreRepairNavigationSettings();
		portalWaitTicks = 0;
		portalExitCandidates = List.of();
		postPortalNavigationTarget = null;
		postPortalNavigationState = null;
		AutomationState portalState = repairStage == RepairStage.OUTBOUND_PERIMETER_PORTAL || repairStage == RepairStage.RETURN_PERIMETER_PORTAL
				? AutomationState.ENTERING_PERIMETER_PORTAL : AutomationState.ENTERING_REPAIR_PORTAL;
		transition(portalState, "Waiting inside stage 7 portal " + repairNavigationTarget.toShortString() + ".");
	}

	private void tickEnteringRepairPortal() {
		if (++portalWaitTicks > 400) {
			transition(AutomationState.ERROR, "Timed out while waiting for the stage 7 portal transition.");
			return;
		}
		baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
	}

	public void handleWorldChange() {
		if (returnPortalWaypoint != null
				&& (state == AutomationState.ENTERING_PERIMETER_PORTAL || state == AutomationState.ENTERING_REPAIR_PORTAL)) {
			stopProcesses();
			bindBaritone();
			if (repairStage != null) applyRepairRestrictions();
			restoreRepairNavigationSettings();
			BlockPos arrivalPortal = positionForReturnWaypoint(returnPortalWaypoint.counterpart());
			returnPortalWaypoint = null;
			beginClearRepairPortal(arrivalPortal, null, null);
			return;
		}
		if (repairStage == null || state != AutomationState.ENTERING_PERIMETER_PORTAL && state != AutomationState.ENTERING_REPAIR_PORTAL) {
			resetForWorldChange();
			return;
		}
		stopProcesses();
		bindBaritone();
		applyRepairRestrictions();
		restoreRepairNavigationSettings();
		portalWaitTicks = 0;
		switch (repairStage) {
			case OUTBOUND_PERIMETER_PORTAL -> {
				repairStage = RepairStage.OUTBOUND_REPAIR_PORTAL;
				beginClearRepairPortal(position(perimeterPortalNether), position(repairPortalNether), AutomationState.NAVIGATING_TO_REPAIR_PORTAL);
			}
			case OUTBOUND_REPAIR_PORTAL -> {
				repairStage = RepairStage.REPAIR_MACHINE;
				beginClearRepairPortal(position(repairPortalOverworld), null, AutomationState.NAVIGATING_TO_REPAIR_MACHINE);
			}
			case RETURN_REPAIR_PORTAL -> {
				repairStage = RepairStage.RETURN_PERIMETER_PORTAL;
				beginClearRepairPortal(position(repairPortalNether), position(perimeterPortalNether), AutomationState.NAVIGATING_TO_PERIMETER_PORTAL);
			}
			case RETURN_PERIMETER_PORTAL -> {
				repairStage = RepairStage.RETURN_TO_MINE;
				beginClearRepairPortal(position(perimeterPortalOverworld), null, null);
			}
			default -> transition(AutomationState.ERROR, "Unexpected stage 7 portal transition.");
		}
	}

	private void beginClearRepairPortal(BlockPos portal, BlockPos nextTarget, AutomationState nextState) {
		portalExitOrigin = currentClientPlayerFeet();
		portalExitSearchScans = 0;
		portalExitCandidates = findPortalExitCandidates(portalExitOrigin);
		postPortalNavigationTarget = nextTarget;
		postPortalNavigationState = nextState;
		disablePlacementForNavigation();
		if (!portalExitCandidates.isEmpty()) startPortalExitPath();
		transition(AutomationState.CLEARING_REPAIR_PORTAL, portalExitCandidates.isEmpty()
				? "Waiting for safe portal exit positions to load near " + portal.toShortString() + "."
				: "Walking clear of stage 7 portal " + portal.toShortString() + ".");
	}

	private void tickClearRepairPortal() {
		if (portalExitCandidates.contains(baritone.getPlayerContext().playerFeet())) {
			customGoalProcess.onLostControl();
			restoreAllowPlace();
			portalExitCandidates = List.of();
			if (postPortalNavigationState == AutomationState.NAVIGATING_TO_REPAIR_MACHINE) {
				startRepairNavigation(closestFurnaceStand(repairFurnaces.get(repairFurnaceIndex)), postPortalNavigationState);
			} else if (postPortalNavigationTarget == null) beginReturnToMine();
			else startRepairNavigation(postPortalNavigationTarget, postPortalNavigationState);
			return;
		}
		if (customGoalProcess.isActive()) return;
		if (++portalExitSearchScans > 40) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, "No reachable safe portal exit position was found within 20 seconds.");
			return;
		}
		portalExitOrigin = currentClientPlayerFeet();
		portalExitCandidates = findPortalExitCandidates(portalExitOrigin);
		if (!portalExitCandidates.isEmpty()) {
			startPortalExitPath();
			synchronize(AutomationState.CLEARING_REPAIR_PORTAL, "Walking clear of the stage 7 portal.");
			return;
		}
		synchronize(AutomationState.CLEARING_REPAIR_PORTAL, "Waiting for safe portal exit positions to load.");
	}

	private void startPortalExitPath() {
		customGoalProcess.setGoalAndPath(new GoalComposite(portalExitCandidates.stream().map(GoalBlock::new).toArray(Goal[]::new)));
	}

	private List<BlockPos> findPortalExitCandidates(BlockPos origin) {
		List<BlockPos> candidates = new ArrayList<>();
		BlockPos player = currentClientPlayerFeet();
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				int horizontalDistanceSquared = dx * dx + dz * dz;
				if (horizontalDistanceSquared < 9 || horizontalDistanceSquared > 64) continue;
				for (int dy = -4; dy <= 4; dy++) {
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
		List<BlockPos> positions = new ArrayList<>();
		var world = Minecraft.getInstance().level;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockPos position = furnace.offset(dx, 0, dz);
				if (world != null && world.getBlockState(position).getBlock() instanceof SlabBlock) position = position.above();
				if (isFurnaceStandPosition(position, furnace)) positions.add(position);
			}
		}
		return positions;
	}

	private BlockPos closestFurnaceStand(BlockPos furnace) {
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		return furnaceStandPositions(furnace).stream().min(Comparator.comparingDouble(player::distSqr))
				.orElseThrow(() -> new IllegalStateException("No safe reachable-distance stand was found for furnace " + furnace.toShortString() + "."));
	}

	private boolean canReachFurnace(BlockPos furnace) {
		return baritone.getPlayerContext().player().getEyePosition().distanceToSqr(Vec3.atCenterOf(furnace)) <= FURNACE_INTERACTION_DISTANCE_SQUARED;
	}

	private boolean isFurnaceStandPosition(BlockPos position, BlockPos furnace) {
		var world = Minecraft.getInstance().level;
		if (world == null) return false;
		BlockPos floor = position.below();
		BlockPos head = position.above();
		if (!world.isLoaded(floor) || !world.isLoaded(head)) return false;
		var floorState = world.getBlockState(floor);
		var feetState = world.getBlockState(position);
		var headState = world.getBlockState(head);
		if (!floorState.getFluidState().isEmpty()
				|| !(floorState.isFaceSturdy(world, floor, Direction.UP) || floorState.getBlock() instanceof SlabBlock)
				|| !feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()
				|| !feetState.getCollisionShape(world, position).isEmpty()
				|| !headState.getCollisionShape(world, head).isEmpty()) return false;
		Vec3 eye = new Vec3(position.getX() + 0.5, position.getY() + 1.62, position.getZ() + 0.5);
		return eye.distanceToSqr(Vec3.atCenterOf(furnace)) <= FURNACE_INTERACTION_DISTANCE_SQUARED;
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

	private void beginSupply(SupplyKind kind, boolean debug) {
		PositionConfig point = kind == SupplyKind.CONSUMABLES ? consumableSupplyPoint : durabilitySupplyPoint;
		if (point == null) {
			stopProcesses();
			restoreAllowPlace();
			transition(AutomationState.ERROR, "No " + kind.displayName + " supply point is configured.");
			return;
		}
		stateBeforeSupply = state;
		debugStage6Only = debug;
		miningReturnPoint = baritone.getPlayerContext().playerFeet();
		miningReturnDimension = baritone.getPlayerContext().world().dimension();
		returnAction = ReturnAction.AFTER_SUPPLY;
		supplyKind = kind;
		activeSupplyPoint = point;
		activeSupplyStand = new BlockPos(point.x, point.y + 1, point.z);
		supplyFlightAttempts = 0;
		durabilitySupplyPlan = kind == SupplyKind.DURABILITY ? captureDurabilitySupplyPlan() : null;
		if (kind == SupplyKind.DURABILITY && durabilitySupplyPlan.targetHealthyCounts().isEmpty()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, "No low-durability tool or elytra requires supply replacement.");
			return;
		}
		stopProcesses();
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		if (player.distSqr(activeSupplyStand) > (double) UNLOAD_WALK_DISTANCE * UNLOAD_WALK_DISTANCE && canFlyToSupply()) {
			startFlyingToSupply();
			return;
		}
		startWalkingToSupply();
	}

	private boolean canFlyToDestination() {
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		return elytraProcess.isLoaded() && chest.is(Items.ELYTRA)
				&& remainingDurability(chest) > ELYTRA_DURABILITY_THRESHOLD
				&& fireworkCount() > BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value;
	}

	private boolean canFlyToSupply() {
		ItemStack chest = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
		return elytraProcess.isLoaded() && chest.is(Items.ELYTRA)
				&& remainingDurability(chest) > DURABILITY_SUPPLY_FLIGHT_THRESHOLD
				&& fireworkCount() > 0;
	}

	private AutomationState supplyNavigationState() {
		return supplyKind == SupplyKind.CONSUMABLES
				? AutomationState.NAVIGATING_TO_RESUPPLY
				: AutomationState.NAVIGATING_TO_DURABILITY_SUPPLY;
	}

	private AutomationState supplyInteractionState() {
		return supplyKind == SupplyKind.CONSUMABLES
				? AutomationState.RESUPPLYING
				: AutomationState.SWAPPING_DURABILITY_AT_SUPPLY;
	}

	private void startWalkingToSupply() {
		supplyFlying = false;
		disablePlacementForNavigation();
		if (baritone.getPlayerContext().playerFeet().equals(activeSupplyStand)) {
			restoreAllowPlace();
			beginSupplyInteraction();
			return;
		}
		customGoalProcess.setGoalAndPath(new GoalBlock(activeSupplyStand));
		transition(supplyNavigationState(), "Walking to the " + supplyKind.displayName + " supply chest " + activeSupplyStand.toShortString() + ".");
	}

	private void tickSupplyNavigation() {
		if (baritone.getPlayerContext().playerFeet().equals(activeSupplyStand)) {
			if (elytraProcess.isActive()) elytraProcess.onLostControl();
			customGoalProcess.onLostControl();
			restoreElytraMinimumDurability();
			restoreAllowPlace();
			beginSupplyInteraction();
			return;
		}
		if (supplyFlying) {
			if (elytraProcess.isActive()) return;
			restoreElytraMinimumDurability();
			restoreElytraFireworkReserve();
			startWalkingToSupply();
			return;
		}
		if (customGoalProcess.isActive()) return;
		restoreAllowPlace();
		if (activeSupplyStand.getY() > baritone.getPlayerContext().playerFeet().y
				&& supplyFlightAttempts < 2 && canFlyToSupply()) {
			startFlyingToSupply();
			return;
		}
		restoreElytraMinimumDurability();
		restoreElytraFireworkReserve();
		transition(AutomationState.ERROR, "The " + supplyKind.displayName + " supply chest is unreachable: " + activeSupplyStand.toShortString() + ".");
	}

	private void startFlyingToSupply() {
		restoreAllowPlace();
		if (savedElytraMinFireworksBeforeLanding == null) {
			savedElytraMinFireworksBeforeLanding = BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value;
		}
		BaritoneAPI.getSettings().elytraMinFireworksBeforeLanding.value = -1;
		if (savedElytraMinimumDurability == null) savedElytraMinimumDurability = BaritoneAPI.getSettings().elytraMinimumDurability.value;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = DURABILITY_SUPPLY_FLIGHT_THRESHOLD;
		supplyFlying = true;
		supplyFlightAttempts++;
		elytraProcess.pathTo(activeSupplyStand);
		transition(supplyNavigationState(), "Flying to the " + supplyKind.displayName + " supply point " + activeSupplyStand.toShortString() + ".");
	}

	private void disablePlacementForNavigation() {
		if (savedAllowPlace == null) savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
		BaritoneAPI.getSettings().allowPlace.value = false;
	}

	private void enableBreakingForNavigation() {
		if (savedAllowBreak == null) savedAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
		if (savedAllowPlace == null) savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
		BaritoneAPI.getSettings().allowBreak.value = true;
		BaritoneAPI.getSettings().allowPlace.value = false;
	}

	private void restoreAllowPlace() {
		if (savedAllowBreak != null) BaritoneAPI.getSettings().allowBreak.value = savedAllowBreak;
		if (savedAllowPlace != null) BaritoneAPI.getSettings().allowPlace.value = savedAllowPlace;
		savedAllowBreak = null;
		savedAllowPlace = null;
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
		supplyPhase = supplyKind == SupplyKind.DURABILITY ? SupplyPhase.PREPARING : SupplyPhase.OPENING;
		supplyInteractionTicks = 0;
		transition(supplyInteractionState(), "Preparing the " + supplyKind.displayName + " supply chest.");
	}

	private void tickSupplyInteraction() {
		if (!inventoryClicks.isEmpty()) return;
		switch (supplyPhase) {
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
				transition(AutomationState.ERROR, "No empty inventory slot is available for the low-durability chest item.");
				return;
			}
			queuePickupSwap(CHEST_MENU_SLOT, menuSlot(empty));
			return;
		}
		ItemStack offhand = inventory.getItem(OFFHAND_INVENTORY_SLOT);
		if (isLowMonitoredItem(offhand)) {
			int empty = firstEmptyInventorySlot();
			if (empty < 0) {
				transition(AutomationState.ERROR, "No empty inventory slot is available for the low-durability offhand item.");
				return;
			}
			queuePickupSwap(OFFHAND_MENU_SLOT, menuSlot(empty));
			return;
		}
		supplyPhase = SupplyPhase.OPENING;
		supplyInteractionTicks = 0;
	}

	private void openSupplyChest() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			supplyPhase = SupplyPhase.TRANSFERRING;
			return;
		}
		if (supplyInteractionTicks++ > 40) {
			transition(AutomationState.ERROR, "Timed out while opening the " + supplyKind.displayName + " supply chest.");
			return;
		}
		BlockPos chest = new BlockPos(activeSupplyPoint.x, activeSupplyPoint.y, activeSupplyPoint.z);
		Vec3 hitLocation = new Vec3(activeSupplyPoint.x + 0.5, activeSupplyPoint.y + 1.0, activeSupplyPoint.z + 0.5);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), hitLocation, baritone.getPlayerContext().playerRotations()), true);
		if (supplyInteractionTicks % 5 == 1) {
			baritone.getPlayerContext().playerController().processRightClickBlock(
					baritone.getPlayerContext().player(), baritone.getPlayerContext().world(), InteractionHand.MAIN_HAND,
					new BlockHitResult(hitLocation, Direction.UP, chest, false));
		}
	}

	private void transferSupplyItems() {
		if (baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu) {
			transition(AutomationState.ERROR, "The supply chest closed before transfer completed.");
			return;
		}
		if (supplyKind == SupplyKind.CONSUMABLES) transferConsumables();
		else transferDurabilityItems();
	}

	private void transferConsumables() {
		if (configuredFoodCount() < FOOD_RESUPPLY_TARGET) {
			if (!queueConsumableTransfer(allowedFoods, FOOD_RESUPPLY_TARGET - configuredFoodCount(), "configured food")) return;
		}
		if (fireworkCount() < FIREWORK_RESUPPLY_TARGET) {
			if (!queueConsumableTransfer(Set.of(Items.FIREWORK_ROCKET), FIREWORK_RESUPPLY_TARGET - fireworkCount(), "firework rockets")) return;
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
				transition(AutomationState.ERROR, "No inventory space is available for " + name + ".");
				return false;
			}
			ItemStack target = baritone.getPlayerContext().player().getInventory().getItem(targetInventorySlot);
			int capacity = target.isEmpty() ? source.getMaxStackSize() : target.getMaxStackSize() - target.getCount();
			int transfer = Math.min(needed, Math.min(source.getCount(), capacity));
			boolean mergeWhole = transfer == source.getCount() || !target.isEmpty() && transfer == capacity;
			queueExactChestTransfer(sourceSlot, chestInventoryMenuSlot(targetInventorySlot, chestSlots), transfer, source.getCount(), mergeWhole);
			synchronize(supplyInteractionState(), "Transferring " + transfer + " " + name + " from the supply chest.");
			return false;
		}
		transition(AutomationState.ERROR, "The supply chest does not contain enough " + name + ".");
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
		for (Map.Entry<Item, Integer> requirement : durabilitySupplyPlan.targetHealthyCounts().entrySet()) {
			if (healthyOwnedCount(requirement.getKey()) >= requirement.getValue()) continue;
			for (int chestSlot = 0; chestSlot < chestSlots; chestSlot++) {
				ItemStack source = baritone.getPlayerContext().player().containerMenu.getSlot(chestSlot).getItem();
				if (source.is(requirement.getKey()) && remainingDurability(source) > durabilityThreshold(requirement.getKey())) {
					inventoryClicks.add(new InventoryClick(chestSlot, 0, ContainerInput.QUICK_MOVE));
					return;
				}
			}
			transition(AutomationState.ERROR, "The durability supply chest has no healthy replacement for " + BuiltInRegistries.ITEM.getKey(requirement.getKey()) + ".");
			return;
		}
		baritone.getPlayerContext().player().closeContainer();
		supplyPhase = SupplyPhase.FINALIZING;
	}

	private void finalizeSupplyEquipment() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) return;
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		if (durabilitySupplyPlan.chestItem() != null) {
			ItemStack equipped = baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST);
			if (!equipped.is(durabilitySupplyPlan.chestItem()) || remainingDurability(equipped) <= durabilityThreshold(equipped)) {
				int source = findHealthyInventoryItem(durabilitySupplyPlan.chestItem());
				if (source < 0) {
					transition(AutomationState.ERROR, "The recovered elytra could not be equipped.");
					return;
				}
				queuePickupSwap(menuSlot(source), CHEST_MENU_SLOT);
				return;
			}
		}
		if (durabilitySupplyPlan.offhandItem() != null) {
			ItemStack offhand = inventory.getItem(OFFHAND_INVENTORY_SLOT);
			if (!offhand.is(durabilitySupplyPlan.offhandItem()) || remainingDurability(offhand) <= durabilityThreshold(offhand)) {
				int source = findHealthyInventoryItem(durabilitySupplyPlan.offhandItem());
				if (source < 0) {
					transition(AutomationState.ERROR, "The recovered offhand item could not be equipped.");
					return;
				}
				queuePickupSwap(menuSlot(source), OFFHAND_MENU_SLOT);
				return;
			}
		}
		if (durabilitySupplyPlan.selectedItem() != null) {
			inventory.setSelectedSlot(durabilitySupplyPlan.selectedSlot());
			ItemStack selected = inventory.getItem(durabilitySupplyPlan.selectedSlot());
			if (!selected.is(durabilitySupplyPlan.selectedItem()) || remainingDurability(selected) <= durabilityThreshold(selected)) {
				int source = findHealthyInventoryItem(durabilitySupplyPlan.selectedItem());
				if (source < 0) {
					transition(AutomationState.ERROR, "The recovered selected tool could not be equipped.");
					return;
				}
				baritone.getPlayerContext().playerController().windowClick(
						baritone.getPlayerContext().player().inventoryMenu.containerId,
						menuSlot(source), durabilitySupplyPlan.selectedSlot(), ContainerInput.SWAP,
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
		activeSupplyPoint = null;
		activeSupplyStand = null;
		supplyPhase = null;
		beginReturnToMine();
	}

	private void beginFurnaceInteraction() {
		restoreRepairNavigationSettings();
		if (repairMachineTakeoffPoint == null) repairMachineTakeoffPoint = baritone.getPlayerContext().playerFeet();
		repairFurnacePhase = RepairFurnacePhase.PREPARING;
		repairInteractionTicks = 0;
		transition(AutomationState.REPAIRING, "Preparing furnace " + (repairFurnaceIndex + 1) + "/" + repairFurnaces.size() + ".");
	}

	private void tickRepairing() {
		if (!inventoryClicks.isEmpty()) return;
		switch (repairFurnacePhase) {
			case PREPARING -> prepareRepairItem();
			case OPENING -> openRepairFurnace();
			case TAKING_OUTPUT -> takeRepairOutput();
			case WAITING_FOR_REPAIR -> finishRepairFurnace();
		}
	}

	private void prepareRepairItem() {
		selectNextRepairTool();
		repairFurnacePhase = RepairFurnacePhase.OPENING;
		repairInteractionTicks = 0;
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
			repairFurnacePhase = RepairFurnacePhase.TAKING_OUTPUT;
			return;
		}
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
			return;
		}
		if (repairInteractionTicks++ > 40) {
			transition(AutomationState.ERROR, "Timed out while opening furnace " + (repairFurnaceIndex + 1) + ".");
			return;
		}
		BlockPos furnace = repairFurnaces.get(repairFurnaceIndex);
		Vec3 hitLocation = Vec3.atCenterOf(furnace);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), hitLocation, baritone.getPlayerContext().playerRotations()), true);
		if (repairInteractionTicks % 5 == 1) {
			baritone.getPlayerContext().playerController().processRightClickBlock(
					baritone.getPlayerContext().player(), baritone.getPlayerContext().world(), InteractionHand.MAIN_HAND,
					new BlockHitResult(hitLocation, Direction.UP, furnace, false));
		}
	}

	private void takeRepairOutput() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof AbstractFurnaceMenu menu)) {
			transition(AutomationState.ERROR, "The furnace closed before its output was collected.");
			return;
		}
		ItemStack output = menu.getSlot(2).getItem();
		if (output.isEmpty()) {
			baritone.getPlayerContext().player().closeContainer();
			advanceRepairFurnace();
			return;
		}
		inventoryClicks.add(new InventoryClick(2, 1, ContainerInput.THROW));
		repairFurnacePhase = RepairFurnacePhase.WAITING_FOR_REPAIR;
		repairDurabilitySnapshot = repairDurabilityTotal();
		repairStableTicks = 0;
		synchronize(AutomationState.REPAIRING, "Collecting experience and dropping the furnace output.");
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
			repairStableTicks = 0;
			synchronize(AutomationState.REPAIRING, "Switched to the next repair tool while absorbing remaining experience.");
			return;
		}
		int durability = repairDurabilityTotal();
		if (durability > repairDurabilitySnapshot) {
			repairDurabilitySnapshot = durability;
			repairStableTicks = 0;
			return;
		}
		if (++repairStableTicks < REPAIR_EXPERIENCE_STABLE_TICKS) return;
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		advanceRepairFurnace();
	}

	private void advanceRepairFurnace() {
		if (++repairFurnaceIndex >= repairFurnaces.size()) {
			if (!repairTargetsFull()) transition(AutomationState.ERROR, "The furnace row was exhausted before all repair targets reached full durability.");
			else beginRepairReturn();
			return;
		}
		repairStage = RepairStage.REPAIR_MACHINE;
		startRepairNavigation(closestFurnaceStand(repairFurnaces.get(repairFurnaceIndex)), AutomationState.NAVIGATING_TO_REPAIR_MACHINE);
	}

	private RepairPlan captureRepairPlan(boolean includeAllDamaged) {
		Map<Item, Integer> targets = new LinkedHashMap<>();
		Map<Item, Integer> baselineFull = new LinkedHashMap<>();
		Inventory inventory = baritone.getPlayerContext().player().getInventory();
		for (int slot = 0; slot < 36; slot++) collectRepairPlanItem(inventory.getItem(slot), targets, baselineFull, includeAllDamaged);
		collectRepairPlanItem(inventory.getItem(OFFHAND_INVENTORY_SLOT), targets, baselineFull, includeAllDamaged);
		collectRepairPlanItem(baritone.getPlayerContext().player().getItemBySlot(EquipmentSlot.CHEST), targets, baselineFull, includeAllDamaged);
		return new RepairPlan(Map.copyOf(targets), Map.copyOf(baselineFull));
	}

	private void collectRepairPlanItem(ItemStack stack, Map<Item, Integer> targets, Map<Item, Integer> baselineFull, boolean includeAllDamaged) {
		if (!isMonitoredItem(stack)) return;
		if (remainingDurability(stack) <= durabilityThreshold(stack)
				|| includeAllDamaged && remainingDurability(stack) < stack.getMaxDamage()) targets.merge(stack.getItem(), 1, Integer::sum);
		else if (remainingDurability(stack) == stack.getMaxDamage()) baselineFull.merge(stack.getItem(), 1, Integer::sum);
	}

	private boolean repairTargetsFull() {
		for (Map.Entry<Item, Integer> target : repairPlan.targetCounts().entrySet()) {
			int required = repairPlan.baselineFullCounts().getOrDefault(target.getKey(), 0) + target.getValue();
			if (fullMonitoredItemCount(target.getKey()) < required) return false;
		}
		return true;
	}

	private boolean repairItemNeedsMoreFullStacks(Item item) {
		Integer targets = repairPlan.targetCounts().get(item);
		if (targets == null) return false;
		return fullMonitoredItemCount(item) < repairPlan.baselineFullCounts().getOrDefault(item, 0) + targets;
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
		return stack.isEmpty() || !repairPlan.targetCounts().containsKey(stack.getItem()) ? 0 : remainingDurability(stack);
	}

	private void beginRepairReturn() {
		if (!(baritone.getPlayerContext().player().containerMenu instanceof InventoryMenu)) {
			baritone.getPlayerContext().player().closeContainer();
		}
		if (repairMachineTakeoffPoint != null && !baritone.getPlayerContext().playerFeet().equals(repairMachineTakeoffPoint)) {
			repairStage = RepairStage.RETURN_TO_MACHINE_TAKEOFF;
			repairNavigationTarget = repairMachineTakeoffPoint;
			repairFlying = false;
			repairFlightAttempts = 2;
			startWalkingForRepair(AutomationState.NAVIGATING_TO_REPAIR_MACHINE);
			return;
		}
		startRepairPortalReturn();
	}

	private void startRepairPortalReturn() {
		repairStage = RepairStage.RETURN_TO_MINE;
		beginReturnToMine();
	}

	private DurabilitySupplyPlan captureDurabilitySupplyPlan() {
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
		return new DurabilitySupplyPlan(
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
				&& (stack.is(Items.ELYTRA) || stack.getItem().components().has(DataComponents.TOOL));
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
		supplyKind = null;
		supplyPhase = null;
		activeSupplyPoint = null;
		activeSupplyStand = null;
		durabilitySupplyPlan = null;
		supplyFlying = false;
		supplyFlightAttempts = 0;
		returnAction = ReturnAction.NONE;
	}

	private void clearRepairContext() {
		restoreRepairNavigationSettings();
		restoreRepairRestrictions();
		repairStage = null;
		repairNavigationTarget = null;
		repairFlying = false;
		repairFlightAttempts = 0;
		portalWaitTicks = 0;
		portalExitCandidates = List.of();
		portalExitOrigin = null;
		portalExitSearchScans = 0;
		postPortalNavigationTarget = null;
		postPortalNavigationState = null;
		repairFurnaces = List.of();
		repairFurnaceIndex = 0;
		repairFurnacePhase = null;
		repairInteractionTicks = 0;
		repairMachineTakeoffPoint = null;
		repairDurabilitySnapshot = 0;
		repairStableTicks = 0;
		repairPlan = null;
		debugStage7Only = false;
	}

	private static int chestInventoryMenuSlot(int inventorySlot, int chestSlots) {
		return chestSlots + (inventorySlot < 9 ? 27 + inventorySlot : inventorySlot - 9);
	}

	private void requestUnload() {
		if (!debugUnloadOnly) {
			miningReturnPoint = baritone.getPlayerContext().playerFeet();
			miningReturnDimension = baritone.getPlayerContext().world().dimension();
			returnAction = ReturnAction.AFTER_UNLOAD;
		}
		stopProcesses();
		if (unloadingPoints.isEmpty()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, "Inventory is full but no unloading point is configured.");
			return;
		}
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		unloadingPoint = unloadingPoints.stream()
				.min(Comparator.comparingLong(point -> horizontalDistanceSquared(player, point.point())))
				.orElseThrow();
		UnloadCandidate preferred = findUnloadCandidates(unloadingPoint.point(), player.y).stream().findFirst().orElse(null);
		BlockPos flightTarget = preferred == null
				? new BlockPos(unloadingPoint.point().x, player.y, unloadingPoint.point().z)
				: preferred.position();
		unloadFlightAttempts = 0;
		if (player.distSqr(flightTarget) <= (double) UNLOAD_WALK_DISTANCE * UNLOAD_WALK_DISTANCE || !canFlyToDestination()) {
			beginUnloadApproach();
			return;
		}
		startFlyingToUnload(flightTarget);
	}

	private void tickNavigateToUnload() {
		if (elytraProcess.isActive()) {
			synchronize(AutomationState.NAVIGATING_TO_UNLOAD, "Flying near unloading point " + unloadingPoint.name() + ".");
			return;
		}
		beginUnloadApproach();
	}

	private void beginUnloadApproach() {
		if (elytraProcess != null && elytraProcess.isActive()) elytraProcess.onLostControl();
		disablePlacementForNavigation();
		int currentY = baritone.getPlayerContext().playerFeet().y;
		unloadCandidates = findUnloadCandidates(unloadingPoint.point(), currentY);
		unloadCandidateIndex = 0;
		if (unloadCandidates.isEmpty()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, "No loaded safe standing block was found near unloading point " + unloadingPoint.name() + ".");
			return;
		}
		pathToUnloadCandidate();
	}

	private void pathToUnloadCandidate() {
		UnloadCandidate candidate = unloadCandidates.get(unloadCandidateIndex);
		customGoalProcess.setGoalAndPath(new GoalBlock(candidate.position()));
		transition(AutomationState.APPROACHING_UNLOAD, "Walking to unloading edge " + candidate.position().toShortString() + " for " + unloadingPoint.name() + ".");
	}

	private void tickApproachingUnload() {
		UnloadCandidate candidate = unloadCandidates.get(unloadCandidateIndex);
		if (candidate.position().equals(baritone.getPlayerContext().playerFeet())) {
			customGoalProcess.onLostControl();
			restoreAllowPlace();
			unloadEdgePosition = unloadEdgePosition(candidate.position(), unloadingPoint.point());
			transition(AutomationState.POSITIONING_FOR_UNLOAD, "Moving to the safe block edge for " + unloadingPoint.name() + ".");
			return;
		}
		if (customGoalProcess.isActive()) return;
		if (candidate.position().getY() > baritone.getPlayerContext().playerFeet().y
				&& unloadFlightAttempts < 2 && canFlyToDestination()) {
			startFlyingToUnload(candidate.position());
			return;
		}
		if (++unloadCandidateIndex >= unloadCandidates.size()) {
			restoreAllowPlace();
			transition(AutomationState.ERROR, "No reachable standing block was found near unloading point " + unloadingPoint.name() + ".");
			return;
		}
		pathToUnloadCandidate();
	}

	private void startFlyingToUnload(BlockPos target) {
		restoreAllowPlace();
		unloadFlightAttempts++;
		elytraProcess.pathTo(target);
		transition(AutomationState.NAVIGATING_TO_UNLOAD, "Flying near unloading point " + unloadingPoint.name() + " at X=" + unloadingPoint.point().x + ", Z=" + unloadingPoint.point().z + ".");
	}

	private void tickPositioningForUnload() {
		Vec3 playerPosition = baritone.getPlayerContext().player().position();
		double dx = unloadEdgePosition.x - playerPosition.x;
		double dz = unloadEdgePosition.z - playerPosition.z;
		double distanceSquared = dx * dx + dz * dz;
		baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
		if (distanceSquared > 0.0016) {
			Vec3 lookTarget = new Vec3(unloadEdgePosition.x, baritone.getPlayerContext().playerHead().y, unloadEdgePosition.z);
			baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
					baritone.getPlayerContext().playerHead(), lookTarget, baritone.getPlayerContext().playerRotations()), false);
			baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
			synchronize(AutomationState.POSITIONING_FOR_UNLOAD, "Moving to the safe block edge for " + unloadingPoint.name() + ".");
			return;
		}
		baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
		unloadSettleTicks = 5;
		transition(AutomationState.UNLOADING, "Facing unloading channel " + unloadingPoint.name() + ".");
	}

	private void tickUnloading() {
		UnloadingPointConfig point = unloadingPoint.point();
		Vec3 target = new Vec3(point.x + 0.5, point.minY + 0.5, point.z + 0.5);
		baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
		baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), target, baritone.getPlayerContext().playerRotations()).withPitch(90.0F), false);
		if (!inventoryClicks.isEmpty()) return;
		if (unloadSettleTicks > 0) {
			unloadSettleTicks--;
			return;
		}
		int disposableSlot = firstDisposableSlot();
		if (disposableSlot >= 0) {
			inventoryClicks.add(new InventoryClick(menuSlot(disposableSlot), 1, ContainerInput.THROW));
			unloadSettleTicks = 1;
			synchronize(AutomationState.UNLOADING, "Dropping mining products into " + unloadingPoint.name() + ".");
			return;
		}
		unloadingPoint = null;
		unloadCandidates = List.of();
		unloadEdgePosition = null;
		baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
		if (debugUnloadOnly) {
			debugUnloadOnly = false;
			transition(AutomationState.COMPLETE, "Stage 5 debug unloading complete.");
		} else if (miningCompletePending) transition(AutomationState.COMPLETE, "Mining and unloading complete.");
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
		if (player.distSqr(activeReturnTarget) > (double) UNLOAD_WALK_DISTANCE * UNLOAD_WALK_DISTANCE && canFlyForReturn()) {
			startFlyingBackToMine();
			return;
		}
		startWalkingBackToMine();
	}

	private ReturnWaypoint chooseReturnWaypoint() {
		ResourceKey<Level> currentDimension = baritone.getPlayerContext().world().dimension();
		BlockPos current = baritone.getPlayerContext().playerFeet();
		List<ReturnRouteCandidate> candidates = new ArrayList<>();
		if (currentDimension.equals(miningReturnDimension)) {
			candidates.add(new ReturnRouteCandidate(ReturnWaypoint.MINE, distance(current, miningReturnPoint)));
		}
		if (hasReturnPortalConfiguration()) {
			if (miningReturnDimension.equals(Level.OVERWORLD)) {
				if (currentDimension.equals(Level.OVERWORLD)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_OVERWORLD,
							distance(current, position(repairPortalOverworld)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(repairPortalNether), position(perimeterPortalNether)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(perimeterPortalOverworld), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_OVERWORLD,
							distance(current, position(perimeterPortalOverworld)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(perimeterPortalNether), position(repairPortalNether)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(repairPortalOverworld), miningReturnPoint)));
				} else if (currentDimension.equals(Level.NETHER)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_NETHER,
							distance(current, position(repairPortalNether)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(repairPortalOverworld), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_NETHER,
							distance(current, position(perimeterPortalNether)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(perimeterPortalOverworld), miningReturnPoint)));
				}
			} else if (miningReturnDimension.equals(Level.NETHER)) {
				if (currentDimension.equals(Level.NETHER)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_NETHER,
							distance(current, position(repairPortalNether)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(repairPortalOverworld), position(perimeterPortalOverworld)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(perimeterPortalNether), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_NETHER,
							distance(current, position(perimeterPortalNether)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(perimeterPortalOverworld), position(repairPortalOverworld)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(repairPortalNether), miningReturnPoint)));
				} else if (currentDimension.equals(Level.OVERWORLD)) {
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.REPAIR_OVERWORLD,
							distance(current, position(repairPortalOverworld)) + PORTAL_TRANSITION_DISTANCE_COST
									+ distance(position(repairPortalNether), miningReturnPoint)));
					candidates.add(new ReturnRouteCandidate(ReturnWaypoint.PERIMETER_OVERWORLD,
							distance(current, position(perimeterPortalOverworld)) + PORTAL_TRANSITION_DISTANCE_COST
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
		return repairStage != null ? canFlyForRepair() : canFlyToDestination();
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
		customGoalProcess.setGoalAndPath(new GoalBlock(activeReturnTarget));
		transition(AutomationState.RETURNING_TO_MINE, "Walking to return waypoint " + activeReturnWaypoint.displayName + " " + activeReturnTarget.toShortString() + ".");
	}

	private void tickReturningToMine() {
		if (baritone.getPlayerContext().playerFeet().equals(activeReturnTarget)) {
			if (elytraProcess.isActive()) elytraProcess.onLostControl();
			customGoalProcess.onLostControl();
			restoreRepairNavigationSettings();
			restoreAllowPlace();
			finishReturnWaypoint();
			return;
		}
		if (returningByElytra) {
			if (elytraProcess.isActive()) {
				synchronize(AutomationState.RETURNING_TO_MINE, "Flying to return waypoint " + activeReturnWaypoint.displayName + " " + activeReturnTarget.toShortString() + ".");
				return;
			}
			restoreRepairNavigationSettings();
			startWalkingBackToMine();
			return;
		}
		if (customGoalProcess.isActive()) return;
		if (returnFlightAttempts < 2 && canFlyForReturn()) {
			startFlyingBackToMine();
			return;
		}
		restoreAllowPlace();
		transition(AutomationState.ERROR, "Return waypoint is unreachable: " + activeReturnTarget.toShortString() + ".");
	}

	private void startFlyingBackToMine() {
		restoreAllowPlace();
		if (activeReturnWaypoint == ReturnWaypoint.PERIMETER_OVERWORLD || activeReturnWaypoint == ReturnWaypoint.PERIMETER_NETHER) {
			enableBreakingForNavigation();
		}
		if (repairStage != null) prepareRepairFlightSettings();
		returningByElytra = true;
		returnFlightAttempts++;
		elytraProcess.pathTo(activeReturnTarget);
		transition(AutomationState.RETURNING_TO_MINE, "Flying to return waypoint " + activeReturnWaypoint.displayName + " " + activeReturnTarget.toShortString() + ".");
	}

	private void finishReturnWaypoint() {
		if (activeReturnWaypoint == ReturnWaypoint.MINE) {
			finishReturnToMine();
			return;
		}
		returnPortalWaypoint = activeReturnWaypoint;
		portalWaitTicks = 0;
		AutomationState portalState = activeReturnWaypoint == ReturnWaypoint.REPAIR_OVERWORLD || activeReturnWaypoint == ReturnWaypoint.REPAIR_NETHER
				? AutomationState.ENTERING_REPAIR_PORTAL : AutomationState.ENTERING_PERIMETER_PORTAL;
		transition(portalState, "Waiting inside return portal " + activeReturnTarget.toShortString() + ".");
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
		returnAction = ReturnAction.NONE;
		if (completedAction == ReturnAction.AFTER_SUPPLY) clearSupplyContext();
		if (completedAction == ReturnAction.AFTER_SUPPLY && debugStage6Only) {
			debugStage6Only = false;
			transition(AutomationState.COMPLETE, "Stage 6 debug supply flow complete.");
		} else if (completedAction == ReturnAction.AFTER_SUPPLY && stateBeforeSupply == AutomationState.COLLECTING_DROPS) {
			beginDropCollection(miningCompletePending);
		} else if (completedAction == ReturnAction.AFTER_REPAIR && debugStage7Only) {
			clearRepairContext();
			transition(AutomationState.COMPLETE, "Stage 7 repair debug flow complete.");
		} else if (completedAction == ReturnAction.AFTER_REPAIR) {
			clearRepairContext();
			if (stateBeforeSupply == AutomationState.COLLECTING_DROPS) beginDropCollection(miningCompletePending);
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
		if (++watchdogStationaryScans < 30) return false;
		watchdogStationaryScans = 0;
		if (++watchdogRetries > 2) {
			stopProcesses();
			transition(AutomationState.ERROR, "Navigation made no positional progress after two retries toward " + target.toShortString() + ".");
			return true;
		}
		restartWatchedNavigation();
		return true;
	}

	private BlockPos watchdogNavigationTarget() {
		return switch (state) {
			case RETURNING_TO_MINE -> activeReturnTarget;
			case NAVIGATING_TO_RESUPPLY, NAVIGATING_TO_DURABILITY_SUPPLY -> activeSupplyStand;
			case NAVIGATING_TO_PERIMETER_PORTAL, NAVIGATING_TO_REPAIR_PORTAL, NAVIGATING_TO_REPAIR_MACHINE -> repairNavigationTarget;
			case NAVIGATING_TO_UNLOAD -> unloadingPoint == null ? null
					: new BlockPos(unloadingPoint.point().x, baritone.getPlayerContext().playerFeet().y, unloadingPoint.point().z);
			case APPROACHING_UNLOAD -> unloadCandidateIndex < unloadCandidates.size() ? unloadCandidates.get(unloadCandidateIndex).position() : null;
			default -> null;
		};
	}

	private boolean isManagedNavigationState(AutomationState candidate) {
		return candidate == AutomationState.RETURNING_TO_MINE
				|| candidate == AutomationState.NAVIGATING_TO_RESUPPLY
				|| candidate == AutomationState.NAVIGATING_TO_DURABILITY_SUPPLY
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
				if (player.distSqr(activeSupplyStand) > (double) UNLOAD_WALK_DISTANCE * UNLOAD_WALK_DISTANCE && canFlyToSupply()) startFlyingToSupply();
				else startWalkingToSupply();
			}
			case NAVIGATING_TO_PERIMETER_PORTAL, NAVIGATING_TO_REPAIR_PORTAL, NAVIGATING_TO_REPAIR_MACHINE -> {
				if (repairStage == RepairStage.RETURN_TO_MACHINE_TAKEOFF) {
					repairFlying = false;
					repairFlightAttempts = 2;
					startWalkingForRepair(state);
				} else startRepairNavigation(repairNavigationTarget, state);
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

	private List<UnloadCandidate> findUnloadCandidates(UnloadingPointConfig point, int preferredY) {
		List<UnloadCandidate> candidates = new ArrayList<>();
		int minY = baritone.getPlayerContext().world().dimensionType().minY() + 1;
		int maxY = baritone.getPlayerContext().world().dimensionType().minY()
				+ baritone.getPlayerContext().world().dimensionType().height() - 2;
		BetterBlockPos player = baritone.getPlayerContext().playerFeet();
		for (int dx = -UNLOAD_LANDING_SEARCH_RADIUS; dx <= UNLOAD_LANDING_SEARCH_RADIUS; dx++) {
			for (int dz = -UNLOAD_LANDING_SEARCH_RADIUS; dz <= UNLOAD_LANDING_SEARCH_RADIUS; dz++) {
				int horizontalDistanceSquared = dx * dx + dz * dz;
				if (horizontalDistanceSquared == 0 || horizontalDistanceSquared > UNLOAD_LANDING_SEARCH_RADIUS * UNLOAD_LANDING_SEARCH_RADIUS) continue;
				int x = point.x + dx;
				int z = point.z + dz;
				BlockPos position = closestSafeStandingPosition(x, z, preferredY, minY, maxY);
				if (position != null) {
					candidates.add(new UnloadCandidate(position, horizontalDistanceSquared, Math.abs(position.getY() - preferredY), player.distSqr(position)));
				}
			}
		}
		candidates.sort(Comparator.comparingInt(UnloadCandidate::horizontalDistanceSquared)
				.thenComparingInt(UnloadCandidate::yDifference)
				.thenComparing((first, second) -> Integer.compare(second.position().getY(), first.position().getY()))
				.thenComparingDouble(UnloadCandidate::playerDistanceSquared));
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
		double maxOffset = 0.7;
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
		if (slot.stack().is(Items.ELYTRA)) elytras.add(slot);
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
			synchronize(state, "Replacing low-durability " + BuiltInRegistries.ITEM.getKey(low.stack().getItem()) + ".");
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
		synchronize(state, "Replaced low-durability " + BuiltInRegistries.ITEM.getKey(low.stack().getItem()) + ".");
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
		if (state == AutomationState.COLLECTING_DROPS && customGoalProcess != null) customGoalProcess.onLostControl();
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
		transition(AutomationState.EATING, "Eating configured food.");
		return true;
	}

	private void tickEating() {
		if (baritone.getPlayerContext().player().getFoodData().getFoodLevel() > FOOD_LEVEL_THRESHOLD) {
			baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
			Minecraft.getInstance().options.keyUse.setDown(false);
			if (stateBeforeEating == AutomationState.MINING && miningProcess != null) {
				miningProcess.resume();
				transition(AutomationState.MINING, "Eating complete. Mining resumed.");
			} else if (stateBeforeEating == AutomationState.COLLECTING_DROPS) {
				transition(AutomationState.COLLECTING_DROPS, "Eating complete. Drop collection resumed.");
			} else if (isManagedNavigationState(stateBeforeEating)) {
				transition(stateBeforeEating, "Eating complete. Navigation resumed.");
				watchdogStationaryScans = 0;
				restartWatchedNavigation();
			} else transition(stateBeforeEating, "Eating complete.");
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
		if (customGoalProcess != null) customGoalProcess.onLostControl();
		if (elytraProcess != null && elytraProcess.isActive()) elytraProcess.onLostControl();
		if (baritone != null) {
			baritone.getInputOverrideHandler().clearAllKeys();
			if (Minecraft.getInstance().gameMode != null) {
				baritone.getPlayerContext().playerController().resetBlockRemoving();
			}
		}
	}

	private void transition(AutomationState next, String nextDetail) {
		if (next == AutomationState.ERROR) {
			restoreAllowPlace();
			if (repairStage != null) {
				restoreRepairNavigationSettings();
				restoreRepairRestrictions();
			}
		}
		state = next;
		detail = nextDetail;
		changedAt = Instant.now();
	}

	private void synchronize(AutomationState next, String nextDetail) {
		if (state != next) changedAt = Instant.now();
		state = next;
		detail = nextDetail;
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
		if (config.perimeterPortalOverworld == null) invalid.add("perimeter_portal_overworld");
		if (config.perimeterPortalNether == null) invalid.add("perimeter_portal_nether");
		if (config.repairPortalOverworld == null) invalid.add("repair_portal_overworld");
		if (config.repairPortalNether == null) invalid.add("repair_portal_nether");
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

	private static int durabilityThreshold(ItemStack stack) {
		return durabilityThreshold(stack.getItem());
	}

	private static int durabilityThreshold(Item item) {
		return item == Items.ELYTRA ? ELYTRA_DURABILITY_THRESHOLD : TOOL_DURABILITY_THRESHOLD;
	}

	private static int menuSlot(int inventorySlot) {
		return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
	}

	private static String progress(AreaMiningStatus status) {
		String remaining = status.knownRemainingBlocks() < 0L ? "scanning" : Long.toString(status.knownRemainingBlocks());
		return "Mining progress: cycle=" + status.minedBlocks() + "/" + status.blockLimit() + ", remaining=" + remaining + ", total=" + status.estimatedTotalBlocks();
	}

	private record StackSlot(ItemStack stack, int inventorySlot, int menuSlot, boolean offhand, boolean chest) {
	}

	private record Replacement(StackSlot low, StackSlot healthy) {
	}

	private record ResourceCheck(boolean repairRequired, String repairItem, Replacement replacement) {
	}

	private record InventoryClick(int slot, int button, ContainerInput type) {
	}

	private record NamedUnloadingPoint(String name, UnloadingPointConfig point) {
	}

	private record UnloadCandidate(BlockPos position, int horizontalDistanceSquared, int yDifference, double playerDistanceSquared) {
	}

	private record DurabilitySupplyPlan(Map<Item, Integer> targetHealthyCounts, Item chestItem, Item offhandItem, Item selectedItem, int selectedSlot) {
	}

	private record RepairPlan(Map<Item, Integer> targetCounts, Map<Item, Integer> baselineFullCounts) {
	}

	private record ReturnRouteCandidate(ReturnWaypoint waypoint, double cost) {
	}

	private enum SupplyKind {
		CONSUMABLES("consumables"),
		DURABILITY("durability");

		private final String displayName;

		SupplyKind(String displayName) {
			this.displayName = displayName;
		}
	}

	private enum SupplyPhase {
		PREPARING,
		OPENING,
		TRANSFERRING,
		FINALIZING
	}

	private enum ReturnAction {
		NONE,
		AFTER_UNLOAD,
		AFTER_SUPPLY,
		AFTER_REPAIR
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

	private enum RepairStage {
		OUTBOUND_PERIMETER_PORTAL,
		OUTBOUND_REPAIR_PORTAL,
		REPAIR_MACHINE,
		RETURN_TO_MACHINE_TAKEOFF,
		RETURN_REPAIR_PORTAL,
		RETURN_PERIMETER_PORTAL,
		RETURN_TO_MINE
	}

	private enum RepairFurnacePhase {
		PREPARING,
		OPENING,
		TAKING_OUTPUT,
		WAITING_FOR_REPAIR
	}
}
