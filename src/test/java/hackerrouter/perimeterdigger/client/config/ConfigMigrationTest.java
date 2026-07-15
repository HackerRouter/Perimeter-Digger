package hackerrouter.perimeterdigger.client.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTest {
	@Test
	void stampsUnversionedConfigurationWithoutChangingValues() {
		JsonObject source = new JsonObject();
		source.addProperty("liquidPolicy", "seal_boundary");
		ConfigMigration.Result result = ConfigMigration.migrate(source);
		assertTrue(result.changed());
		assertEquals(ConfigMigration.CURRENT_SCHEMA_VERSION, result.config().get("schemaVersion").getAsInt());
		assertEquals("seal_boundary", result.config().get("liquidPolicy").getAsString());
		assertFalse(source.has("schemaVersion"));
	}

	@Test
	void leavesCurrentConfigurationUnchanged() {
		JsonObject source = new JsonObject();
		source.addProperty("schemaVersion", ConfigMigration.CURRENT_SCHEMA_VERSION);
		ConfigMigration.Result result = ConfigMigration.migrate(source);
		assertFalse(result.changed());
	}

	@Test
	void rejectsFutureAndInvalidVersions() {
		JsonObject future = new JsonObject();
		future.addProperty("schemaVersion", ConfigMigration.CURRENT_SCHEMA_VERSION + 1);
		assertThrows(IllegalStateException.class, () -> ConfigMigration.migrate(future));
		JsonObject fractional = new JsonObject();
		fractional.addProperty("schemaVersion", 0.5);
		assertThrows(IllegalStateException.class, () -> ConfigMigration.migrate(fractional));
		JsonObject negative = new JsonObject();
		negative.addProperty("schemaVersion", -1);
		assertThrows(IllegalStateException.class, () -> ConfigMigration.migrate(negative));
	}
}
