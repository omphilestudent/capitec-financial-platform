package za.co.capitec.transactionservice.transaction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitec.transactionservice.transaction.dto.TransactionFilter;
import za.co.capitec.transactionservice.transaction.dto.TransactionSummaryResponse;
import za.co.capitec.transactionservice.transaction.entity.Transaction;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;
import za.co.capitec.transactionservice.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TransactionHistoryFilterTest {

    private final UUID accountId = UUID.fromString("0c59d3f8-f1cb-454a-82b7-a77aaf63987b");

    private TransactionRepository repository;
    private AccountServiceClient accountServiceClient;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        accountServiceClient = mock(AccountServiceClient.class);
        service = new TransactionService(repository, accountServiceClient, mock(za.co.capitec.transactionservice.common.messaging.OutboxWriter.class));
        when(accountServiceClient.accountExists(accountId)).thenReturn(true);
    }

    @Test
    void dateRangeUsesHalfOpenDayBoundariesInTheDatabaseQuery() {
        when(repository.findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(accountId), any(), any())).thenReturn(List.of());

        service.getTransactionsByAccount(accountId,
                new TransactionFilter(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null));

        verify(repository).findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                accountId,
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 12, 1, 0, 0));
    }

    @Test
    void typeFilterSelectsTheTypedQuery() {
        when(repository.findByAccountIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(accountId), eq(TransactionType.DEBIT), any(), any())).thenReturn(List.of());

        service.getTransactionsByAccount(accountId, new TransactionFilter(null, null, TransactionType.DEBIT));

        verify(repository).findByAccountIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(accountId), eq(TransactionType.DEBIT), any(), any());
    }

    @Test
    void fromAfterToIsRejectedBeforeQuerying() {
        InvalidTransactionFilterException ex = assertThrows(InvalidTransactionFilterException.class,
                () -> service.getTransactionsByAccount(accountId,
                        new TransactionFilter(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1), null)));

        assertTrue(ex.getMessage().contains("'from'"));
        verifyNoInteractions(repository);
    }

    @Test
    void summaryTotalsCreditsDebitsAndNetOverTheFilteredSet() {
        List<Transaction> rows = List.of(
                new Transaction(accountId, "TXN-1", TransactionType.CREDIT, new BigDecimal("1000.00"), "ZAR", null),
                new Transaction(accountId, "TXN-2", TransactionType.DEBIT, new BigDecimal("250.50"), "ZAR", null),
                new Transaction(accountId, "TXN-3", TransactionType.DEBIT, new BigDecimal("49.50"), "ZAR", null));
        when(repository.findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(accountId), any(), any())).thenReturn(rows);

        TransactionSummaryResponse summary = service.getTransactionSummary(accountId,
                new TransactionFilter(null, null, null));

        assertEquals(3, summary.transactionCount());
        assertEquals(new BigDecimal("1000.00"), summary.creditTotal());
        assertEquals(new BigDecimal("300.00"), summary.debitTotal());
        assertEquals(new BigDecimal("700.00"), summary.netTotal());
        assertEquals("ALL", summary.type());
    }

    @Test
    void unknownAccountIsRejectedForFilteredQueries() {
        UUID unknown = UUID.randomUUID();
        when(accountServiceClient.accountExists(unknown)).thenReturn(false);

        assertThrows(AccountNotFoundException.class,
                () -> service.getTransactionSummary(unknown, new TransactionFilter(null, null, null)));
    }
}
