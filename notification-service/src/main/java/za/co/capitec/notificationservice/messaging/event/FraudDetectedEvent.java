package za.co.capitec.notificationservice.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/** fraud.events payload. */
public record FraudDetectedEvent(
        UUID eventId, UUID fraudId, String fraudReference, UUID customerId, UUID accountId, UUID transactionId,
        int riskScore, String riskLevel, String detectionRule, String description, LocalDateTime detectedAt,
        boolean requiresInvestigation) {
    public static final String TOPIC = "fraud.events";
}
