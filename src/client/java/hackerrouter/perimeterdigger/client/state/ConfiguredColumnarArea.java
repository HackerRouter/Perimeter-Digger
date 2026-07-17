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

import baritone.api.process.area.IColumnarArea;
import hackerrouter.perimeterdigger.client.config.DetectedAreaConfig;
import hackerrouter.perimeterdigger.client.config.ScanlineConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ConfiguredColumnarArea implements IColumnarArea {
	private final int minX;
	private final int maxX;
	private final int minY;
	private final int maxY;
	private final int minZ;
	private final int maxZ;
	private final long columnCount;
	private final Map<Integer, int[]> intervalsByZ;

	public ConfiguredColumnarArea(DetectedAreaConfig config, int minY, int maxY) {
		if (config == null || config.scanlines == null || config.scanlines.isEmpty()) {
			throw new IllegalArgumentException("The mining area is empty.");
		}
		if (minY > maxY) {
			throw new IllegalArgumentException("The mining Y range is inverted.");
		}
		TreeMap<Integer, List<int[]>> grouped = new TreeMap<>();
		for (ScanlineConfig scanline : config.scanlines) {
			if (scanline == null || scanline.minX > scanline.maxX) {
				throw new IllegalArgumentException("The mining area contains an invalid scanline.");
			}
			grouped.computeIfAbsent(scanline.z, ignored -> new ArrayList<>()).add(new int[]{scanline.minX, scanline.maxX});
		}
		Map<Integer, int[]> mergedByZ = new HashMap<>();
		int actualMinX = Integer.MAX_VALUE;
		int actualMaxX = Integer.MIN_VALUE;
		long actualColumnCount = 0L;
		for (Map.Entry<Integer, List<int[]>> entry : grouped.entrySet()) {
			List<int[]> intervals = entry.getValue();
			intervals.sort((first, second) -> Integer.compare(first[0], second[0]));
			List<Integer> merged = new ArrayList<>();
			int start = intervals.get(0)[0];
			int end = intervals.get(0)[1];
			for (int index = 1; index < intervals.size(); index++) {
				int[] next = intervals.get(index);
				if ((long) next[0] <= (long) end + 1L) {
					end = Math.max(end, next[1]);
				} else {
					merged.add(start);
					merged.add(end);
					actualColumnCount = Math.addExact(actualColumnCount, (long) end - start + 1L);
					actualMinX = Math.min(actualMinX, start);
					actualMaxX = Math.max(actualMaxX, end);
					start = next[0];
					end = next[1];
				}
			}
			merged.add(start);
			merged.add(end);
			actualColumnCount = Math.addExact(actualColumnCount, (long) end - start + 1L);
			actualMinX = Math.min(actualMinX, start);
			actualMaxX = Math.max(actualMaxX, end);
			int[] values = new int[merged.size()];
			for (int index = 0; index < merged.size(); index++) values[index] = merged.get(index);
			mergedByZ.put(entry.getKey(), values);
		}
		this.minX = actualMinX;
		this.maxX = actualMaxX;
		this.minY = minY;
		this.maxY = maxY;
		this.minZ = grouped.firstKey();
		this.maxZ = grouped.lastKey();
		this.columnCount = actualColumnCount;
		this.intervalsByZ = Map.copyOf(mergedByZ);
	}

	@Override
	public int minX() {
		return minX;
	}

	@Override
	public int maxX() {
		return maxX;
	}

	@Override
	public int minY() {
		return minY;
	}

	@Override
	public int maxY() {
		return maxY;
	}

	@Override
	public int minZ() {
		return minZ;
	}

	@Override
	public int maxZ() {
		return maxZ;
	}

	@Override
	public boolean containsXZ(int x, int z) {
		int[] intervals = intervalsByZ.get(z);
		if (intervals == null) return false;
		int low = 0;
		int high = intervals.length / 2 - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			int start = intervals[middle * 2];
			int end = intervals[middle * 2 + 1];
			if (x < start) high = middle - 1;
			else if (x > end) low = middle + 1;
			else return true;
		}
		return false;
	}

	@Override
	public long estimatedBlockCount() {
		try {
			return Math.multiplyExact(columnCount, (long) maxY - minY + 1L);
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}
}
