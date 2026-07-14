package hackerrouter.perimeterdigger.client.config;

public final class ScanlineConfig {
	public int z;
	public int minX;
	public int maxX;

	public ScanlineConfig() {
	}

	public ScanlineConfig(int z, int minX, int maxX) {
		this.z = z;
		this.minX = minX;
		this.maxX = maxX;
	}
}
