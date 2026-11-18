package za.co.capitec.frauddetection.detection.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ProcessedEventStore {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claim(UUID eventId, String topic, UUID transactionId) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, topic, transaction_id, processed_at) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
                eventId, topic, transactionId, LocalDateTime.now());
        return inserted == 1;
    }
}
