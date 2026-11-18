package za.co.capitec.transactionservice.transaction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitec.transactionservice.common.messaging.OutboxWriter;
import za.co.capitec.transactionservice.transaction.dto.CreateTransactionRequest;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;
import za.co.capitec.transactionservice.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyntheticOccurredAtTest {

    private final UUID accountId = UUID.randomUUID();
    private TransactionService service;

    @BeforeEach
    void setUp() {
        AccountServiceClient accounts = mock(AccountServiceClient.class);
        when(accounts.accountExists(accountId)).thenReturn(true);
        TransactionRepository repo = mock(TransactionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new TransactionService(repo, accounts, mock(OutboxWriter.class));
    }

    private CreateTransactionRequest request(Boolean synthetic, LocalDateTime occurredAt) {
        return new CreateTransactionRequest(accountId, TransactionType.DEBIT, new BigDecimal("10.00"), "ZAR",
                "test", synthetic, occurredAt);
    }

    @Test
    void backdatedOccurredAtIsRejectedForRealTransactions() {
        assertThrows(LedgerRuleException.class,
                () -> service.createTransaction(request(null, LocalDateTime.now().minusDays(10))));
    }

    @Test
    void syntheticOccurredAtOutsideTheSixteenMonthWindowIsRejected() {
        assertThrows(LedgerRuleException.class,
                () -> service.createTransaction(request(true, LocalDateTime.now().minusMonths(20))));
        assertThrows(LedgerRuleException.class,
                () -> service.createTransaction(request(true, LocalDateTime.now().plusDays(1))));
    }
}
