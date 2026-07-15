package hackerrouter.perimeterdigger.client.navigation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class InteractionPositionFinder {
	private final int searchRadius;
	private final double interactionDistanceSquared;

	public InteractionPositionFinder(int searchRadius, double interactionDistanceSquared) {
		this.searchRadius = searchRadius;
		this.interactionDistanceSquared = interactionDistanceSquared;
	}

	public List<BlockPos> find(ClientLevel world, BlockPos target) {
		if (world == null) return List.of();
		List<BlockPos> positions = new ArrayList<>();
		for (int dx = -searchRadius; dx <= searchRadius; dx++) {
			for (int dz = -searchRadius; dz <= searchRadius; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockPos position = target.offset(dx, 0, dz);
				if (world.getBlockState(position).getBlock() instanceof SlabBlock) position = position.above();
				if (isValid(world, position, target)) positions.add(position);
			}
		}
		return List.copyOf(positions);
	}

	public Optional<BlockPos> closest(ClientLevel world, BlockPos target, BlockPos origin) {
		return find(world, target).stream().min(Comparator.comparingDouble(origin::distSqr));
	}

	public boolean isAtValidPosition(ClientLevel world, BlockPos target, BlockPos playerFeet) {
		return find(world, target).contains(playerFeet);
	}

	public boolean canReach(Player player, BlockPos target) {
		return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= interactionDistanceSquared;
	}

	private boolean isValid(ClientLevel world, BlockPos position, BlockPos target) {
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
		return eye.distanceToSqr(Vec3.atCenterOf(target)) <= interactionDistanceSquared;
	}
}
