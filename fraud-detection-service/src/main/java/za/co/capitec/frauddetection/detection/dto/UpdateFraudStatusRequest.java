package za.co.capitec.frauddetection.detection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.capitec.frauddetection.detection.entity.InvestigationStatus;

public record UpdateFraudStatusRequest(
        @NotNull(message = "Investigation status is required")
        InvestigationStatus investigationStatus
) {}
