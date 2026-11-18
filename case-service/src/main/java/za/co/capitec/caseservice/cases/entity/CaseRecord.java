package za.co.capitec.caseservice.cases.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class CaseRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "case_number", nullable = false, unique = true, updatable = false, length = 20)
    private String caseNumber;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CasePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private CaseOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, updatable = false, length = 40)
    private CaseType caseType;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "source_fraud_id", unique = true, updatable = false)
    private UUID sourceFraudId;

    @Column(name = "assigned_investigator", length = 100)
    private String assignedInvestigator;

    @Column(length = 100)
    private String owner;

    @Column(name = "resolution_details", columnDefinition = "TEXT")
    private String resolutionDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    private long version;

    protected CaseRecord() {
    }

    public CaseRecord(UUID id, String caseNumber, String subject, String description,
                      CasePriority priority, CaseOrigin origin, CaseType caseType,
                      UUID customerId, UUID accountId, UUID transactionId, UUID sourceFraudId,
                      String owner) {
        this.id = id;
        this.caseNumber = caseNumber;
        this.subject = subject;
        this.description = description;
        this.status = CaseStatus.NEW;
        this.priority = priority;
        this.origin = origin;
        this.caseType = caseType;
        this.customerId = customerId;
        this.accountId = accountId;
        this.transactionId = transactionId;
        this.sourceFraudId = sourceFraudId;
        this.owner = owner;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(CaseStatus next, String resolutionDetails) {
        this.status = next;
        if (resolutionDetails != null) this.resolutionDetails = resolutionDetails;
        if (next == CaseStatus.CLOSED) this.closedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void changePriority(CasePriority priority) {
        this.priority = priority;
        this.updatedAt = LocalDateTime.now();
    }

    public void assign(String investigator) {
        this.assignedInvestigator = investigator;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeOwner(String owner) {
        this.owner = owner;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getCaseNumber() { return caseNumber; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public CaseStatus getStatus() { return status; }
    public CasePriority getPriority() { return priority; }
    public CaseOrigin getOrigin() { return origin; }
    public CaseType getCaseType() { return caseType; }
    public UUID getCustomerId() { return customerId; }
    public UUID getAccountId() { return accountId; }
    public UUID getTransactionId() { return transactionId; }
    public UUID getSourceFraudId() { return sourceFraudId; }
    public String getAssignedInvestigator() { return assignedInvestigator; }
    public String getOwner() { return owner; }
    public String getResolutionDetails() { return resolutionDetails; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public long getVersion() { return version; }
}
