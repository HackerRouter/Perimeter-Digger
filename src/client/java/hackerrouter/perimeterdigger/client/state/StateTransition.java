package hackerrouter.perimeterdigger.client.state;

import hackerrouter.perimeterdigger.client.translation.LocalizedMessage;
import java.time.Instant;

public record StateTransition(
		Instant timestamp,
		AutomationState previousState,
		AutomationState nextState,
		LocalizedMessage detail,
		String dimension,
		String position
) {
}
