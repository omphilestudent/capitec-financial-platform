package za.co.capitec.frauddetection.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/** Published to fraud.events for every fraud detection. The key is the fraud ID. */
public record FraudDetectedEvent(
        UUID eventId,
        UUID fraudId,
        String fraudReference,
        UUID customerId,
        UUID accountId,
        UUID transactionId,
        int riskScore,
        String riskLevel,
        String detectionRule,
        String description,
        LocalDateTime detectedAt,
        boolean requiresInvestigation
) {
    public static final String TOPIC = "fraud.events";
    public static final String EVENT_TYPE = "FraudDetected";
}
