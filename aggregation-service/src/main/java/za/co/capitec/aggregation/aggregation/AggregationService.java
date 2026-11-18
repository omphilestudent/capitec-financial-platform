package za.co.capitec.aggregation.aggregation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.aggregation.common.messaging.OutboxWriter;
import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;
import za.co.capitec.aggregation.messaging.AggregationEvents;
import za.co.capitec.aggregation.messaging.AggregationEvents.TransactionAggregatedEvent;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AggregationService {

    public enum Outcome { COUNTED, NOT_COUNTED, DUPLICATE_SOURCE, DUPLICATE_CROSS_SOURCE }

    private final JdbcTemplate jdbc;
    private final OutboxWriter outboxWriter;

    public AggregationService(JdbcTemplate jdbc, OutboxWriter outboxWriter) {
        this.jdbc = jdbc;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public Outcome ingest(NormalizedTransaction tx) {
        UUID id = UUID.randomUUID();
        String fingerprint = fingerprint(tx);
        boolean counted = !"FAILED".equals(tx.status());

        int inserted = jdbc.update(
                "INSERT INTO aggregated_transactions (id, source, external_id, fingerprint, customer_id, account_id, "
                        + "direction, amount, currency, category, description, status, occurred_at, counted, imported_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (source, external_id) DO NOTHING",
                id, tx.source().name(), tx.externalId(), fingerprint, tx.customerId(), tx.accountId(),
                tx.direction().name(), tx.amount(), tx.currency(), tx.category().name(), tx.description(),
                tx.status(), tx.occurredAt(), counted, LocalDateTime.now());
        if (inserted == 0) {
            return Outcome.DUPLICATE_SOURCE;
        }

        Optional<UUID> original = findCountedWithFingerprintFromOtherSource(fingerprint, tx.source().name(), id);
        if (original.isPresent()) {
            jdbc.update("UPDATE aggregated_transactions SET counted = FALSE, duplicate_of = ? WHERE id = ?",
                    original.get(), id);
            return Outcome.DUPLICATE_CROSS_SOURCE;
        }

        if (counted) {
            upsertDailySummary(tx);
        }

        outboxWriter.write(UUID.randomUUID(), AggregationEvents.TOPIC, tx.accountId().toString(),
                AggregationEvents.EVENT_TYPE,
                new TransactionAggregatedEvent(UUID.randomUUID(), id, tx.source().name(), tx.externalId(),
                        tx.customerId(), tx.accountId(), tx.direction().name(), tx.amount(), tx.currency(),
                        tx.category().name(), tx.status(), counted, tx.occurredAt(), LocalDateTime.now()));

        return counted ? Outcome.COUNTED : Outcome.NOT_COUNTED;
    }

    private Optional<UUID> findCountedWithFingerprintFromOtherSource(String fingerprint, String source, UUID self) {
        List<UUID> matches = jdbc.query(
                "SELECT id FROM aggregated_transactions WHERE fingerprint = ? AND source <> ? AND counted = TRUE "
                        + "AND id <> ? ORDER BY imported_at LIMIT 1",
                (rs, n) -> rs.getObject("id", UUID.class), fingerprint, source, self);
        return matches.stream().findFirst();
    }

    private void upsertDailySummary(NormalizedTransaction tx) {
        LocalDate day = tx.occurredAt().toLocalDate();
        boolean credit = tx.direction() == Direction.CREDIT;
        jdbc.update(
                "INSERT INTO daily_account_summaries (account_id, summary_date, customer_id, credit_count, debit_count, "
                        + "credit_total, debit_total) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (account_id, summary_date) DO UPDATE SET "
                        + "credit_count = daily_account_summaries.credit_count + EXCLUDED.credit_count, "
                        + "debit_count = daily_account_summaries.debit_count + EXCLUDED.debit_count, "
                        + "credit_total = daily_account_summaries.credit_total + EXCLUDED.credit_total, "
                        + "debit_total = daily_account_summaries.debit_total + EXCLUDED.debit_total",
                tx.accountId(), day, tx.customerId(),
                credit ? 1 : 0, credit ? 0 : 1,
                credit ? tx.amount() : BigDecimal.ZERO, credit ? BigDecimal.ZERO : tx.amount());
    }

    static String fingerprint(NormalizedTransaction tx) {
        String basis = String.join("|",
                tx.accountId().toString(),
                tx.direction().name(),
                tx.amount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                tx.occurredAt().truncatedTo(ChronoUnit.MINUTES).toString(),
                tx.description() == null ? "" : tx.description().trim().toLowerCase(Locale.ROOT));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
