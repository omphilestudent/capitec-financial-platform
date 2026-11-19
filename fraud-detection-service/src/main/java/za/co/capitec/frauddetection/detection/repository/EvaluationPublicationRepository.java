package za.co.capitec.frauddetection.detection.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class EvaluationPublicationRepository {

    /** A processed transaction whose evaluation has not been published yet. */
    public record PendingEvaluation(UUID transactionId, UUID accountId, String outcome) {
    }

    private final JdbcTemplate jdbc;

    public EvaluationPublicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(UUID transactionId, String outcome) {
        return jdbc.update(
                "INSERT INTO fraud_evaluation_publications (transaction_id, outcome, published_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (transaction_id) DO NOTHING",
                transactionId, outcome, LocalDateTime.now()) == 1;
    }

    public List<PendingEvaluation> unpublishedEvaluations(int limit) {
        return jdbc.query(
                "SELECT pt.transaction_id, pt.account_id, pt.outcome FROM processed_transactions pt "
                        + "LEFT JOIN fraud_evaluation_publications p ON p.transaction_id = pt.transaction_id "
                        + "WHERE p.transaction_id IS NULL ORDER BY pt.processed_at LIMIT ?",
                (rs, n) -> new PendingEvaluation(
                        rs.getObject("transaction_id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getString("outcome")),
                limit);
    }
}
