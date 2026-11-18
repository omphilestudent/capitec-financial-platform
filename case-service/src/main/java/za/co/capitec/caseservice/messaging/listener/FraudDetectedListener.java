package za.co.capitec.caseservice.messaging.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import za.co.capitec.caseservice.cases.service.CaseService;
import za.co.capitec.caseservice.messaging.event.FraudDetectedEvent;

/**
 * Opens a case for fraud detections that require investigation. Other detections
 * are ignored here. Duplicate deliveries are safe: CaseService returns the existing case.
 */
@Component
public class FraudDetectedListener {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectedListener.class);

    private final CaseService caseService;

    public FraudDetectedListener(CaseService caseService) {
        this.caseService = caseService;
    }

    @KafkaListener(topics = FraudDetectedEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onFraudDetected(FraudDetectedEvent event) {
        if (event == null) {
            return;
        }
        var caseResponse = caseService.openCaseForFraud(event);
        log.info("Fraud {} linked to case {}", event.fraudReference(), caseResponse.caseNumber());
    }
}
