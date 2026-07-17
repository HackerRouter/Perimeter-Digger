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

package hackerrouter.perimeterdigger.client.state;

import hackerrouter.perimeterdigger.client.config.UnloadingPointConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class UnloadFlow {
	NamedPoint point;
	List<Candidate> candidates = List.of();
	Vec3 edgePosition;
	int candidateIndex;
	int flightAttempts;
	int settleTicks;
	boolean debugOnly;

	void reset() {
		point = null;
		candidates = List.of();
		edgePosition = null;
		candidateIndex = 0;
		flightAttempts = 0;
		settleTicks = 0;
		debugOnly = false;
	}

	record NamedPoint(String name, UnloadingPointConfig point) {
	}

	record Candidate(BlockPos position, int horizontalDistanceSquared, int yDifference, double playerDistanceSquared) {
	}
}
