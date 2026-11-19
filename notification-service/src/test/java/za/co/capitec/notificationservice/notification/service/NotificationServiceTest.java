package za.co.capitec.notificationservice.notification.service;

import org.junit.jupiter.api.Test;
import za.co.capitec.notificationservice.common.exception.InvalidEventException;
import za.co.capitec.notificationservice.common.exception.NotificationNotFoundException;
import za.co.capitec.notificationservice.messaging.event.FraudDetectedEvent;
import za.co.capitec.notificationservice.notification.entity.Notification;
import za.co.capitec.notificationservice.notification.repository.NotificationRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private FraudDetectedEvent fraud(boolean requiresInvestigation, String riskLevel, UUID eventId) {
        return new FraudDetectedEvent(eventId, UUID.randomUUID(), "FRD-1", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 85, riskLevel, "HIGH_AMOUNT", "Amount above threshold",
                LocalDateTime.now(), requiresInvestigation);
    }

    @Test
    void highRiskFraudCreatesACriticalNotificationKeyedByTheEvent() {
        NotificationRepository repo = mock(NotificationRepository.class);
        when(repo.existsBySourceEventId(any())).thenReturn(false);
        NotificationService service = new NotificationService(repo);
        UUID eventId = UUID.randomUUID();

        service.onFraudDetected(fraud(true, "CRITICAL", eventId));

        var captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repo).saveAndFlush(captor.capture());
        assertEquals(eventId, captor.getValue().getSourceEventId());
        assertEquals("FRAUD_HIGH_RISK", captor.getValue().getNotificationType());
        assertEquals("CRITICAL", captor.getValue().getSeverity());
    }

    @Test
    void lowerRiskFraudDoesNotNotify() {
        NotificationRepository repo = mock(NotificationRepository.class);
        new NotificationService(repo).onFraudDetected(fraud(false, "MEDIUM", UUID.randomUUID()));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void redeliveredEventCreatesNoSecondNotification() {
        NotificationRepository repo = mock(NotificationRepository.class);
        when(repo.existsBySourceEventId(any())).thenReturn(true);
        new NotificationService(repo).onFraudDetected(fraud(true, "HIGH", UUID.randomUUID()));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void eventWithoutAnIdIsRejected() {
        NotificationService service = new NotificationService(mock(NotificationRepository.class));
        assertThrows(InvalidEventException.class, () -> service.onFraudDetected(fraud(true, "HIGH", null)));
    }

    @Test
    void markingAnUnknownNotificationReadFails() {
        NotificationRepository repo = mock(NotificationRepository.class);
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThrows(NotificationNotFoundException.class,
                () -> new NotificationService(repo).markRead(UUID.randomUUID()));
    }
}
