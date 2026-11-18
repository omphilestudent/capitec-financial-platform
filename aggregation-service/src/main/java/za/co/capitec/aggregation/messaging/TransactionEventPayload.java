package za.co.capitec.aggregation.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionEventPayload(
        UUID eventId,
        UUID transactionId,
        UUID accountId,
        String transactionReference,
        String type,
        BigDecimal amount,
        String currency,
        String status,
        String description,
        LocalDateTime createdAt) {
}
