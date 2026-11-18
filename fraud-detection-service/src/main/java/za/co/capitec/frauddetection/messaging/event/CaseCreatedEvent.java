package za.co.capitec.frauddetection.messaging.event;

import java.util.UUID;

/** case.events payload published by case-service when a case is opened. */
public record CaseCreatedEvent(
        UUID eventId,
        UUID caseId,
        String caseNumber,
        String subject,
        String priority,
        String origin,
        UUID customerId,
        UUID accountId,
        UUID sourceFraudId,
        java.time.Instant occurredAt
) {
    public static final String TOPIC = "case.events";
}
