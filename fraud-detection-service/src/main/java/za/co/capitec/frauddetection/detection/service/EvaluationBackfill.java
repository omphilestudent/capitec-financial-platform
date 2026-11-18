package za.co.capitec.frauddetection.detection.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.frauddetection.common.messaging.OutboxWriter;
import za.co.capitec.frauddetection.detection.repository.EvaluationPublicationRepository;
import za.co.capitec.frauddetection.messaging.event.FraudEvaluatedEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class EvaluationBackfill implements ApplicationRunner {

    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;
    private final EvaluationPublicationRepository publications;
    private final OutboxWriter outboxWriter;

    public EvaluationBackfill(JdbcTemplate jdbc, EvaluationPublicationRepository publications, OutboxWriter outboxWriter) {
        this.jdbc = jdbc;
        this.publications = publications;
        this.outboxWriter = outboxWriter;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        while (publishBatch() > 0) {
            continue;
        }
    }

    int publishBatch() {
        List<UUID> pending = publications.unpublishedEvaluations(BATCH);
        for (UUID transactionId : pending) {
            var row = jdbc.queryForMap(
                    "SELECT account_id, outcome FROM processed_transactions WHERE transaction_id = ?", transactionId);
            String outcome = (String) row.get("outcome");
            publications.claim(transactionId, outcome);
            outboxWriter.write(UUID.nameUUIDFromBytes((transactionId + ":" + outcome).getBytes()),
                    FraudEvaluatedEvent.TOPIC, transactionId.toString(), FraudEvaluatedEvent.EVENT_TYPE,
                    new FraudEvaluatedEvent(UUID.nameUUIDFromBytes(("eval:" + transactionId).getBytes()),
                            transactionId, (UUID) row.get("account_id"), outcome, LocalDateTime.now()));
        }
        return pending.size();
    }
}
