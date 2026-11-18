package za.co.capitec.statementservice.statement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "statement_access_logs")
public class StatementAccessLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "statement_id", updatable = false)
    private UUID statementId;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "actor_subject", length = 100, updatable = false)
    private String actorSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, updatable = false, length = 30)
    private AccessType accessType;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private LocalDateTime accessedAt;

    @Column(nullable = false, updatable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 255, updatable = false)
    private String failureReason;

    protected StatementAccessLog() {
    }

    public StatementAccessLog(UUID id, UUID statementId, UUID customerId, String actorSubject, AccessType accessType,
                              LocalDateTime accessedAt, boolean success, String failureReason) {
        this.id = id;
        this.statementId = statementId;
        this.customerId = customerId;
        this.actorSubject = actorSubject;
        this.accessType = accessType;
        this.accessedAt = accessedAt;
        this.success = success;
        this.failureReason = failureReason;
    }

    public UUID getId() { return id; }
    public UUID getStatementId() { return statementId; }
    public UUID getCustomerId() { return customerId; }
    public String getActorSubject() { return actorSubject; }
    public AccessType getAccessType() { return accessType; }
    public LocalDateTime getAccessedAt() { return accessedAt; }
    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
}
