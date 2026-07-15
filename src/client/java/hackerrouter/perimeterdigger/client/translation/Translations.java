package hackerrouter.perimeterdigger.client.translation;

import hackerrouter.perimeterdigger.client.state.AutomationState;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class Translations {
	public static final Translator ROOT = new Translator("perimeterdigger");
	public static final Translator COMMAND = ROOT.derive("command");
	public static final Translator STATE = ROOT.derive("state");
	public static final Translator DETAIL = ROOT.derive("detail");
	public static final Translator VALUE = ROOT.derive("value");

	private Translations() {
	}

	public static MutableComponent state(AutomationState state) {
		return STATE.tr(state.name().toLowerCase(Locale.ROOT));
	}
}
