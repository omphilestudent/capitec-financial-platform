package za.co.capitec.statementservice.statement.integration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class IntegrationPayloads {

    public static final String AGGREGATED_TOPIC = "transaction.aggregated";
    public static final String FRAUD_EVENTS_TOPIC = "fraud.events";
    public static final String FRAUD_EVALUATIONS_TOPIC = "fraud.evaluations";
    public static final String TRANSACTION_SERVICE_SOURCE = "TRANSACTION_SERVICE";

    private IntegrationPayloads() {
    }

    public record AggregatedTransaction(
            UUID eventId,
            UUID aggregatedTransactionId,
            String source,
            String externalId,
            UUID customerId,
            UUID accountId,
            String direction,
            BigDecimal amount,
            String currency,
            String category,
            String status,
            boolean counted,
            LocalDateTime occurredAt) {
    }

    public record FraudDetected(
            UUID eventId,
            UUID fraudId,
            String fraudReference,
            UUID customerId,
            UUID accountId,
            UUID transactionId,
            String riskLevel) {
    }

    public record FraudEvaluated(
            UUID eventId,
            UUID transactionId,
            UUID accountId,
            String outcome,
            LocalDateTime evaluatedAt) {
    }
}
