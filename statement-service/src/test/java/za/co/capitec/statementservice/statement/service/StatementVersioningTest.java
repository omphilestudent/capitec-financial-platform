package za.co.capitec.statementservice.statement.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitec.statementservice.common.exception.InvalidStatementRequestException;
import za.co.capitec.statementservice.common.messaging.OutboxWriter;
import za.co.capitec.statementservice.security.CurrentUser;
import za.co.capitec.statementservice.statement.client.AccountClient;
import za.co.capitec.statementservice.statement.client.AccountView;
import za.co.capitec.statementservice.statement.client.CustomerClient;
import za.co.capitec.statementservice.statement.client.CustomerView;
import za.co.capitec.statementservice.statement.domain.Statement;
import za.co.capitec.statementservice.statement.eligibility.EligibilityService;
import za.co.capitec.statementservice.statement.integration.LedgerRepository;
import za.co.capitec.statementservice.statement.integration.LedgerRepository.LedgerRow;
import za.co.capitec.statementservice.statement.pdf.LedgerCalculator;
import za.co.capitec.statementservice.statement.pdf.StatementPdfRenderer;
import za.co.capitec.statementservice.statement.repository.StatementRepository;
import za.co.capitec.statementservice.statement.repository.StatementSnapshotRepository;
import za.co.capitec.statementservice.statement.storage.StatementStorage;

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

class StatementVersioningTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser("customer-owner", false, customerId);
    private final CurrentUser stranger = new CurrentUser("customer-other", false, UUID.randomUUID());
    private final LocalDate start = LocalDate.of(2026, 10, 1);
    private final LocalDate end = LocalDate.of(2026, 10, 31);

    private AccountClient accounts;
    private LedgerRepository ledger;
    private StatementRepository statements;
    private StatementStorage storage;
    private StatementSnapshotRepository snapshots;
    private StatementService service;
    private List<LedgerRow> rows;

    private LedgerRow row(String when, String amount, String direction) {
        return new LedgerRow(UUID.randomUUID(), UUID.randomUUID(), "EXT-" + when, "BANK_FEED", customerId, accountId,
                direction, new BigDecimal(amount), "ZAR", "COMPLETED", "OTHER", "row", LocalDateTime.parse(when),
                false, "NOT_DETECTED");
    }

    @BeforeEach
    void setUp() throws Exception {
        accounts = mock(AccountClient.class);
        ledger = mock(LedgerRepository.class);
        statements = mock(StatementRepository.class);
        storage = mock(StatementStorage.class);
        snapshots = mock(StatementSnapshotRepository.class);
        when(accounts.getAccount(accountId)).thenReturn(
                new AccountView(accountId, customerId, "29390331863", "SAVINGS", BigDecimal.ZERO, "ZAR", "ACTIVE"));
        CustomerClient customers = mock(CustomerClient.class);
        when(customers.getCustomer(customerId)).thenReturn(new CustomerView(customerId, "Synthetic", "Customer", "INDIVIDUAL"));
        rows = List.of(row("2026-09-25T09:00:00", "2000.00", "CREDIT"), row("2026-10-03T09:00:00", "150.00", "DEBIT"),
                row("2026-10-10T09:00:00", "500.00", "CREDIT"));
        when(ledger.entriesForAccount(eq(customerId), eq(accountId), any())).thenReturn(rows);
        when(storage.store(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(statements.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statements.saveAndFlush(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statements.maxVersion(any(), any(), any())).thenReturn(1);
        service = new StatementService(accounts, customers, new EligibilityService(ledger), new LedgerCalculator(),
                new StatementPdfRenderer(), storage, statements, snapshots, mock(StatementAccess.class),
                mock(OutboxWriter.class), 366);
    }

    private Statement issuedVersion(String fingerprint) {
        Statement s = new Statement(UUID.randomUUID(), "STM-V1", customerId, accountId, "29390331863", "SAVINGS",
                start, end, "ZAR");
        s.markGenerated(BigDecimal.ZERO, new BigDecimal("2350.00"), new BigDecimal("2500.00"),
                new BigDecimal("150.00"), "statements/v1.pdf", LocalDateTime.now());
        s.configureVersion(1, "Initial generation", fingerprint);
        s.promoteToCurrent();
        return s;
    }

    @Test
    void regenerationWithUnchangedEligibilityReturnsTheCurrentVersionWithoutANewVersion() throws Exception {
        String fingerprint = StatementService.fingerprintOf(new EligibilityService(ledger).load(customerId, accountId, end));
        Statement current = issuedVersion(fingerprint);
        when(statements.findById(current.getId())).thenReturn(Optional.of(current));

        var result = service.regenerate(owner, current.getId(), "check");

        assertFalse(result.created());
        assertEquals(1, result.statement().versionNumber());
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void regenerationAfterEligibilityChangeCreatesVersionTwoAndSupersedesVersionOne() throws Exception {
        Statement current = issuedVersion("0".repeat(64));
        when(statements.findById(current.getId())).thenReturn(Optional.of(current));
        when(statements.maxVersion(accountId, start, end)).thenReturn(1);

        var result = service.regenerate(owner, current.getId(), "Fraud review cleared");

        assertTrue(result.created());
        assertEquals(2, result.statement().versionNumber());
        assertEquals("CURRENT", result.statement().versionStatus());
        assertEquals("SUPERSEDED", current.getVersionStatus());
        assertEquals(result.statement().id(), current.getSupersededBy());
        verify(snapshots).save(eq(result.statement().id()), any());
    }

    @Test
    void supersededVersionCannotBeRegenerated() throws Exception {
        Statement old = issuedVersion("0".repeat(64));
        old.markSuperseded(UUID.randomUUID());
        when(statements.findById(old.getId())).thenReturn(Optional.of(old));

        assertThrows(InvalidStatementRequestException.class, () -> service.regenerate(owner, old.getId(), "x"));
    }

    @Test
    void anotherCustomerCannotRegenerateTheStatement() throws Exception {
        Statement current = issuedVersion("0".repeat(64));
        when(statements.findById(current.getId())).thenReturn(Optional.of(current));

        assertThrows(za.co.capitec.statementservice.common.exception.AccessDeniedStatementException.class,
                () -> service.regenerate(stranger, current.getId(), "x"));
        verify(storage, never()).store(anyString(), any());
    }
}
