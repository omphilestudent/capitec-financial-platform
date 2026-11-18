package za.co.capitec.frauddetection.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record FraudEvaluatedEvent(
        UUID eventId,
        UUID transactionId,
        UUID accountId,
        String outcome,
        LocalDateTime evaluatedAt
) {
    public static final String TOPIC = "fraud.evaluations";
    public static final String EVENT_TYPE = "FraudEvaluated";
}
