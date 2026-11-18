package za.co.capitec.caseservice.cases.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.capitec.caseservice.cases.entity.CasePriority;
import za.co.capitec.caseservice.cases.entity.CaseType;

import java.util.UUID;

/** A case opened by a user for a real customer, and optionally a real account or transaction. */
public record CreateCaseRequest(
        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject cannot exceed 200 characters")
        String subject,

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        String description,

        @NotNull(message = "Priority is required")
        CasePriority priority,

        @NotNull(message = "Case type is required")
        CaseType caseType,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        UUID accountId,

        UUID transactionId,

        @Size(max = 100, message = "Owner cannot exceed 100 characters")
        String owner
) {}
