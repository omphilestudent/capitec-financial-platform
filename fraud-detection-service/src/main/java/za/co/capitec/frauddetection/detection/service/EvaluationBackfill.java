package za.co.capitec.frauddetection.detection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.capitec.frauddetection.common.messaging.OutboxWriter;
import za.co.capitec.frauddetection.detection.repository.EvaluationPublicationRepository;
import za.co.capitec.frauddetection.messaging.event.FraudEvaluatedEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Publishes a FraudEvaluatedEvent for every processed transaction that has not been published yet.
 *
 * Each batch runs in its own short transaction, so the claim row and the outbox row for a transaction
 * are committed together, and a crash mid-backfill loses nothing: the next start resumes from the
 * rows still unclaimed. The pending query returns everything the batch needs in one statement, so the
 * backfill does not issue one extra lookup per transaction.
 */
@Component
public class EvaluationBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationBackfill.class);
    static final int BATCH = 500;

    private final TransactionTemplate tx;
    private final EvaluationPublicationRepository publications;
    private final OutboxWriter outboxWriter;

    public EvaluationBackfill(TransactionTemplate tx, EvaluationPublicationRepository publications,
                              OutboxWriter outboxWriter) {
        this.tx = tx;
        this.publications = publications;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        int batch;
        do {
            Integer published = tx.execute(status -> publishBatch());
            batch = published == null ? 0 : published;
            total += batch;
        } while (batch > 0);
        if (total > 0) {
            log.info("EVALUATION_BACKFILL published {} fraud evaluation events", total);
        }
    }

    int publishBatch() {
        List<EvaluationPublicationRepository.PendingEvaluation> pending = publications.unpublishedEvaluations(BATCH);
        for (var row : pending) {
            publications.claim(row.transactionId(), row.outcome());
            outboxWriter.write(UUID.nameUUIDFromBytes((row.transactionId() + ":" + row.outcome()).getBytes()),
                    FraudEvaluatedEvent.TOPIC, row.transactionId().toString(), FraudEvaluatedEvent.EVENT_TYPE,
                    new FraudEvaluatedEvent(UUID.nameUUIDFromBytes(("eval:" + row.transactionId()).getBytes()),
                            row.transactionId(), row.accountId(), row.outcome(), LocalDateTime.now()));
        }
        return pending.size();
    }
}
