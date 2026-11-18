package za.co.capitec.frauddetection.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionCreatedEvent(
        UUID eventId,
        UUID transactionId,
        UUID accountId,
        String transactionReference,
        String type,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime createdAt
) {
    public static final String TOPIC = "transaction.events";
    public static final String LEGACY_TOPIC = "transaction-events";
}
