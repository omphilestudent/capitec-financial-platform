package za.co.capitec.frauddetection.messaging.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import za.co.capitec.frauddetection.common.exception.InvalidEventException;
import za.co.capitec.frauddetection.detection.service.FraudDetectionService;
import za.co.capitec.frauddetection.messaging.event.FraudDetectionValidator;
import za.co.capitec.frauddetection.messaging.event.TransactionCreatedEvent;

@Component
public class TransactionCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionCreatedListener.class);

    private final FraudDetectionService fraudDetectionService;

    public TransactionCreatedListener(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @KafkaListener(
            topics = TransactionCreatedEvent.TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",

            properties = "spring.json.value.default.type=za.co.capitec.frauddetection.messaging.event.TransactionCreatedEvent")
    public void onTransactionCreated(TransactionCreatedEvent event) {
        handle(event, TransactionCreatedEvent.TOPIC);
    }

    @KafkaListener(
            topics = TransactionCreatedEvent.LEGACY_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=za.co.capitec.frauddetection.messaging.event.TransactionCreatedEvent")
    public void onLegacyTransactionCreated(TransactionCreatedEvent event) {
        handle(event, TransactionCreatedEvent.LEGACY_TOPIC);
    }

    private void handle(TransactionCreatedEvent event, String sourceTopic) {
        String problem = FraudDetectionValidator.validate(event);
        if (problem != null) {
            throw new InvalidEventException("Invalid transaction event: " + problem);
        }
        try {
            var outcome = fraudDetectionService.processTransaction(event, sourceTopic);
            log.info("Transaction {} evaluated from {}: {}", event.transactionId(), sourceTopic, outcome);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.info("Transaction {} was processed by a concurrent delivery", event.transactionId());
        }
    }
}
