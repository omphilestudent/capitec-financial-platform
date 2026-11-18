package za.co.capitec.frauddetection.messaging.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraudDetectionValidatorTest {

    private TransactionCreatedEvent valid() {
        return new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "TXN-1", "DEBIT", new BigDecimal("10.00"), "ZAR", "PENDING", LocalDateTime.now());
    }

    @Test
    void validEventPasses() {
        assertNull(FraudDetectionValidator.validate(valid()));
    }

    @Test
    void rejectsMissingIdsAndBadValues() {
        assertNotNull(FraudDetectionValidator.validate(null));
        assertNotNull(FraudDetectionValidator.validate(new TransactionCreatedEvent(UUID.randomUUID(), null,
                UUID.randomUUID(), "T", "DEBIT", BigDecimal.ONE, "ZAR", "PENDING", LocalDateTime.now())));
        assertNotNull(FraudDetectionValidator.validate(new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "T", "DEBIT", BigDecimal.ZERO, "ZAR", "PENDING", LocalDateTime.now())));
        assertNotNull(FraudDetectionValidator.validate(new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "T", "REFUND", BigDecimal.ONE, "ZAR", "PENDING", LocalDateTime.now())));
        assertNotNull(FraudDetectionValidator.validate(new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "T", "DEBIT", BigDecimal.ONE, "zar", "PENDING", LocalDateTime.now())));
        assertNotNull(FraudDetectionValidator.validate(new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "T", "DEBIT", BigDecimal.ONE, "ZAR", "PENDING", null)));
    }
}
