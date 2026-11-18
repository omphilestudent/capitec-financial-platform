package za.co.capitec.notificationservice.messaging.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import za.co.capitec.notificationservice.messaging.event.CaseCreatedEvent;
import za.co.capitec.notificationservice.messaging.event.CaseUpdatedEvent;
import za.co.capitec.notificationservice.messaging.event.FraudDetectedEvent;
import za.co.capitec.notificationservice.notification.service.NotificationService;

/**
 * One listener per topic, each with its payload type. case.events carries creations,
 * case.updates carries changes, and fraud.events carries detections.
 */
@Component
public class NotificationEventListeners {

    private final NotificationService notificationService;

    public NotificationEventListeners(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = CaseCreatedEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=za.co.capitec.notificationservice.messaging.event.CaseCreatedEvent")
    public void onCaseCreated(CaseCreatedEvent event) {
        notificationService.onCaseCreated(event);
    }

    @KafkaListener(topics = CaseUpdatedEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=za.co.capitec.notificationservice.messaging.event.CaseUpdatedEvent")
    public void onCaseUpdated(CaseUpdatedEvent event) {
        notificationService.onCaseUpdated(event);
    }

    @KafkaListener(topics = FraudDetectedEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=za.co.capitec.notificationservice.messaging.event.FraudDetectedEvent")
    public void onFraudDetected(FraudDetectedEvent event) {
        notificationService.onFraudDetected(event);
    }
}
