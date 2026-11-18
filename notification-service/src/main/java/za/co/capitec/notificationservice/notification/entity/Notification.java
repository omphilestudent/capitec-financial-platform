package za.co.capitec.notificationservice.notification.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_event_id", nullable = false, unique = true, updatable = false)
    private UUID sourceEventId;

    @Column(name = "notification_type", nullable = false, updatable = false, length = 40)
    private String notificationType;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "fraud_id")
    private UUID fraudId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(UUID sourceEventId, String notificationType, String severity, String title,
                        String message, UUID caseId, UUID fraudId, UUID customerId) {
        this.id = UUID.randomUUID();
        this.sourceEventId = sourceEventId;
        this.notificationType = notificationType;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.caseId = caseId;
        this.fraudId = fraudId;
        this.customerId = customerId;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        if (readAt == null) readAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getNotificationType() { return notificationType; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getCaseId() { return caseId; }
    public UUID getFraudId() { return fraudId; }
    public UUID getCustomerId() { return customerId; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
