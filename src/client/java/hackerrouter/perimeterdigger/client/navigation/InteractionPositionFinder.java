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

package hackerrouter.perimeterdigger.client.navigation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
		Vec3 eye = player.getEyePosition();
		return eye.distanceToSqr(Vec3.atCenterOf(target)) <= interactionDistanceSquared
				&& canSee(player, eye, target);
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
		Player player = Minecraft.getInstance().player;
		return player != null && isSafelyWithinReach(position, target, interactionDistanceSquared) && canSee(player, eye, target);
	}

	private static boolean canSee(Player player, Vec3 eye, BlockPos target) {
		HitResult hit = player.level().clip(new ClipContext(eye, Vec3.atCenterOf(target),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && ((BlockHitResult) hit).getBlockPos().equals(target);
	}

	static boolean isSafelyWithinReach(BlockPos position, BlockPos target, double distanceSquared) {
		double dx = Math.abs(position.getX() - target.getX()) + 0.5;
		double dy = position.getY() + 1.62 - (target.getY() + 0.5);
		double dz = Math.abs(position.getZ() - target.getZ()) + 0.5;
		return dx * dx + dy * dy + dz * dz <= distanceSquared;
	}
}
