package za.co.capitec.statementservice.statement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.statementservice.statement.domain.AccessType;
import za.co.capitec.statementservice.statement.domain.Statement;
import za.co.capitec.statementservice.statement.domain.StatementDownloadToken;
import za.co.capitec.statementservice.statement.integration.LedgerRepository;
import za.co.capitec.statementservice.statement.repository.StatementDownloadTokenRepository;
import za.co.capitec.statementservice.statement.repository.StatementRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class StatementWithdrawalService {

    static final String SYSTEM_ACTOR = "system:fraud-integration";

    private final LedgerRepository ledger;
    private final StatementRepository statements;
    private final StatementDownloadTokenRepository tokens;
    private final StatementAccess access;

    public StatementWithdrawalService(LedgerRepository ledger, StatementRepository statements,
                                      StatementDownloadTokenRepository tokens, StatementAccess access) {
        this.ledger = ledger;
        this.statements = statements;
        this.tokens = tokens;
        this.access = access;
    }

    @Transactional
    public int withdrawFor(UUID transactionKey) {
        int withdrawn = 0;
        for (UUID statementId : ledger.statementsCoveringFlaggedTransaction(transactionKey)) {
            Statement statement = statements.findById(statementId).orElse(null);
            if (statement == null) {
                continue;
            }
            statement.markWithdrawn("Withdrawn: a transaction on this statement is under fraud review");
            statements.save(statement);
            java.util.List<StatementDownloadToken> active = tokens.findByStatementIdAndRevokedAtIsNull(statementId);
            LocalDateTime now = LocalDateTime.now();
            active.forEach(t -> t.revoke(now));
            tokens.saveAll(active);
            access.record(statementId, statement.getCustomerId(), SYSTEM_ACTOR, AccessType.WITHDRAWN, true,
                    "flagged transaction " + transactionKey);
            withdrawn++;
        }
        return withdrawn;
    }
}
