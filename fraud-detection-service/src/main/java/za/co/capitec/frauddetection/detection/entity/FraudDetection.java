package za.co.capitec.frauddetection.detection.entity;

import jakarta.persistence.*;
import za.co.capitec.frauddetection.detection.rules.RiskLevel;

import java.time.LocalDateTime;
import java.util.UUID;

/** A fraud detection on one transaction. The case it belongs to is set once the case is opened. */
@Entity
@Table(name = "fraud_detections")
public class FraudDetection {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "fraud_reference", nullable = false, unique = true, updatable = false, length = 20)
    private String fraudReference;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private UUID transactionId;

    @Column(name = "fraud_type", nullable = false, length = 40)
    private String fraudType;

    @Column(name = "fraud_category", nullable = false, length = 40)
    private String fraudCategory;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel;

    @Column(name = "detection_rule", nullable = false, length = 40)
    private String detectionRule;

    @Column(name = "detection_description", nullable = false, columnDefinition = "TEXT")
    private String detectionDescription;

    @Column(name = "detection_source", nullable = false, length = 40)
    private String detectionSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "investigation_status", nullable = false, length = 20)
    private InvestigationStatus investigationStatus;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Version
    private long version;

    protected FraudDetection() {
    }

    public FraudDetection(UUID id, String fraudReference, UUID customerId, UUID accountId, UUID transactionId,
                          String fraudType, String fraudCategory, int riskScore, RiskLevel riskLevel,
                          String detectionRule, String detectionDescription, String detectionSource) {
        this.id = id;
        this.fraudReference = fraudReference;
        this.customerId = customerId;
        this.accountId = accountId;
        this.transactionId = transactionId;
        this.fraudType = fraudType;
        this.fraudCategory = fraudCategory;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.detectionRule = detectionRule;
        this.detectionDescription = detectionDescription;
        this.detectionSource = detectionSource;
        this.investigationStatus = InvestigationStatus.OPEN;
        this.detectedAt = LocalDateTime.now();
        this.lastUpdated = this.detectedAt;
    }

    public void linkToCase(UUID caseId) {
        this.caseId = caseId;
        this.lastUpdated = LocalDateTime.now();
    }

    public void changeStatus(InvestigationStatus next) {
        this.investigationStatus = next;
        this.lastUpdated = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getFraudReference() { return fraudReference; }
    public UUID getCaseId() { return caseId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getAccountId() { return accountId; }
    public UUID getTransactionId() { return transactionId; }
    public String getFraudType() { return fraudType; }
    public String getFraudCategory() { return fraudCategory; }
    public int getRiskScore() { return riskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getDetectionRule() { return detectionRule; }
    public String getDetectionDescription() { return detectionDescription; }
    public String getDetectionSource() { return detectionSource; }
    public InvestigationStatus getInvestigationStatus() { return investigationStatus; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
}
