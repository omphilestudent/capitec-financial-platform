package za.co.capitec.caseservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** Published to case.events when a case is opened. The message key is the case ID. */
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
        Instant occurredAt
) {
    public static final String TOPIC = "case.events";
    public static final String EVENT_TYPE = "CaseCreated";
}
