package za.co.capitec.notificationservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** case.events payload (CaseCreated only). */
public record CaseCreatedEvent(
        UUID eventId, UUID caseId, String caseNumber, String subject, String priority, String origin,
        UUID customerId, UUID accountId, UUID sourceFraudId, Instant occurredAt) {
    public static final String TOPIC = "case.events";
}
