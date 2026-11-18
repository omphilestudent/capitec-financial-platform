package za.co.capitec.transactionservice.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published to {@link #TOPIC} after a transaction is persisted.
 * The message key is the transaction ID.
 */
public record TransactionCreatedEvent(
        UUID eventId,
        UUID transactionId,
        UUID accountId,
        String transactionReference,
        String type,
        BigDecimal amount,
        String currency,
        String status,
        String description,
        LocalDateTime createdAt
) {
    public static final String TOPIC = "transaction.events";
    public static final String LEGACY_TOPIC = "transaction-events";
    public static final String EVENT_TYPE = "TransactionCreated";
}
