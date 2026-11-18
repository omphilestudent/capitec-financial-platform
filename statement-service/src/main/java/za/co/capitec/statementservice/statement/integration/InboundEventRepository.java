package za.co.capitec.statementservice.statement.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class InboundEventRepository {

    private final JdbcTemplate jdbc;

    public InboundEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(UUID eventId, String topic) {
        return jdbc.update(
                "INSERT INTO statement_inbound_events (event_id, topic, received_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (event_id) DO NOTHING",
                eventId, topic, LocalDateTime.now()) == 1;
    }
}
