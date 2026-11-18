package za.co.capitec.frauddetection.messaging.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import za.co.capitec.frauddetection.common.exception.InvalidEventException;
import za.co.capitec.frauddetection.detection.service.FraudDetectionService;
import za.co.capitec.frauddetection.messaging.event.CaseCreatedEvent;

/** Consumes case.events (CaseCreated only) and links the case to its fraud record. */
@Component
public class CaseEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CaseEventsListener.class);

    private final FraudDetectionService fraudDetectionService;

    public CaseEventsListener(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @KafkaListener(
            topics = CaseCreatedEvent.TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=za.co.capitec.frauddetection.messaging.event.CaseCreatedEvent")
    public void onCaseCreated(CaseCreatedEvent event) {
        if (event == null || event.caseId() == null) {
            throw new InvalidEventException("Invalid case event: caseId is missing");
        }
        fraudDetectionService.linkCase(event);
        log.info("Case {} linked to fraud {}", event.caseId(), event.sourceFraudId());
    }
}
