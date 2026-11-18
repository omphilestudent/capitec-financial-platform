package za.co.capitec.caseservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to case.updates when a case's status or assignment changes.
 * changeType is STATUS_CHANGED or ASSIGNED.
 */
public record CaseUpdatedEvent(
        UUID eventId,
        UUID caseId,
        String caseNumber,
        String changeType,
        String oldStatus,
        String newStatus,
        String assignedInvestigator,
        Instant occurredAt
) {
    public static final String TOPIC = "case.updates";
    public static final String EVENT_TYPE = "CaseUpdated";
}
