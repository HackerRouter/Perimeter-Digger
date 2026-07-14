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
import hackerrouter.perimeterdigger.client.config.UnloadingPointConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AutomationController {
	private static final int MONITOR_INTERVAL_TICKS = 10;
	private static final int DURABILITY_THRESHOLD = 16;
	private static final int DROP_COLLECTION_RADIUS = 32;
	private static final int FOOD_LEVEL_THRESHOLD = 14;
	private static final int NO_DROP_SCAN_LIMIT = 3;
	private static final int UNLOAD_LANDING_SEARCH_RADIUS = 16;
	private static final int UNLOAD_WALK_DISTANCE = 32;
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
	private List<NamedUnloadingPoint> unloadingPoints = List.of();
	private NamedUnloadingPoint unloadingPoint;
	private List<UnloadCandidate> unloadCandidates = List.of();
	private int unloadCandidateIndex;
	private int unloadSettleTicks;
	private final ArrayDeque<InventoryClick> inventoryClicks = new ArrayDeque<>();
	private int tickCounter;
	private int noDropScans;
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
		this.unloadingPoints = config.unloadingPoints.entrySet().stream()
				.map(entry -> new NamedUnloadingPoint(entry.getKey(), entry.getValue()))
				.toList();
		this.miningCompletePending = false;
		this.debugUnloadOnly = false;
		this.noDropScans = 0;
		this.inventoryClicks.clear();
		BaritoneAPI.getSettings().itemSaver.value = true;
		BaritoneAPI.getSettings().itemSaverThreshold.value = DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoSwap.value = true;
		BaritoneAPI.getSettings().elytraMinimumDurability.value = DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
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
		BaritoneAPI.getSettings().elytraMinimumDurability.value = DURABILITY_THRESHOLD;
		BaritoneAPI.getSettings().elytraAutoJump.value = true;
		requestUnload();
		return state == AutomationState.ERROR ? List.of("stage5_start") : List.of();
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
		if (++tickCounter % MONITOR_INTERVAL_TICKS != 0) return;
		if (state == AutomationState.NAVIGATING_TO_UNLOAD) {
			tickNavigateToUnload();
			return;
		}
		if (state == AutomationState.APPROACHING_UNLOAD) {
			tickApproachingUnload();
			return;
		}
		if (state == AutomationState.IDLE || state == AutomationState.ERROR || state == AutomationState.COMPLETE
				|| state == AutomationState.NAVIGATING_TO_REPAIR_PORTAL || state == AutomationState.NAVIGATING_TO_RESUPPLY
				|| state == AutomationState.NAVIGATING_TO_DURABILITY_SUPPLY) return;
		if (!inventoryClicks.isEmpty()) return;
		if ((state == AutomationState.MINING || state == AutomationState.COLLECTING_DROPS) && emptyInventorySlots() == 0) {
			requestUnload();
			return;
		}
		ResourceCheck resources = inspectResources();
		if (resources.repairRequired()) {
			stopProcesses();
			if (durabilityRecoveryMode.equals("supply_point")) {
				transition(AutomationState.NAVIGATING_TO_DURABILITY_SUPPLY, "Supply replacement required: no healthy replacement exists for " + resources.repairItem() + ".");
			} else {
				transition(AutomationState.NAVIGATING_TO_REPAIR_PORTAL, "Repair required: no healthy replacement exists for " + resources.repairItem() + ".");
			}
			return;
		}
		if (resources.replacement() != null) {
			performReplacement(resources.replacement());
			return;
		}
		if (baritone.getPlayerContext().player().getFoodData().getFoodLevel() <= FOOD_LEVEL_THRESHOLD) {
			if (!beginEating()) {
				stopProcesses();
				transition(AutomationState.NAVIGATING_TO_RESUPPLY, "Food is required but no configured food is available.");
			}
			return;
		}
		if (state == AutomationState.MINING || state == AutomationState.PAUSED) synchronizeMining();
		else if (state == AutomationState.COLLECTING_DROPS) tickDropCollection();
	}

	public void stop() {
		stopProcesses();
		baritone = null;
		miningProcess = null;
		customGoalProcess = null;
		elytraProcess = null;
		area = null;
		inventoryClicks.clear();
		debugUnloadOnly = false;
		transition(AutomationState.IDLE, "Stopped");
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
		return detail;
	}

	public Instant changedAt() {
		return changedAt;
	}

	public void resetForWorldChange() {
		stopProcesses();
		baritone = null;
		miningProcess = null;
		customGoalProcess = null;
		elytraProcess = null;
		area = null;
		inventoryClicks.clear();
		debugUnloadOnly = false;
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
		noDropScans = 0;
		transition(AutomationState.COLLECTING_DROPS, "Collecting drops within 32 blocks.");
		tickDropCollection();
	}

	private void tickDropCollection() {
		if (emptyInventorySlots() == 0) {
			requestUnload();
			return;
		}
		List<Goal> goals = nearbyDropGoals();
		if (goals.isEmpty()) {
			customGoalProcess.onLostControl();
			if (++noDropScans < NO_DROP_SCAN_LIMIT) {
				synchronize(AutomationState.COLLECTING_DROPS, "Waiting for nearby drops. Empty slots: " + emptyInventorySlots() + ".");
				return;
			}
			if (miningCompletePending) transition(AutomationState.COMPLETE, "Mining and drop collection complete.");
			else startMiningCycle();
			return;
		}
		noDropScans = 0;
		customGoalProcess.setGoalAndPath(new GoalComposite(goals.toArray(new Goal[0])));
		synchronize(AutomationState.COLLECTING_DROPS, "Collecting " + goals.size() + " nearby drop targets. Empty slots: " + emptyInventorySlots() + ".");
	}

	private List<Goal> nearbyDropGoals() {
		List<Goal> goals = new ArrayList<>();
		double radiusSquared = (double) DROP_COLLECTION_RADIUS * DROP_COLLECTION_RADIUS;
		for (Entity entity : baritone.getPlayerContext().entities()) {
			if (entity instanceof ItemEntity && entity.isAlive() && entity.onGround()
					&& entity.distanceToSqr(baritone.getPlayerContext().player()) <= radiusSquared) {
				goals.add(new GoalBlock(new BetterBlockPos(entity.position().x, entity.position().y + 0.1, entity.position().z)));
			}
		}
		return goals;
	}

	private void requestUnload() {
		stopProcesses();
		if (unloadingPoints.isEmpty()) {
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
		if (player.distSqr(flightTarget) <= (double) UNLOAD_WALK_DISTANCE * UNLOAD_WALK_DISTANCE || !elytraProcess.isLoaded()) {
			beginUnloadApproach();
			return;
		}
		elytraProcess.pathTo(flightTarget);
		transition(AutomationState.NAVIGATING_TO_UNLOAD, "Flying near unloading point " + unloadingPoint.name() + " at X=" + unloadingPoint.point().x + ", Z=" + unloadingPoint.point().z + ".");
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
		int currentY = baritone.getPlayerContext().playerFeet().y;
		unloadCandidates = findUnloadCandidates(unloadingPoint.point(), currentY);
		unloadCandidateIndex = 0;
		if (unloadCandidates.isEmpty()) {
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
			unloadSettleTicks = 5;
			transition(AutomationState.UNLOADING, "Facing unloading channel " + unloadingPoint.name() + ".");
			return;
		}
		if (customGoalProcess.isActive()) return;
		if (++unloadCandidateIndex >= unloadCandidates.size()) {
			transition(AutomationState.ERROR, "No reachable standing block was found near unloading point " + unloadingPoint.name() + ".");
			return;
		}
		pathToUnloadCandidate();
	}

	private void tickUnloading() {
		UnloadingPointConfig point = unloadingPoint.point();
		Vec3 target = new Vec3(point.x + 0.5, point.minY + 0.5, point.z + 0.5);
		baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(
				baritone.getPlayerContext().playerHead(), target, baritone.getPlayerContext().playerRotations()), false);
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
		if (debugUnloadOnly) {
			debugUnloadOnly = false;
			transition(AutomationState.COMPLETE, "Stage 5 debug unloading complete.");
		} else if (miningCompletePending) transition(AutomationState.COMPLETE, "Mining and unloading complete.");
		else startMiningCycle();
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
			if (remainingDurability(low.stack()) > DURABILITY_THRESHOLD) continue;
			StackSlot healthy = group.stream()
					.filter(candidate -> candidate != low && candidate.stack().getItem() == low.stack().getItem())
					.filter(candidate -> remainingDurability(candidate.stack()) > DURABILITY_THRESHOLD)
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
		int hotbarSlot = foodSlot < 9 ? foodSlot : 7;
		if (foodSlot >= 9) {
			baritone.getPlayerContext().playerController().windowClick(
					baritone.getPlayerContext().player().inventoryMenu.containerId,
					menuSlot(foodSlot), hotbarSlot, ContainerInput.SWAP,
					baritone.getPlayerContext().player());
		}
		inventory.setSelectedSlot(hotbarSlot);
		transition(AutomationState.EATING, "Eating configured food.");
		return true;
	}

	private void tickEating() {
		if (baritone.getPlayerContext().player().getFoodData().getFoodLevel() > FOOD_LEVEL_THRESHOLD) {
			baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
			if (stateBeforeEating == AutomationState.MINING && miningProcess != null) {
				miningProcess.resume();
				transition(AutomationState.MINING, "Eating complete. Mining resumed.");
			} else if (stateBeforeEating == AutomationState.COLLECTING_DROPS) {
				transition(AutomationState.COLLECTING_DROPS, "Eating complete. Drop collection resumed.");
			} else transition(stateBeforeEating, "Eating complete.");
			return;
		}
		baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
	}

	private void executeInventoryClick() {
		InventoryClick click = inventoryClicks.poll();
		if (click == null) return;
		baritone.getPlayerContext().playerController().windowClick(
				baritone.getPlayerContext().player().inventoryMenu.containerId,
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

	private void stopProcesses() {
		if (baritone != null) baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
		if (miningProcess != null) miningProcess.cancel();
		if (customGoalProcess != null) customGoalProcess.onLostControl();
		if (elytraProcess != null && elytraProcess.isActive()) elytraProcess.onLostControl();
	}

	private void transition(AutomationState next, String nextDetail) {
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
		if ("supply_point".equals(config.durabilityRecoveryMode) && config.supplyPoint == null) invalid.add("supply_point_for_durability_recovery");
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
}
