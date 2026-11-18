package za.co.capitec.statementservice.statement.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.capitec.statementservice.statement.integration.IntegrationPayloads.AggregatedTransaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class LedgerRepository {

    public record LedgerRow(UUID aggregatedTransactionId, UUID fraudKey, String externalId, String source,
                            UUID customerId, UUID accountId, String direction, java.math.BigDecimal amount,
                            String currency, String status, String category, String description,
                            LocalDateTime occurredAt, boolean flagged, String decisionOutcome) {
    }

    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean upsertLedgerEntry(AggregatedTransaction tx, UUID fraudKey, String description) {
        return jdbc.update(
                "INSERT INTO statement_ledger_entries (aggregated_transaction_id, source, external_id, fraud_key, "
                        + "customer_id, account_id, direction, amount, currency, status, category, description, "
                        + "occurred_at, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT DO NOTHING",
                tx.aggregatedTransactionId(), tx.source(), tx.externalId(), fraudKey, tx.customerId(), tx.accountId(),
                tx.direction(), tx.amount(), tx.currency(), tx.status(), tx.category(), description,
                tx.occurredAt(), LocalDateTime.now()) == 1;
    }

    public boolean recordFlag(UUID transactionKey, UUID fraudId, String fraudReference, UUID customerId,
                              UUID accountId, String riskLevel) {
        return jdbc.update(
                "INSERT INTO statement_fraud_flags (transaction_key, fraud_id, fraud_reference, customer_id, account_id, "
                        + "risk_level, received_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                transactionKey, fraudId, fraudReference, customerId, accountId, riskLevel, LocalDateTime.now()) == 1;
    }

    public boolean recordDecision(UUID transactionKey, UUID accountId, String outcome) {
        return jdbc.update(
                "INSERT INTO statement_fraud_decisions (transaction_key, account_id, outcome, received_at) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                transactionKey, accountId, outcome, LocalDateTime.now()) == 1;
    }

    public void applyRevision(UUID transactionKey, UUID accountId, UUID supersededFraudId, String revisedOutcome) {
        if (supersededFraudId != null) {
            jdbc.update("DELETE FROM statement_fraud_flags WHERE transaction_key = ? AND fraud_id = ?",
                    transactionKey, supersededFraudId);
        }
        jdbc.update(
                "INSERT INTO statement_fraud_decisions (transaction_key, account_id, outcome, received_at) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (transaction_key) DO UPDATE SET outcome = EXCLUDED.outcome",
                transactionKey, accountId, revisedOutcome, LocalDateTime.now());
    }

    public boolean isFlagged(UUID transactionKey) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM statement_fraud_flags WHERE transaction_key = ?", Integer.class, transactionKey);
        return count != null && count > 0;
    }

    public List<LedgerRow> entriesForAccount(UUID customerId, UUID accountId, LocalDateTime upToExclusive) {
        return jdbc.query(
                "SELECT l.aggregated_transaction_id, l.fraud_key, l.external_id, l.source, l.customer_id, l.account_id, "
                        + "l.direction, l.amount, l.currency, l.status, l.category, l.description, l.occurred_at, "
                        + "EXISTS (SELECT 1 FROM statement_fraud_flags f WHERE f.transaction_key = l.fraud_key) AS flagged, "
                        + "(SELECT d.outcome FROM statement_fraud_decisions d WHERE d.transaction_key = l.fraud_key) "
                        + "AS decision_outcome "
                        + "FROM statement_ledger_entries l "
                        + "WHERE l.customer_id = ? AND l.account_id = ? AND l.occurred_at < ? "
                        + "ORDER BY l.occurred_at, l.external_id",
                (rs, n) -> new LedgerRow(
                        rs.getObject("aggregated_transaction_id", UUID.class),
                        rs.getObject("fraud_key", UUID.class),
                        rs.getString("external_id"),
                        rs.getString("source"),
                        rs.getObject("customer_id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getString("direction"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getObject("occurred_at", LocalDateTime.class),
                        rs.getBoolean("flagged"),
                        rs.getString("decision_outcome")),
                customerId, accountId, upToExclusive);
    }

    public List<UUID> statementsCoveringFlaggedTransaction(UUID fraudKey) {
        return jdbc.query(
                "SELECT s.id FROM statements s "
                        + "JOIN statement_ledger_entries l ON l.account_id = s.account_id "
                        + "AND l.fraud_key = ? "
                        + "AND CAST(l.occurred_at AS DATE) BETWEEN s.statement_period_start AND s.statement_period_end "
                        + "WHERE s.status = 'GENERATED'",
                (rs, n) -> rs.getObject("id", UUID.class), fraudKey);
    }
}
