package za.co.capitec.frauddetection.detection.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.frauddetection.common.messaging.OutboxWriter;
import za.co.capitec.frauddetection.config.FraudRulesProperties;
import za.co.capitec.frauddetection.detection.rules.Assessment;
import za.co.capitec.frauddetection.detection.rules.FraudRuleEngine;
import za.co.capitec.frauddetection.messaging.event.FraudEvaluationRevision;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class HistoricalReevaluationService {

    public static final String RULE_VERSION = "velocity-window-bounded-v1";

    public enum Mode { OFF, DRY_RUN, APPLY }

    public record Candidate(UUID detectionId, String fraudReference, UUID transactionId, UUID accountId,
                            BigDecimal amount, LocalDateTime createdAt) {
    }

    public record Summary(int candidates, int superseded, int confirmed, int skippedAlreadyRevised) {
    }

    private final JdbcTemplate jdbc;
    private final FraudRuleEngine ruleEngine;
    private final OutboxWriter outboxWriter;

    public HistoricalReevaluationService(JdbcTemplate jdbc, FraudRulesProperties rules, OutboxWriter outboxWriter) {
        this.jdbc = jdbc;
        this.ruleEngine = new FraudRuleEngine(rules);
        this.outboxWriter = outboxWriter;
    }

    public List<Candidate> candidates() {
        return jdbc.query(
                "SELECT fd.id, fd.fraud_reference, fd.transaction_id, fd.account_id, pt.amount, pt.transaction_created_at "
                        + "FROM fraud_detections fd JOIN processed_transactions pt ON pt.transaction_id = fd.transaction_id "
                        + "WHERE fd.detection_rule = 'VELOCITY' AND fd.superseded_at IS NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM fraud_reevaluations r WHERE r.detection_id = fd.id "
                        + "AND r.rule_version = ?) ORDER BY pt.transaction_created_at, fd.id",
                (rs, n) -> new Candidate(rs.getObject("id", UUID.class), rs.getString("fraud_reference"),
                        rs.getObject("transaction_id", UUID.class), rs.getObject("account_id", UUID.class),
                        rs.getBigDecimal("amount"), rs.getObject("transaction_created_at", LocalDateTime.class)),
                RULE_VERSION);
    }

    public record Decision(LocalDateTime windowStart, long priorInWindow, boolean detected) {
    }

    public Decision decide(Candidate candidate) {
        LocalDateTime windowStart = ruleEngine.velocityWindowStart(candidate.createdAt());
        Long prior = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_transactions WHERE account_id = ? "
                        + "AND transaction_created_at >= ? AND transaction_created_at <= ? AND transaction_id <> ?",
                Long.class, candidate.accountId(), windowStart, candidate.createdAt(), candidate.transactionId());
        long count = prior == null ? 0 : prior;
        Assessment assessment = ruleEngine.evaluate(candidate.amount(), count + 1);
        return new Decision(windowStart, count, assessment.detected());
    }

    public Summary dryRun() {
        int superseded = 0;
        int confirmed = 0;
        List<Candidate> list = candidates();
        for (Candidate candidate : list) {
            if (decide(candidate).detected()) {
                confirmed++;
            } else {
                superseded++;
            }
        }
        return new Summary(list.size(), superseded, confirmed, 0);
    }

    @Transactional
    public boolean apply(Candidate candidate) {
        Decision decision = decide(candidate);
        UUID revisionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        String revised = decision.detected() ? "DETECTED" : "NOT_DETECTED";
        int inserted = jdbc.update(
                "INSERT INTO fraud_reevaluations (id, detection_id, transaction_id, account_id, original_outcome, "
                        + "revised_outcome, prior_in_window, window_start, window_end, rule_version, revised_at) "
                        + "VALUES (?, ?, ?, ?, 'DETECTED', ?, ?, ?, ?, ?, ?) ON CONFLICT (detection_id, rule_version) DO NOTHING",
                revisionId, candidate.detectionId(), candidate.transactionId(), candidate.accountId(), revised,
                decision.priorInWindow(), decision.windowStart(), candidate.createdAt(), RULE_VERSION, now);
        if (inserted == 0) {
            return false;
        }
        if (!decision.detected()) {
            jdbc.update("UPDATE fraud_detections SET superseded_at = ?, superseded_by_revision = ? WHERE id = ?",
                    now, revisionId, candidate.detectionId());
        }
        outboxWriter.write(UUID.randomUUID(), FraudEvaluationRevision.TOPIC, candidate.transactionId().toString(),
                FraudEvaluationRevision.EVENT_TYPE,
                new FraudEvaluationRevision(UUID.randomUUID(), revisionId, candidate.transactionId(),
                        candidate.accountId(), candidate.detectionId(), revised, RULE_VERSION, now));
        return true;
    }
}
