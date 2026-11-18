package za.co.capitec.caseservice.cases.dto;

import za.co.capitec.caseservice.cases.entity.CaseActivity;

import java.time.LocalDateTime;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        String activityType,
        String description,
        String actor,
        LocalDateTime createdAt
) {
    public static ActivityResponse from(CaseActivity a) {
        return new ActivityResponse(a.getId(), a.getActivityType(), a.getDescription(), a.getActor(), a.getCreatedAt());
    }
}
