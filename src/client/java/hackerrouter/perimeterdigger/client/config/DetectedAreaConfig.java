package hackerrouter.perimeterdigger.client.config;

import java.util.ArrayList;
import java.util.List;

public final class DetectedAreaConfig {
	public String boundaryBlock;
	public int boundaryY;
	public long columnCount;
	public int minX;
	public int maxX;
	public int minZ;
	public int maxZ;
	public List<ScanlineConfig> scanlines = new ArrayList<>();
}
