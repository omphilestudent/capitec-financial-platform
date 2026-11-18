package za.co.capitec.statementservice.statement.integration;

import org.junit.jupiter.api.Test;
import za.co.capitec.statementservice.common.exception.InvalidEventException;
import za.co.capitec.statementservice.statement.service.StatementWithdrawalService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionReplayTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID supersededFraudId = UUID.randomUUID();

    private String revisionJson() {
        return "{\"eventId\":\"" + eventId + "\",\"revisionId\":\"" + UUID.randomUUID()
                + "\",\"transactionId\":\"" + transactionId + "\",\"accountId\":\"" + accountId
                + "\",\"supersedesFraudId\":\"" + supersededFraudId
                + "\",\"revisedOutcome\":\"NOT_DETECTED\",\"ruleVersion\":\"velocity-window-bounded-v1\"}";
    }

    @Test
    void replayingTheSameRevisionEventAppliesEligibilityExactlyOnce() throws Exception {
        InboundEventRepository inbound = mock(InboundEventRepository.class);
        LedgerRepository ledger = mock(LedgerRepository.class);
        StatementIntegrationListeners listeners = new StatementIntegrationListeners(inbound, ledger,
                mock(StatementWithdrawalService.class));
        when(inbound.claim(eq(eventId), eq(IntegrationPayloads.FRAUD_REVISIONS_TOPIC)))
                .thenReturn(true, false, false);

        listeners.onFraudRevised(revisionJson());
        listeners.onFraudRevised(revisionJson());
        listeners.onFraudRevised(revisionJson());

        verify(ledger, times(1)).applyRevision(transactionId, accountId, supersededFraudId, "NOT_DETECTED");
        verify(inbound, times(3)).claim(eq(eventId), eq(IntegrationPayloads.FRAUD_REVISIONS_TOPIC));
    }

    @Test
    void malformedRevisionIsRejectedWithoutChangingEligibility() throws Exception {
        InboundEventRepository inbound = mock(InboundEventRepository.class);
        LedgerRepository ledger = mock(LedgerRepository.class);
        StatementIntegrationListeners listeners = new StatementIntegrationListeners(inbound, ledger,
                mock(StatementWithdrawalService.class));

        assertThrows(InvalidEventException.class, () -> listeners.onFraudRevised("{not json"));
        verify(ledger, never()).applyRevision(any(), any(), any(), anyString());
    }
}
