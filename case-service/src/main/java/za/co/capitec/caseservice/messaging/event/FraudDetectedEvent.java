package za.co.capitec.caseservice.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/** The fraud.events payload published by fraud-detection-service. */
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
}
