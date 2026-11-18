package za.co.capitec.statementservice.statement.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.statementservice.common.exception.InvalidEventException;
import za.co.capitec.statementservice.statement.integration.IntegrationPayloads.AggregatedTransaction;
import za.co.capitec.statementservice.statement.integration.IntegrationPayloads.FraudDetected;
import za.co.capitec.statementservice.statement.integration.IntegrationPayloads.FraudEvaluated;
import za.co.capitec.statementservice.statement.service.StatementWithdrawalService;

import java.util.Objects;
import java.util.UUID;

@Component
public class StatementIntegrationListeners {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final InboundEventRepository inbound;
    private final LedgerRepository ledger;
    private final StatementWithdrawalService withdrawals;

    public StatementIntegrationListeners(InboundEventRepository inbound, LedgerRepository ledger,
                                         StatementWithdrawalService withdrawals) {
        this.inbound = inbound;
        this.ledger = ledger;
        this.withdrawals = withdrawals;
    }

    @KafkaListener(topics = IntegrationPayloads.AGGREGATED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onAggregated(String json) throws JsonProcessingException {
        AggregatedTransaction tx = parse(json, AggregatedTransaction.class);
        require(tx.eventId() != null && tx.aggregatedTransactionId() != null && tx.customerId() != null
                && tx.accountId() != null && tx.amount() != null && tx.direction() != null
                && tx.occurredAt() != null && tx.currency() != null && tx.status() != null, "aggregated event is incomplete");
        if (!inbound.claim(tx.eventId(), IntegrationPayloads.AGGREGATED_TOPIC)) {
            return;
        }
        if (!tx.counted()) {
            return;
        }
        UUID fraudKey = IntegrationPayloads.TRANSACTION_SERVICE_SOURCE.equals(tx.source())
                ? UUID.fromString(tx.externalId())
                : tx.aggregatedTransactionId();
        boolean inserted = ledger.upsertLedgerEntry(tx, fraudKey, null);
        if (inserted && ledger.isFlagged(fraudKey)) {
            withdrawals.withdrawFor(fraudKey);
        }
    }

    @KafkaListener(topics = IntegrationPayloads.FRAUD_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onFraudDetected(String json) throws JsonProcessingException {
        FraudDetected fraud = parse(json, FraudDetected.class);
        require(fraud.eventId() != null && fraud.fraudId() != null && fraud.transactionId() != null
                && fraud.customerId() != null && fraud.accountId() != null, "fraud event is incomplete");
        if (!inbound.claim(fraud.eventId(), IntegrationPayloads.FRAUD_EVENTS_TOPIC)) {
            return;
        }
        ledger.recordFlag(fraud.transactionId(), fraud.fraudId(), fraud.fraudReference(), fraud.customerId(),
                fraud.accountId(), fraud.riskLevel());
        withdrawals.withdrawFor(fraud.transactionId());
    }

    @KafkaListener(topics = IntegrationPayloads.FRAUD_EVALUATIONS_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onFraudEvaluated(String json) throws JsonProcessingException {
        FraudEvaluated evaluation = parse(json, FraudEvaluated.class);
        require(evaluation.eventId() != null && evaluation.transactionId() != null && evaluation.accountId() != null
                && evaluation.outcome() != null, "fraud evaluation is incomplete");
        if (!inbound.claim(evaluation.eventId(), IntegrationPayloads.FRAUD_EVALUATIONS_TOPIC)) {
            return;
        }
        ledger.recordDecision(evaluation.transactionId(), evaluation.accountId(), evaluation.outcome());
        if ("DETECTED".equals(evaluation.outcome())) {
            withdrawals.withdrawFor(evaluation.transactionId());
        }
    }

    @KafkaListener(topics = IntegrationPayloads.FRAUD_REVISIONS_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onFraudRevised(String json) throws JsonProcessingException {
        IntegrationPayloads.FraudRevision revision = parse(json, IntegrationPayloads.FraudRevision.class);
        require(revision.eventId() != null && revision.transactionId() != null && revision.accountId() != null
                && revision.revisedOutcome() != null, "fraud revision is incomplete");
        if (!inbound.claim(revision.eventId(), IntegrationPayloads.FRAUD_REVISIONS_TOPIC)) {
            return;
        }
        ledger.applyRevision(revision.transactionId(), revision.accountId(), revision.supersedesFraudId(),
                revision.revisedOutcome());
    }

    private static <T> T parse(String json, Class<T> type) throws JsonProcessingException {
        Objects.requireNonNull(json, "empty payload");
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Malformed " + type.getSimpleName() + " payload: " + e.getOriginalMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidEventException(message);
        }
    }
}
