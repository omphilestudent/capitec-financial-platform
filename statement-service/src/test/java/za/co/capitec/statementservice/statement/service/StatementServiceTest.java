package za.co.capitec.statementservice.statement.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitec.statementservice.common.exception.AccessDeniedStatementException;
import za.co.capitec.statementservice.common.exception.InvalidStatementRequestException;
import za.co.capitec.statementservice.common.exception.StatementGenerationException;
import za.co.capitec.statementservice.common.messaging.OutboxWriter;
import za.co.capitec.statementservice.security.CurrentUser;
import za.co.capitec.statementservice.statement.client.AccountClient;
import za.co.capitec.statementservice.statement.client.AccountView;
import za.co.capitec.statementservice.statement.client.CustomerClient;
import za.co.capitec.statementservice.statement.client.CustomerView;
import za.co.capitec.statementservice.statement.domain.Statement;
import za.co.capitec.statementservice.statement.events.StatementEvents;
import za.co.capitec.statementservice.statement.pdf.LedgerCalculator;
import za.co.capitec.statementservice.statement.eligibility.EligibilityService;
import za.co.capitec.statementservice.statement.integration.LedgerRepository;
import za.co.capitec.statementservice.statement.integration.LedgerRepository.LedgerRow;
import za.co.capitec.statementservice.statement.pdf.StatementPdfRenderer;
import za.co.capitec.statementservice.statement.repository.StatementRepository;
import za.co.capitec.statementservice.statement.storage.StatementStorage;
import za.co.capitec.statementservice.statement.web.dto.GenerateStatementRequest;
import za.co.capitec.statementservice.statement.web.dto.GenerateStatementRequest.PeriodType;

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
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class StatementServiceTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser("customer-owner", false, customerId);
    private final CurrentUser staff = new CurrentUser("staff", true, null);
    private final CurrentUser stranger = new CurrentUser("customer-other", false, UUID.randomUUID());

    private AccountClient accounts;
    private CustomerClient customers;
    private LedgerRepository ledger;
    private StatementStorage storage;
    private StatementRepository statements;
    private StatementAccess access;
    private OutboxWriter outbox;
    private StatementService service;

    @BeforeEach
    void setUp() throws Exception {
        accounts = mock(AccountClient.class);
        customers = mock(CustomerClient.class);
        ledger = mock(LedgerRepository.class);
        storage = mock(StatementStorage.class);
        statements = mock(StatementRepository.class);
        access = mock(StatementAccess.class);
        outbox = mock(OutboxWriter.class);

        when(accounts.getAccount(accountId)).thenReturn(
                new AccountView(accountId, customerId, "29390331863", "SAVINGS", BigDecimal.ZERO, "ZAR", "ACTIVE"));
        when(customers.getCustomer(customerId)).thenReturn(
                new CustomerView(customerId, "Thabo", "Mokoena", "INDIVIDUAL"));
        when(ledger.entriesForAccount(eq(customerId), eq(accountId), any())).thenReturn(List.of(
                ledgerRow("2026-09-25T09:00:00", "2000.00", "CREDIT", "NOT_DETECTED", false),
                ledgerRow("2026-10-03T09:00:00", "150.00", "DEBIT", "NOT_DETECTED", false),
                ledgerRow("2026-10-10T09:00:00", "500.00", "CREDIT", "NOT_DETECTED", false)));
        when(statements.findByAccountIdAndPeriodStartAndPeriodEnd(any(), any(), any())).thenReturn(Optional.empty());
        when(statements.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.store(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));

        service = new StatementService(accounts, customers, new EligibilityService(ledger), new LedgerCalculator(),
                new StatementPdfRenderer(), storage, statements, access, outbox, 366);
    }

    private LedgerRow ledgerRow(String when, String amount, String direction, String outcome, boolean flagged) {
        return new LedgerRow(UUID.randomUUID(), UUID.randomUUID(), "EXT-" + when, "BANK_FEED", customerId, accountId,
                direction, new BigDecimal(amount), "ZAR", "COMPLETED", "OTHER", "row " + when,
                LocalDateTime.parse(when), flagged, outcome);
    }

    private GenerateStatementRequest monthly(int year, int month) {
        return new GenerateStatementRequest(accountId, PeriodType.MONTHLY, year, month, null, null);
    }

    @Test
    void generatesPdfStoresItAndPublishesStatementGeneratedEvent() throws Exception {
        var result = service.generate(owner, monthly(2026, 10));

        assertTrue(result.created());
        assertEquals("GENERATED", result.statement().status());
        assertEquals(0, new BigDecimal("2350.00").compareTo(result.statement().closingBalance()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.statement().openingBalance()));
        verify(storage).store(startsWith("statements/2026/10/"), any(byte[].class));
        verify(outbox).write(any(), eq(StatementEvents.TOPIC), anyString(),
                eq(StatementEvents.StatementGeneratedEvent.EVENT_TYPE), any());
    }

    @Test
    void flaggedAndPendingTransactionsAreExcludedFromBalancesAndCounts() throws Exception {
        when(ledger.entriesForAccount(eq(customerId), eq(accountId), any())).thenReturn(List.of(
                ledgerRow("2026-09-25T09:00:00", "2000.00", "CREDIT", "NOT_DETECTED", false),
                ledgerRow("2026-10-03T09:00:00", "150.00", "DEBIT", "DETECTED", true),
                ledgerRow("2026-10-04T09:00:00", "75.00", "DEBIT", null, false),
                ledgerRow("2026-10-10T09:00:00", "500.00", "CREDIT", "NOT_DETECTED", false),
                ledgerRow("2026-10-12T09:00:00", "20.00", "DEBIT", "NOT_DETECTED", false)));

        var result = service.generate(owner, monthly(2026, 10));

        assertEquals(0, new BigDecimal("2000.00").compareTo(result.statement().openingBalance()));
        assertEquals(0, new BigDecimal("2480.00").compareTo(result.statement().closingBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(result.statement().totalCredits()));
        assertEquals(0, new BigDecimal("20.00").compareTo(result.statement().totalDebits()));
    }

    @Test
    void accountWithFewerThanTwoEligibleTransactionsCannotGenerate() throws Exception {
        when(ledger.entriesForAccount(eq(customerId), eq(accountId), any())).thenReturn(List.of(
                ledgerRow("2026-10-03T09:00:00", "150.00", "DEBIT", "NOT_DETECTED", false),
                ledgerRow("2026-10-04T09:00:00", "75.00", "DEBIT", "DETECTED", true)));

        var ex = assertThrows(za.co.capitec.statementservice.statement.service.InsufficientEligibleTransactionsException.class,
                () -> service.generate(owner, monthly(2026, 10)));

        assertEquals(1, ex.getEligibleCount());
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void accountTypeMustMatchTheSelectedAccount() throws Exception {
        assertThrows(InvalidStatementRequestException.class, () -> service.generate(owner,
                new GenerateStatementRequest(accountId, PeriodType.MONTHLY, 2026, 10, null, null, "CHEQUE")));
    }

    @Test
    void ownerCannotGenerateForAnotherCustomersAccount() throws Exception {
        assertThrows(AccessDeniedStatementException.class, () -> service.generate(stranger, monthly(2026, 10)));
        verify(storage, never()).store(anyString(), any());
        verify(outbox, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void existingGeneratedStatementIsReturnedWithoutReGenerating() throws Exception {
        Statement existing = new Statement(UUID.randomUUID(), "STM-EXISTING", customerId, accountId, "29390331863",
                "SAVINGS", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "ZAR");
        existing.markGenerated(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "k", LocalDateTime.now());
        when(statements.findByAccountIdAndPeriodStartAndPeriodEnd(accountId, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31))).thenReturn(Optional.of(existing));

        var result = service.generate(owner, monthly(2026, 10));

        assertFalse(result.created());
        assertEquals("STM-EXISTING", result.statement().statementReference());
        verify(storage, never()).store(anyString(), any());
        verify(outbox, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void failureAfterCreationRecordsFailureAndPublishesFailedEvent() throws Exception {
        when(storage.store(anyString(), any())).thenThrow(new java.io.IOException("disk full"));

        assertThrows(StatementGenerationException.class, () -> service.generate(owner, monthly(2026, 10)));

        verify(outbox).write(any(), eq(StatementEvents.TOPIC), anyString(),
                eq(StatementEvents.StatementGenerationFailedEvent.EVENT_TYPE), any());
        verify(outbox, never()).write(any(), any(), any(), eq(StatementEvents.StatementGeneratedEvent.EVENT_TYPE), any());
    }

    @Test
    void monthlyPeriodMustBeValid() {
        assertThrows(InvalidStatementRequestException.class,
                () -> service.generate(owner, monthly(2026, 13)));
        assertThrows(InvalidStatementRequestException.class,
                () -> service.generate(owner, new GenerateStatementRequest(accountId, PeriodType.MONTHLY, null, 5, null, null)));
    }

    @Test
    void customRangeIsValidatedAndCapped() {
        assertThrows(InvalidStatementRequestException.class, () -> service.generate(owner,
                new GenerateStatementRequest(accountId, PeriodType.CUSTOM, null, null,
                        LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1))));
        assertThrows(InvalidStatementRequestException.class, () -> service.generate(owner,
                new GenerateStatementRequest(accountId, PeriodType.CUSTOM, null, null,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2026, 10, 1))));
    }

    @Test
    void futureStartIsRejectedAndFutureEndIsClampedToToday() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertThrows(InvalidStatementRequestException.class, () -> service.generate(owner,
                new GenerateStatementRequest(accountId, PeriodType.CUSTOM, null, null, tomorrow, tomorrow.plusDays(5))));

        LocalDate[] clamped = service.resolvePeriod(new GenerateStatementRequest(accountId, PeriodType.CUSTOM, null, null,
                LocalDate.now().minusDays(3), LocalDate.now().plusDays(10)), LocalDate.now());
        assertEquals(LocalDate.now(), clamped[1]);
    }

    @Test
    void staffMayViewAnyCustomersStatements() {
        assertTrue(staff.canAccessCustomer(UUID.randomUUID()));
        assertTrue(owner.canAccessCustomer(customerId));
        assertFalse(owner.canAccessCustomer(UUID.randomUUID()));
    }

    @Test
    void storageKeysAreDeterministicPerPeriod() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals("statements/2026/03/11111111-2222-3333-4444-555555555555.pdf",
                StatementService.storageKey(id, LocalDate.of(2026, 3, 1)));
    }

    @Test
    void statementReferencesAreUniqueAndWellFormed() {
        String first = StatementService.newReference(LocalDate.of(2026, 11, 18));
        String second = StatementService.newReference(LocalDate.of(2026, 11, 18));
        assertTrue(first.matches("STM-20261118-[0-9A-F]{8}"));
        assertNotEquals(first, second);
    }
}
