package za.co.capitec.frauddetection.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record FraudEvaluationRevision(
        UUID eventId,
        UUID revisionId,
        UUID transactionId,
        UUID accountId,
        UUID supersedesFraudId,
        String revisedOutcome,
        String ruleVersion,
        LocalDateTime revisedAt
) {
    public static final String TOPIC = "fraud.evaluation-revisions";
    public static final String EVENT_TYPE = "FraudEvaluationRevised";
}
