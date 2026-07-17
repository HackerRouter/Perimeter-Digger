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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;

public final class ConfigMigration {
	public static final int CURRENT_SCHEMA_VERSION = 1;

	private ConfigMigration() {
	}

	public static Result migrate(JsonObject source) {
		JsonObject migrated = source.deepCopy();
		int version = schemaVersion(migrated);
		if (version > CURRENT_SCHEMA_VERSION) {
			throw new IllegalStateException("Configuration schema " + version + " is newer than supported schema " + CURRENT_SCHEMA_VERSION + ".");
		}
		boolean changed = false;
		while (version < CURRENT_SCHEMA_VERSION) {
			version = migrateOne(migrated, version);
			changed = true;
		}
		if (!migrated.has("schemaVersion") || migrated.get("schemaVersion").getAsInt() != CURRENT_SCHEMA_VERSION) {
			migrated.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
			changed = true;
		}
		return new Result(migrated, changed);
	}

	private static int schemaVersion(JsonObject source) {
		JsonElement element = source.get("schemaVersion");
		if (element == null || element.isJsonNull()) return 0;
		try {
			int value = new BigDecimal(element.getAsString()).intValueExact();
			if (value < 0) throw new IllegalStateException("Configuration schema version cannot be negative.");
			return value;
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Configuration schema version must be a non-negative integer.", exception);
		}
	}

	private static int migrateOne(JsonObject config, int version) {
		if (version == 0) {
			config.addProperty("schemaVersion", 1);
			return 1;
		}
		throw new IllegalStateException("No migration is available from configuration schema " + version + ".");
	}

	public record Result(JsonObject config, boolean changed) {
	}
}
