package za.co.capitec.caseservice.cases.dto;

import za.co.capitec.caseservice.cases.entity.CaseRecord;

import java.time.LocalDateTime;
import java.util.UUID;

public record CaseResponse(
        UUID id,
        String caseNumber,
        String subject,
        String description,
        String status,
        String priority,
        String origin,
        String caseType,
        UUID customerId,
        UUID accountId,
        UUID transactionId,
        UUID sourceFraudId,
        String assignedInvestigator,
        String owner,
        String resolutionDetails,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt
) {
    public static CaseResponse from(CaseRecord c) {
        return new CaseResponse(
                c.getId(), c.getCaseNumber(), c.getSubject(), c.getDescription(),
                c.getStatus().name(), c.getPriority().name(), c.getOrigin().name(), c.getCaseType().name(),
                c.getCustomerId(), c.getAccountId(), c.getTransactionId(), c.getSourceFraudId(),
                c.getAssignedInvestigator(), c.getOwner(), c.getResolutionDetails(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getClosedAt()
        );
    }
}
