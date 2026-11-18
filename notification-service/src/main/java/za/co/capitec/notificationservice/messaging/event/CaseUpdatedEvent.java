package za.co.capitec.notificationservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

/** case.updates payload: STATUS_CHANGED or ASSIGNED. */
public record CaseUpdatedEvent(
        UUID eventId, UUID caseId, String caseNumber, String changeType, String oldStatus, String newStatus,
        String assignedInvestigator, Instant occurredAt) {
    public static final String TOPIC = "case.updates";
}
