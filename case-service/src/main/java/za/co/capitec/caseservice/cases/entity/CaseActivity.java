package za.co.capitec.caseservice.cases.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** One entry in a case's history. Rows are only ever inserted. */
@Entity
@Table(name = "case_activity")
public class CaseActivity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "activity_type", nullable = false, length = 40)
    private String activityType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CaseActivity() {
    }

    public CaseActivity(UUID caseId, String activityType, String description, String actor) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.activityType = activityType;
        this.description = description;
        this.actor = actor;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public String getActivityType() { return activityType; }
    public String getDescription() { return description; }
    public String getActor() { return actor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
