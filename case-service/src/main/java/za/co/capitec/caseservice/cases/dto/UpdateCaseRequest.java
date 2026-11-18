package za.co.capitec.caseservice.cases.dto;

import jakarta.validation.constraints.Size;
import za.co.capitec.caseservice.cases.entity.CasePriority;
import za.co.capitec.caseservice.cases.entity.CaseStatus;

/** Partial update. Null fields are left unchanged. */
public record UpdateCaseRequest(
        CaseStatus status,

        CasePriority priority,

        @Size(max = 100, message = "Investigator cannot exceed 100 characters")
        String assignedInvestigator,

        @Size(max = 100, message = "Owner cannot exceed 100 characters")
        String owner,

        @Size(max = 5000, message = "Description cannot exceed 5000 characters")
        String description,

        @Size(max = 5000, message = "Resolution details cannot exceed 5000 characters")
        String resolutionDetails
) {}
