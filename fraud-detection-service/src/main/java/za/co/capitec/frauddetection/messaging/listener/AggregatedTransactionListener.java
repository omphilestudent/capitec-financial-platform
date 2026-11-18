package za.co.capitec.frauddetection.messaging.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import za.co.capitec.frauddetection.detection.service.FraudDetectionService;
import za.co.capitec.frauddetection.messaging.event.AggregatedTransactionPayload;
import za.co.capitec.frauddetection.messaging.event.FraudDetectionValidator;
import za.co.capitec.frauddetection.messaging.event.TransactionCreatedEvent;
import za.co.capitec.frauddetection.common.exception.InvalidEventException;

@Component
public class AggregatedTransactionListener {

    private final FraudDetectionService fraudDetectionService;

    public AggregatedTransactionListener(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @KafkaListener(
            topics = AggregatedTransactionPayload.TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=za.co.capitec.frauddetection.messaging.event.AggregatedTransactionPayload")
    public void onAggregated(AggregatedTransactionPayload payload) {
        if (!payload.counted() || AggregatedTransactionPayload.TRANSACTION_SERVICE.equals(payload.source())) {
            return;
        }
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                payload.eventId(),
                payload.aggregatedTransactionId(),
                payload.accountId(),
                payload.externalId(),
                payload.direction(),
                payload.amount(),
                payload.currency(),
                payload.status(),
                payload.occurredAt());
        String problem = FraudDetectionValidator.validate(event);
        if (problem != null) {
            throw new InvalidEventException("Invalid aggregated transaction: " + problem);
        }
        fraudDetectionService.processTransaction(event, AggregatedTransactionPayload.TOPIC);
    }
}
