package za.co.capitec.frauddetection.detection.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class EvaluationPublicationRepository {

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

    public java.util.List<UUID> unpublishedEvaluations(int limit) {
        return jdbc.query(
                "SELECT pt.transaction_id FROM processed_transactions pt "
                        + "LEFT JOIN fraud_evaluation_publications p ON p.transaction_id = pt.transaction_id "
                        + "WHERE p.transaction_id IS NULL ORDER BY pt.processed_at LIMIT ?",
                (rs, n) -> rs.getObject("transaction_id", UUID.class), limit);
    }
}
