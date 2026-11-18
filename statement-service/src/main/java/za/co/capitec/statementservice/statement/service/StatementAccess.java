package za.co.capitec.statementservice.statement.service;

import org.springframework.stereotype.Component;
import za.co.capitec.statementservice.statement.domain.AccessType;
import za.co.capitec.statementservice.statement.domain.StatementAccessLog;
import za.co.capitec.statementservice.statement.repository.StatementAccessLogRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class StatementAccess {

    private final StatementAccessLogRepository logs;

    public StatementAccess(StatementAccessLogRepository logs) {
        this.logs = logs;
    }

    public void record(UUID statementId, UUID customerId, String actor, AccessType type, boolean success, String reason) {
        logs.save(new StatementAccessLog(UUID.randomUUID(), statementId, customerId, actor, type,
                LocalDateTime.now(), success, reason == null ? null : reason.substring(0, Math.min(reason.length(), 255))));
    }
}
