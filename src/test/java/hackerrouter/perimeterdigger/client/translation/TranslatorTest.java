package hackerrouter.perimeterdigger.client.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranslatorTest {
	@Test
	void derivesStableScopedKeys() {
		Translator translator = new Translator("perimeterdigger").derive("command").derive("status");
		assertEquals("perimeterdigger.command.status.title", translator.key("title"));
	}

	@Test
	void rejectsInvalidPaths() {
		assertThrows(IllegalArgumentException.class, () -> new Translator(""));
		assertThrows(IllegalArgumentException.class, () -> new Translator(".invalid"));
		assertThrows(IllegalArgumentException.class, () -> new Translator("invalid."));
		assertThrows(IllegalArgumentException.class, () -> new Translator("invalid..path"));
	}
}
