package hackerrouter.perimeterdigger.client.translation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TranslationConsistencyTest {
	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:([1-9][0-9]*)\\$)?s");

	@Test
	void maintainedLanguagesHaveMatchingOrderedKeysAndPlaceholders() {
		Map<String, String> english = load("en_us");
		Map<String, String> chinese = load("zh_cn");
		assertEquals(new ArrayList<>(english.keySet()), new ArrayList<>(chinese.keySet()));
		for (String key : english.keySet()) {
			assertFalse(english.get(key).isBlank(), key);
			assertFalse(chinese.get(key).isBlank(), key);
			assertEquals(placeholders(english.get(key)), placeholders(chinese.get(key)), key);
		}
	}

	private static Map<String, String> load(String language) {
		String path = "assets/perimeter-digger/lang/" + language + ".json";
		var stream = TranslationConsistencyTest.class.getClassLoader().getResourceAsStream(path);
		assertNotNull(stream, path);
		return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, String>>() {}.getType());
	}

	private static List<Integer> placeholders(String value) {
		List<Integer> result = new ArrayList<>();
		Matcher matcher = PLACEHOLDER.matcher(value);
		int implicit = 1;
		while (matcher.find()) result.add(matcher.group(1) == null ? implicit++ : Integer.parseInt(matcher.group(1)));
		result.sort(Integer::compareTo);
		return result;
	}
}
