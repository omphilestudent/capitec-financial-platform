package za.co.capitec.frauddetection.detection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pending_case_links")
public class PendingCaseLink {

    @Id
    @Column(name = "fraud_id", nullable = false, updatable = false)
    private UUID fraudId;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    protected PendingCaseLink() {
    }

    public PendingCaseLink(UUID fraudId, UUID caseId) {
        this.fraudId = fraudId;
        this.caseId = caseId;
        this.receivedAt = LocalDateTime.now();
    }

    public UUID getFraudId() { return fraudId; }
    public UUID getCaseId() { return caseId; }
}
