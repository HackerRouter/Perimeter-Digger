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

import hackerrouter.perimeterdigger.client.config.DetectedAreaConfig;
import hackerrouter.perimeterdigger.client.config.ScanlineConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredColumnarAreaTest {
	@Test
	void mergesAdjacentAndOverlappingIntervals() {
		DetectedAreaConfig config = new DetectedAreaConfig();
		config.scanlines = List.of(
				new ScanlineConfig(5, 1, 3),
				new ScanlineConfig(5, 4, 6),
				new ScanlineConfig(5, 6, 8),
				new ScanlineConfig(7, -2, -1)
		);
		ConfiguredColumnarArea area = new ConfiguredColumnarArea(config, -10, 9);
		assertEquals(-2, area.minX());
		assertEquals(8, area.maxX());
		assertEquals(5, area.minZ());
		assertEquals(7, area.maxZ());
		assertTrue(area.containsXZ(7, 5));
		assertTrue(area.containsXZ(-2, 7));
		assertFalse(area.containsXZ(0, 6));
		assertEquals(200, area.estimatedBlockCount());
	}

	@Test
	void rejectsEmptyInvalidAndInvertedAreas() {
		DetectedAreaConfig empty = new DetectedAreaConfig();
		assertThrows(IllegalArgumentException.class, () -> new ConfiguredColumnarArea(empty, 0, 1));
		DetectedAreaConfig invalid = new DetectedAreaConfig();
		invalid.scanlines = List.of(new ScanlineConfig(0, 2, 1));
		assertThrows(IllegalArgumentException.class, () -> new ConfiguredColumnarArea(invalid, 0, 1));
		DetectedAreaConfig valid = new DetectedAreaConfig();
		valid.scanlines = List.of(new ScanlineConfig(0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new ConfiguredColumnarArea(valid, 2, 1));
	}
}
