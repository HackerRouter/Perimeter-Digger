package hackerrouter.perimeterdigger.client.config;

public final class UnloadingPointConfig {
	public int x;
	public int minY;
	public int z;

	public UnloadingPointConfig() {
	}

	public UnloadingPointConfig(int x, int minY, int z) {
		this.x = x;
		this.minY = minY;
		this.z = z;
	}

	@Override
	public String toString() {
		return x + " " + minY + " " + z;
	}
}
