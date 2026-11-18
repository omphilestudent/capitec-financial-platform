package za.co.capitec.transactionservice.transaction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitec.transactionservice.common.messaging.OutboxWriter;
import za.co.capitec.transactionservice.transaction.dto.AccountBalanceResponse;
import za.co.capitec.transactionservice.transaction.dto.FundingRequest;
import za.co.capitec.transactionservice.transaction.dto.TransactionResponse;
import za.co.capitec.transactionservice.transaction.entity.EntryKind;
import za.co.capitec.transactionservice.transaction.entity.Transaction;
import za.co.capitec.transactionservice.transaction.entity.TransactionStatus;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;
import za.co.capitec.transactionservice.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FundingAndBalanceTest {

    private final UUID accountId = UUID.fromString("0c59d3f8-f1cb-454a-82b7-a77aaf63987b");

    private TransactionRepository repository;
    private AccountServiceClient accounts;
    private OutboxWriter outbox;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        accounts = mock(AccountServiceClient.class);
        outbox = mock(OutboxWriter.class);
        service = new TransactionService(repository, accounts, outbox);
        when(accounts.accountExists(accountId)).thenReturn(true);
        when(repository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> {
            Transaction saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
    }

    private FundingRequest funding(EntryKind kind, String amount, String key) {
        return new FundingRequest(accountId, kind, new BigDecimal(amount), "ZAR", key, "Synthetic funding");
    }

    @Test
    void openingBalancePostsAPostedCreditAndPublishesOnce() {
        when(repository.findByIdempotencyKey("open-1")).thenReturn(Optional.empty());
        when(repository.existsByAccountIdAndEntryKind(accountId, EntryKind.OPENING_BALANCE)).thenReturn(false);

        TransactionResponse response = service.postFunding(funding(EntryKind.OPENING_BALANCE, "8000.00", "open-1"));

        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(TransactionType.CREDIT, response.type());
        assertEquals(0, new BigDecimal("8000.00").compareTo(response.amount()));
        verify(outbox).write(any(), eq(TransactionCreatedEventTopics.PRIMARY), anyString(), anyString(), any());
        verify(outbox).writeCopy(any(), eq(TransactionCreatedEventTopics.LEGACY), anyString(), anyString(), any());
    }

    @Test
    void replayWithSameKeyReturnsExistingWithoutWritingAgain() {
        Transaction existing = Transaction.funding(accountId, "TXN-EXISTING", new BigDecimal("500.00"), "ZAR",
                "x", EntryKind.FUNDING, "fund-7");
        when(repository.findByIdempotencyKey("fund-7")).thenReturn(Optional.of(existing));

        TransactionResponse response = service.postFunding(funding(EntryKind.FUNDING, "500.00", "fund-7"));

        assertEquals("TXN-EXISTING", response.transactionReference());
        verify(repository, never()).saveAndFlush(any());
        verify(outbox, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void reusingAKeyForADifferentAmountIsRejected() {
        Transaction existing = Transaction.funding(accountId, "TXN-EXISTING", new BigDecimal("500.00"), "ZAR",
                "x", EntryKind.FUNDING, "fund-8");
        when(repository.findByIdempotencyKey("fund-8")).thenReturn(Optional.of(existing));

        assertThrows(LedgerRuleException.class,
                () -> service.postFunding(funding(EntryKind.FUNDING, "900.00", "fund-8")));
    }

    @Test
    void secondOpeningBalanceForTheSameAccountIsRejected() {
        when(repository.findByIdempotencyKey("open-2")).thenReturn(Optional.empty());
        when(repository.existsByAccountIdAndEntryKind(accountId, EntryKind.OPENING_BALANCE)).thenReturn(true);

        assertThrows(LedgerRuleException.class,
                () -> service.postFunding(funding(EntryKind.OPENING_BALANCE, "100.00", "open-2")));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void standardEntriesCannotBeSubmittedAsFunding() {
        assertThrows(LedgerRuleException.class,
                () -> service.postFunding(funding(EntryKind.STANDARD, "100.00", "std-1")));
    }

    @Test
    void nonZarFundingIsRejected() {
        FundingRequest usd = new FundingRequest(accountId, EntryKind.FUNDING, new BigDecimal("10.00"), "USD", "k", null);
        assertThrows(LedgerRuleException.class, () -> service.postFunding(usd));
    }

    @Test
    void balanceIsPostedCreditsMinusPostedDebitsInDecimalArithmetic() {
        when(repository.sumAmount(accountId, TransactionType.CREDIT, TransactionStatus.COMPLETED))
                .thenReturn(new BigDecimal("8000.10"));
        when(repository.sumAmount(accountId, TransactionType.DEBIT, TransactionStatus.COMPLETED))
                .thenReturn(new BigDecimal("1250.55"));

        AccountBalanceResponse balance = service.balance(accountId);

        assertEquals(0, new BigDecimal("6749.55").compareTo(balance.balance()));
        assertEquals("ZAR", balance.currency());
    }

    @Test
    void accountWithNoPostedActivityHasZeroBalance() {
        when(repository.sumAmount(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        AccountBalanceResponse balance = service.balance(accountId);

        assertEquals(0, BigDecimal.ZERO.compareTo(balance.balance()));
    }

    private Transaction pendingDebit(String amount) {
        Transaction tx = new Transaction(accountId, "TXN-PENDING-" + amount, TransactionType.DEBIT,
                new BigDecimal(amount), "ZAR", "debit");
        org.springframework.test.util.ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
        return tx;
    }

    private void balances(String credits, String debits) {
        when(repository.sumAmount(accountId, TransactionType.CREDIT, TransactionStatus.COMPLETED))
                .thenReturn(new BigDecimal(credits));
        when(repository.sumAmount(accountId, TransactionType.DEBIT, TransactionStatus.COMPLETED))
                .thenReturn(new BigDecimal(debits));
    }

    @Test
    void coveredSavingsDebitPostsAndReducesTheBalance() {
        Transaction debit = pendingDebit("1000.00");
        when(repository.findById(debit.getId())).thenReturn(Optional.of(debit));
        when(accounts.accountType(accountId)).thenReturn("SAVINGS");
        balances("8000.00", "0.00");
        when(repository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse posted = service.postPending(debit.getId());

        assertEquals(TransactionStatus.COMPLETED, posted.status());
    }

    @Test
    void savingsDebitThatWouldOverdrawIsRejectedAndStaysPending() {
        Transaction debit = pendingDebit("9000.00");
        when(repository.findById(debit.getId())).thenReturn(Optional.of(debit));
        when(accounts.accountType(accountId)).thenReturn("SAVINGS");
        balances("8000.00", "0.00");

        assertThrows(LedgerRuleException.class, () -> service.postPending(debit.getId()));
        assertEquals(TransactionStatus.PENDING, debit.getStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void creditAccountMayOverdrawWithinLimitButNotBeyondIt() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "creditOverdraftLimit", new BigDecimal("50000.00"));
        Transaction within = pendingDebit("3000.00");
        Transaction beyond = pendingDebit("60000.00");
        when(repository.findById(within.getId())).thenReturn(Optional.of(within));
        when(repository.findById(beyond.getId())).thenReturn(Optional.of(beyond));
        when(accounts.accountType(accountId)).thenReturn("CREDIT");
        balances("1000.00", "0.00");
        when(repository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(TransactionStatus.COMPLETED, service.postPending(within.getId()).status());
        assertThrows(LedgerRuleException.class, () -> service.postPending(beyond.getId()));
    }

    @Test
    void repostingACompletedDebitIsIdempotent() {
        Transaction debit = pendingDebit("100.00");
        debit.setStatus(TransactionStatus.COMPLETED);
        when(repository.findById(debit.getId())).thenReturn(Optional.of(debit));

        assertEquals(TransactionStatus.COMPLETED, service.postPending(debit.getId()).status());
        verify(repository, never()).saveAndFlush(any());
        verify(accounts, never()).accountType(any());
    }

    private static final class TransactionCreatedEventTopics {
        static final String PRIMARY = "transaction.events";
        static final String LEGACY = "transaction-events";
    }
}
