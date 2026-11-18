package za.co.capitec.frauddetection.detection.dto;

import za.co.capitec.frauddetection.detection.entity.FraudDetection;

import java.time.LocalDateTime;
import java.util.UUID;

public record FraudResponse(
        UUID id,
        String fraudReference,
        UUID caseId,
        UUID customerId,
        UUID accountId,
        UUID transactionId,
        String fraudType,
        String fraudCategory,
        int riskScore,
        String riskLevel,
        String detectionRule,
        String detectionDescription,
        String detectionSource,
        String investigationStatus,
        LocalDateTime detectedAt,
        LocalDateTime lastUpdated
) {
    public static FraudResponse from(FraudDetection f) {
        return new FraudResponse(
                f.getId(), f.getFraudReference(), f.getCaseId(), f.getCustomerId(), f.getAccountId(),
                f.getTransactionId(), f.getFraudType(), f.getFraudCategory(), f.getRiskScore(),
                f.getRiskLevel().name(), f.getDetectionRule(), f.getDetectionDescription(),
                f.getDetectionSource(), f.getInvestigationStatus().name(), f.getDetectedAt(), f.getLastUpdated()
        );
    }
}
