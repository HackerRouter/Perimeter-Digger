package hackerrouter.perimeterdigger.client.config;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PerimeterConfig {
	public int schemaVersion = ConfigMigration.CURRENT_SCHEMA_VERSION;
	public Integer diggingMinY;
	public Integer diggingMaxY;
	public PositionConfig consumableSupplyPoint;
	public PositionConfig durabilitySupplyPoint;
	public PositionConfig bedPoint;
	public PositionConfig perimeterPortalOverworld;
	public PositionConfig perimeterPortalNether;
	public PositionConfig repairPortalOverworld;
	public PositionConfig repairPortalNether;
	public PositionConfig furnaceRowStart;
	public PositionConfig furnaceRowEnd;
	public Map<String, UnloadingPointConfig> unloadingPoints = new LinkedHashMap<>();
	public String liquidPolicy = "seal_boundary";
	public String durabilityRecoveryMode = "repair_portal";
	public List<String> sealingBlocks = new ArrayList<>(List.of("minecraft:netherrack"));
	public List<String> foods = new ArrayList<>(List.of("minecraft:enchanted_golden_apple"));
	public List<String> unloadingWhitelist = new ArrayList<>();
	public DetectedAreaConfig detectedArea;
	public FunctionConfig functions = new FunctionConfig();
	public AdvancedConfig advanced = new AdvancedConfig();

	public void normalize() {
		schemaVersion = ConfigMigration.CURRENT_SCHEMA_VERSION;
		if (unloadingPoints == null) {
			unloadingPoints = new LinkedHashMap<>();
		}
		if (detectedArea != null && detectedArea.scanlines == null) {
			detectedArea.scanlines = new java.util.ArrayList<>();
		}
		if (liquidPolicy == null) {
			liquidPolicy = "seal_boundary";
		}
		if (durabilityRecoveryMode == null) {
			durabilityRecoveryMode = "repair_portal";
		}
		if (sealingBlocks == null) {
			sealingBlocks = new ArrayList<>(List.of("minecraft:netherrack"));
		}
		if (foods == null) {
			foods = new ArrayList<>(List.of("minecraft:enchanted_golden_apple"));
		}
		if (unloadingWhitelist == null) {
			unloadingWhitelist = new ArrayList<>();
		}
		if (functions == null) {
			functions = new FunctionConfig();
		}
		if (advanced == null) {
			advanced = new AdvancedConfig();
		}
	}
}
