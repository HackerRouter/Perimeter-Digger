package hackerrouter.perimeterdigger.client.state;

import java.time.Instant;

public record StateTransition(
		Instant timestamp,
		AutomationState previousState,
		AutomationState nextState,
		String detail,
		String dimension,
		String position
) {
}
