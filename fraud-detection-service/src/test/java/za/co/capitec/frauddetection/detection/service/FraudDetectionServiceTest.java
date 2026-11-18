package za.co.capitec.frauddetection.detection.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.capitec.frauddetection.common.messaging.OutboxWriter;
import za.co.capitec.frauddetection.config.FraudRulesProperties;
import za.co.capitec.frauddetection.detection.entity.FraudDetection;
import za.co.capitec.frauddetection.detection.entity.PendingCaseLink;
import za.co.capitec.frauddetection.detection.entity.ProcessedTransaction;
import za.co.capitec.frauddetection.detection.repository.FraudDetectionRepository;
import za.co.capitec.frauddetection.detection.repository.PendingCaseLinkRepository;
import za.co.capitec.frauddetection.detection.repository.ProcessedTransactionRepository;
import za.co.capitec.frauddetection.detection.rules.FraudRuleEngine;
import za.co.capitec.frauddetection.detection.rules.RiskLevel;
import za.co.capitec.frauddetection.messaging.event.CaseCreatedEvent;
import za.co.capitec.frauddetection.messaging.event.FraudDetectedEvent;
import za.co.capitec.frauddetection.messaging.event.TransactionCreatedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock AccountClient accountClient;
    @Mock FraudDetectionRepository fraudRepository;
    @Mock ProcessedTransactionRepository processedRepository;
    @Mock PendingCaseLinkRepository pendingLinkRepository;
    @Mock OutboxWriter outboxWriter;
    @Mock za.co.capitec.frauddetection.detection.repository.ProcessedEventStore processedEventStore;
    @Mock za.co.capitec.frauddetection.detection.repository.EvaluationPublicationRepository evaluationPublications;

    private FraudDetectionService service;
    private final UUID accountId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        FraudRulesProperties rules = new FraudRulesProperties(
                new FraudRulesProperties.HighAmount(true, new BigDecimal("50000"), 70),
                new FraudRulesProperties.Velocity(true, 5, 10, 60),
                new FraudRulesProperties.Levels(30, 60, 80), new FraudRulesProperties.CrossBorder(false, 60), new FraudRulesProperties.LargeEft(false, new BigDecimal("20000"), 65));
        org.mockito.Mockito.lenient().when(processedEventStore.claim(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(evaluationPublications.claim(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        service = new FraudDetectionService(new FraudRuleEngine(rules), accountClient, fraudRepository,
                processedRepository, pendingLinkRepository, outboxWriter, rules, processedEventStore, evaluationPublications);
    }

    private TransactionCreatedEvent tx(UUID transactionId, String amount) {
        return new TransactionCreatedEvent(UUID.randomUUID(), transactionId, accountId,
                "TXN-TEST", "CREDIT", new BigDecimal(amount), "ZAR", "PENDING", LocalDateTime.now());
    }

    @Test
    void duplicateTransactionIsIgnoredAndNothingIsWritten() {
        UUID txId = UUID.randomUUID();
        when(processedRepository.existsById(txId)).thenReturn(true);

        assertEquals(FraudDetectionService.Outcome.DUPLICATE, service.processTransaction(tx(txId, "60000")));

        verifyNoInteractions(accountClient, fraudRepository, outboxWriter);
        verify(processedRepository, never()).save(any());
    }

    @Test
    void ordinaryTransactionCreatesNoFraudRecordAndNoEvent() {
        UUID txId = UUID.randomUUID();
        when(processedRepository.existsById(txId)).thenReturn(false);
        when(processedRepository.countByAccountIdAndTransactionCreatedAtBetween(eq(accountId), any(), any()))
                .thenReturn(0L);

        assertEquals(FraudDetectionService.Outcome.NOT_DETECTED, service.processTransaction(tx(txId, "250.00")));

        ArgumentCaptor<ProcessedTransaction> saved = ArgumentCaptor.forClass(ProcessedTransaction.class);
        verify(processedRepository).save(saved.capture());
        assertEquals("NOT_DETECTED", saved.getValue().getOutcome());
        verify(fraudRepository, never()).saveAndFlush(any());
        verify(outboxWriter, never()).write(any(), eq(za.co.capitec.frauddetection.messaging.event.FraudDetectedEvent.TOPIC), any(), any(), any());
        verify(outboxWriter).write(any(), eq(za.co.capitec.frauddetection.messaging.event.FraudEvaluatedEvent.TOPIC), any(), any(), any());
        verifyNoInteractions(accountClient);
    }

    @Test
    void highAmountCreatesFraudRecordWithRealTransactionData() {
        UUID txId = UUID.randomUUID();
        when(processedRepository.existsById(txId)).thenReturn(false);
        when(processedRepository.countByAccountIdAndTransactionCreatedAtBetween(eq(accountId), any(), any()))
                .thenReturn(0L);
        when(accountClient.findAccount(accountId)).thenReturn(Optional.of(
                new AccountClient.AccountView(accountId, customerId)));
        when(fraudRepository.nextFraudReference()).thenReturn("FRD-100001");
        when(fraudRepository.saveAndFlush(any(FraudDetection.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(FraudDetectionService.Outcome.DETECTED, service.processTransaction(tx(txId, "60000")));

        ArgumentCaptor<FraudDetection> fraud = ArgumentCaptor.forClass(FraudDetection.class);
        verify(fraudRepository).saveAndFlush(fraud.capture());
        FraudDetection f = fraud.getValue();
        assertEquals("FRD-100001", f.getFraudReference());
        assertEquals(txId, f.getTransactionId());
        assertEquals(accountId, f.getAccountId());
        assertEquals(customerId, f.getCustomerId());
        assertEquals(RiskLevel.HIGH, f.getRiskLevel());
        assertEquals("HIGH_AMOUNT", f.getDetectionRule());
        assertTrue(f.getDetectionDescription().contains("60000"));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outboxWriter).write(any(), eq(FraudDetectedEvent.TOPIC), eq(f.getId().toString()),
                eq(FraudDetectedEvent.EVENT_TYPE), event.capture());
        FraudDetectedEvent published = (FraudDetectedEvent) event.getValue();
        assertTrue(published.requiresInvestigation());
        assertEquals(txId, published.transactionId());
    }

    @Test
    void accountServiceFailureLeavesNothingWritten() {
        UUID txId = UUID.randomUUID();
        when(processedRepository.existsById(txId)).thenReturn(false);
        when(processedRepository.countByAccountIdAndTransactionCreatedAtBetween(eq(accountId), any(), any()))
                .thenReturn(0L);
        when(accountClient.findAccount(accountId)).thenThrow(new AccountLookupException("down", null));

        assertThrows(AccountLookupException.class, () -> service.processTransaction(tx(txId, "60000")));
        verify(processedRepository, never()).save(any());
        verify(fraudRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownAccountIsRecordedWithoutAFraudRecord() {
        UUID txId = UUID.randomUUID();
        when(processedRepository.existsById(txId)).thenReturn(false);
        when(processedRepository.countByAccountIdAndTransactionCreatedAtBetween(eq(accountId), any(), any()))
                .thenReturn(0L);
        when(accountClient.findAccount(accountId)).thenReturn(Optional.empty());

        assertEquals(FraudDetectionService.Outcome.ACCOUNT_NOT_FOUND, service.processTransaction(tx(txId, "60000")));
        verify(fraudRepository, never()).saveAndFlush(any());
    }

    @Test
    void caseLinkIsAppliedToExistingFraudOnce() {
        FraudDetection fraud = new FraudDetection(UUID.randomUUID(), "FRD-1", customerId, accountId,
                UUID.randomUUID(), "TRANSACTION_RISK", "HIGH_VALUE_TRANSACTION", 70, RiskLevel.HIGH,
                "HIGH_AMOUNT", "x", "RULE_ENGINE");
        UUID caseId = UUID.randomUUID();
        when(fraudRepository.findById(fraud.getId())).thenReturn(Optional.of(fraud));

        service.linkCase(caseEvent(fraud.getId(), caseId));
        assertEquals(caseId, fraud.getCaseId());

        service.linkCase(caseEvent(fraud.getId(), UUID.randomUUID()));
        assertEquals(caseId, fraud.getCaseId());
    }

    @Test
    void caseLinkArrivingBeforeFraudIsStoredAndAppliedLater() {
        UUID fraudId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(fraudRepository.findById(fraudId)).thenReturn(Optional.empty());
        when(pendingLinkRepository.existsById(fraudId)).thenReturn(false);

        service.linkCase(caseEvent(fraudId, caseId));
        verify(pendingLinkRepository).save(any(PendingCaseLink.class));

        UUID txId = UUID.randomUUID();
        when(processedRepository.existsById(txId)).thenReturn(false);
        when(processedRepository.countByAccountIdAndTransactionCreatedAtBetween(eq(accountId), any(), any()))
                .thenReturn(0L);
        when(accountClient.findAccount(accountId)).thenReturn(Optional.of(new AccountClient.AccountView(accountId, customerId)));
        when(fraudRepository.nextFraudReference()).thenReturn("FRD-2");
        when(fraudRepository.saveAndFlush(any(FraudDetection.class))).thenAnswer(i -> i.getArgument(0));
        PendingCaseLink pending = new PendingCaseLink(UUID.randomUUID(), caseId);
        when(pendingLinkRepository.findById(any(UUID.class))).thenReturn(Optional.of(pending));

        service.processTransaction(tx(txId, "60000"));
        verify(pendingLinkRepository).delete(pending);
    }

    @Test
    void caseEventWithoutFraudIdIsIgnored() {
        service.linkCase(new CaseCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "CASE-1", "s", "HIGH",
                "MANUAL", customerId, null, null, Instant.now()));
        verifyNoInteractions(fraudRepository, pendingLinkRepository);
    }

    private CaseCreatedEvent caseEvent(UUID fraudId, UUID caseId) {
        return new CaseCreatedEvent(UUID.randomUUID(), caseId, "CASE-1", "Fraud alert", "HIGH",
                "FRAUD_DETECTION", customerId, accountId, fraudId, Instant.now());
    }
}
