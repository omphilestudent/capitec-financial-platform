package za.co.capitec.transactionservice.transaction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitec.transactionservice.common.messaging.OutboxWriter;
import za.co.capitec.transactionservice.messaging.event.TransactionCreatedEvent;
import za.co.capitec.transactionservice.transaction.dto.CreateTransactionRequest;
import za.co.capitec.transactionservice.transaction.dto.TransactionResponse;
import za.co.capitec.transactionservice.transaction.entity.Transaction;
import za.co.capitec.transactionservice.transaction.entity.TransactionStatus;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;
import za.co.capitec.transactionservice.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private TransactionRepository transactionRepository;
    private AccountServiceClient accountServiceClient;
    private OutboxWriter outboxWriter;
    private TransactionService transactionService;

    private final UUID accountId =
            UUID.fromString("0c59d3f8-f1cb-454a-82b7-a77aaf63987b");

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        accountServiceClient = mock(AccountServiceClient.class);
        outboxWriter = mock(OutboxWriter.class);

        transactionService = new TransactionService(
                transactionRepository,
                accountServiceClient,
                outboxWriter
        );
    }

    @Test
    void shouldCreateTransactionSuccessfullyAndPublishEvent() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId,
                TransactionType.CREDIT,
                new BigDecimal("1250.50"),
                "zar",
                " Initial deposit "
        );

        when(accountServiceClient.accountExists(accountId)).thenReturn(true);

        // JPA assigns the ID on persist; the stub does the same.
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction saved = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(
                            saved, "id", UUID.randomUUID());
                    return saved;
                });

        TransactionResponse response =
                transactionService.createTransaction(request);

        assertNotNull(response);
        assertEquals(accountId, response.accountId());
        assertEquals(TransactionType.CREDIT, response.type());
        assertEquals(new BigDecimal("1250.50"), response.amount());
        assertEquals("ZAR", response.currency());
        assertEquals("Initial deposit", response.description());
        assertEquals(TransactionStatus.PENDING, response.status());
        assertTrue(response.transactionReference().startsWith("TXN-"));

        verify(transactionRepository).save(any(Transaction.class));

        org.mockito.ArgumentCaptor<Object> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(Object.class);

        verify(outboxWriter).write(
                any(UUID.class),
                eq(TransactionCreatedEvent.TOPIC),
                any(String.class),
                eq(TransactionCreatedEvent.EVENT_TYPE),
                eventCaptor.capture()
        );

        TransactionCreatedEvent event = (TransactionCreatedEvent) eventCaptor.getValue();

        assertNotNull(event.eventId());
        assertEquals(response.id(), event.transactionId());
        assertEquals(accountId, event.accountId());
        assertEquals(response.transactionReference(), event.transactionReference());
        assertEquals("CREDIT", event.type());
        assertEquals(new BigDecimal("1250.50"), event.amount());
        assertEquals("ZAR", event.currency());
        assertEquals("PENDING", event.status());
    }

    @Test
    void shouldRejectTransactionForNonExistentAccount() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                accountId,
                TransactionType.DEBIT,
                new BigDecimal("100.00"),
                "ZAR",
                "Test debit"
        );

        when(accountServiceClient.accountExists(accountId)).thenReturn(false);

        assertThrows(
                AccountNotFoundException.class,
                () -> transactionService.createTransaction(request)
        );

        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRetrieveTransactionById() {
        UUID transactionId = UUID.randomUUID();

        Transaction transaction = new Transaction(
                accountId,
                "TXN-TEST123",
                TransactionType.CREDIT,
                new BigDecimal("500.00"),
                "ZAR",
                "Test transaction"
        );

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(transaction));

        TransactionResponse response =
                transactionService.getTransactionById(transactionId);

        assertEquals(accountId, response.accountId());
        assertEquals(new BigDecimal("500.00"), response.amount());
        verify(transactionRepository).findById(transactionId);
    }

    @Test
    void shouldThrowExceptionWhenTransactionDoesNotExist() {
        UUID transactionId = UUID.randomUUID();

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.empty());

        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.getTransactionById(transactionId)
        );
    }
}
