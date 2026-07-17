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

package hackerrouter.perimeterdigger.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerimeterConfigTest {
	@Test
	void normalizeRestoresRequiredContainersAndSchema() {
		PerimeterConfig config = new PerimeterConfig();
		config.schemaVersion = 0;
		config.unloadingPoints = null;
		config.sealingBlocks = null;
		config.foods = null;
		config.unloadingWhitelist = null;
		config.functions = null;
		config.advanced = null;
		config.normalize();
		assertEquals(ConfigMigration.CURRENT_SCHEMA_VERSION, config.schemaVersion);
		assertNotNull(config.unloadingPoints);
		assertNotNull(config.sealingBlocks);
		assertNotNull(config.foods);
		assertNotNull(config.unloadingWhitelist);
		assertNotNull(config.functions);
		assertNotNull(config.advanced);
	}

	@Test
	void crossDimensionRepairIsEnabledByDefault() {
		assertTrue(new FunctionConfig().crossDimensionRepair);
	}
}
