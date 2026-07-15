package hackerrouter.perimeterdigger.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class WorldConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Minecraft minecraft = Minecraft.getInstance();
	private String identity;
	private Path path;
	private PerimeterConfig config;

	public PerimeterConfig get() {
		ensureLoaded();
		return config;
	}

	public String identity() {
		ensureLoaded();
		return identity;
	}

	public void reload() {
		identity = null;
		path = null;
		config = null;
		ensureLoaded();
	}

	public void save() {
		ensureLoaded();
		writeConfig();
	}

	private void writeConfig() {
		try {
			Files.createDirectories(path.getParent());
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to save world configuration: " + exception.getMessage(), exception);
		}
	}

	public void ensureLoaded() {
		String currentIdentity = currentIdentity();
		if (currentIdentity.equals(identity) && config != null) {
			return;
		}
		identity = currentIdentity;
		String fileName = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)) + ".json";
		path = FabricLoader.getInstance().getConfigDir().resolve("perimeter-digger").resolve("worlds").resolve(fileName);
		if (!Files.exists(path)) {
			config = new PerimeterConfig();
			return;
		}
		try {
			JsonObject source = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			ConfigMigration.Result migration = ConfigMigration.migrate(source);
			config = GSON.fromJson(migration.config(), PerimeterConfig.class);
			if (config == null) {
				config = new PerimeterConfig();
			}
			config.normalize();
			if (migration.changed()) writeConfig();
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to load world configuration: " + exception.getMessage(), exception);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Failed to parse or migrate world configuration: " + exception.getMessage(), exception);
		}
	}

	private String currentIdentity() {
		if (minecraft.level == null) {
			throw new IllegalStateException("No world is currently loaded.");
		}
		ServerData server = minecraft.getCurrentServer();
		if (server != null) {
			return "server:" + server.ip;
		}
		if (minecraft.getSingleplayerServer() != null) {
			return "singleplayer:" + minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		}
		return "local:" + minecraft.level.dimension().identifier();
	}
}
