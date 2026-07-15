package hackerrouter.perimeterdigger.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
