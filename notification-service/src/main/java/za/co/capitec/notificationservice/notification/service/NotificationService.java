package za.co.capitec.notificationservice.notification.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.notificationservice.common.exception.InvalidEventException;
import za.co.capitec.notificationservice.common.exception.NotificationNotFoundException;
import za.co.capitec.notificationservice.messaging.event.CaseCreatedEvent;
import za.co.capitec.notificationservice.messaging.event.CaseUpdatedEvent;
import za.co.capitec.notificationservice.messaging.event.FraudDetectedEvent;
import za.co.capitec.notificationservice.notification.entity.Notification;
import za.co.capitec.notificationservice.notification.repository.NotificationRepository;

import java.util.List;
import java.util.UUID;

/**
 * Creates notifications from real business events. Every notification is keyed by
 * its source event ID, so a redelivered event creates nothing new.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void onCaseCreated(CaseCreatedEvent e) {
        requireEventId(e.eventId());
        save(new Notification(e.eventId(), "CASE_CREATED",
                "CRITICAL".equals(e.priority()) || "HIGH".equals(e.priority()) ? "WARNING" : "INFO",
                "New case " + e.caseNumber(),
                e.subject(), e.caseId(), e.sourceFraudId(), e.customerId()));
    }

    public void onCaseUpdated(CaseUpdatedEvent e) {
        requireEventId(e.eventId());
        if ("ASSIGNED".equals(e.changeType())) {
            save(new Notification(e.eventId(), "CASE_ASSIGNED", "INFO",
                    "Case " + e.caseNumber() + " assigned",
                    "Assigned to " + e.assignedInvestigator(), e.caseId(), null, null));
        } else if ("STATUS_CHANGED".equals(e.changeType())) {
            boolean escalated = "ESCALATED".equals(e.newStatus());
            save(new Notification(e.eventId(), escalated ? "CASE_ESCALATED" : "CASE_STATUS_CHANGED",
                    escalated ? "CRITICAL" : "INFO",
                    "Case " + e.caseNumber() + (escalated ? " escalated" : " status changed"),
                    "Status changed from " + e.oldStatus() + " to " + e.newStatus(),
                    e.caseId(), null, null));
        }
    }

    public void onFraudDetected(FraudDetectedEvent e) {
        requireEventId(e.eventId());
        if (!e.requiresInvestigation()) {
            return; // only high-risk detections notify
        }
        save(new Notification(e.eventId(), "FRAUD_HIGH_RISK",
                "CRITICAL".equals(e.riskLevel()) ? "CRITICAL" : "WARNING",
                "High-risk fraud alert " + e.fraudReference(),
                e.description(), null, e.fraudId(), e.customerId()));
    }

    @Transactional(readOnly = true)
    public List<Notification> list(boolean unreadOnly, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = repository.findAll(PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return result.getContent().stream()
                .filter(n -> !unreadOnly || n.getReadAt() == null)
                .toList();
    }

    public Notification markRead(UUID id) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + id));
        n.markRead();
        return n;
    }

    private void save(Notification notification) {
        if (repository.existsBySourceEventId(notification.getSourceEventId())) {
            return;
        }
        try {
            repository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException concurrentDelivery) {
            // Another delivery stored it first.
        }
    }

    private static void requireEventId(UUID eventId) {
        if (eventId == null) {
            throw new InvalidEventException("eventId is missing");
        }
    }
}
