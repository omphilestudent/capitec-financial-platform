
package za.co.capitec.statementservice.statement.service;

import org.junit.jupiter.api.Test;
import za.co.capitec.statementservice.statement.domain.AccessType;
import za.co.capitec.statementservice.statement.domain.Statement;
import za.co.capitec.statementservice.statement.domain.StatementDownloadToken;
import za.co.capitec.statementservice.statement.domain.StatementStatus;
import za.co.capitec.statementservice.statement.integration.LedgerRepository;
import za.co.capitec.statementservice.statement.repository.StatementDownloadTokenRepository;
import za.co.capitec.statementservice.statement.repository.StatementRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StatementWithdrawalServiceTest {

    @Test
    void flagWithdrawsGeneratedStatementsCoveringTheTransactionAndRevokesTheirLinks() {
        LedgerRepository ledger = mock(LedgerRepository.class);
        StatementRepository statements = mock(StatementRepository.class);
        StatementDownloadTokenRepository tokens = mock(StatementDownloadTokenRepository.class);
        StatementAccess access = mock(StatementAccess.class);
        StatementWithdrawalService service = new StatementWithdrawalService(ledger, statements, tokens, access);

        UUID fraudKey = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        Statement statement = new Statement(statementId, "STM-1", UUID.randomUUID(), UUID.randomUUID(), "1", "SAVINGS",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "ZAR");
        statement.markGenerated(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "k", LocalDateTime.now());
        StatementDownloadToken token = new StatementDownloadToken(UUID.randomUUID(), statementId, "h".repeat(64),
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now());

        when(ledger.statementsCoveringFlaggedTransaction(fraudKey)).thenReturn(List.of(statementId));
        when(statements.findById(statementId)).thenReturn(Optional.of(statement));
        when(tokens.findByStatementIdAndRevokedAtIsNull(statementId)).thenReturn(List.of(token));

        int withdrawn = service.withdrawFor(fraudKey);

        assertEquals(1, withdrawn);
        assertEquals(StatementStatus.WITHDRAWN, statement.getStatus());
        assertTrue(token.isRevoked());
        verify(access).record(eq(statementId), any(), anyString(), eq(AccessType.WITHDRAWN), eq(true), any());
    }

    @Test
    void flagWithNoCoveringStatementChangesNothing() {
        LedgerRepository ledger = mock(LedgerRepository.class);
        when(ledger.statementsCoveringFlaggedTransaction(any())).thenReturn(List.of());
        StatementWithdrawalService service = new StatementWithdrawalService(ledger, mock(StatementRepository.class),
                mock(StatementDownloadTokenRepository.class), mock(StatementAccess.class));

        assertEquals(0, service.withdrawFor(UUID.randomUUID()));
    }
}
