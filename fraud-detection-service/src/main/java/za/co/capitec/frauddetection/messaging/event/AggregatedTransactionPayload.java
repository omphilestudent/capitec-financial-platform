package za.co.capitec.frauddetection.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AggregatedTransactionPayload(
        UUID eventId,
        UUID aggregatedTransactionId,
        String source,
        String externalId,
        UUID customerId,
        UUID accountId,
        String direction,
        BigDecimal amount,
        String currency,
        String category,
        String status,
        boolean counted,
        LocalDateTime occurredAt,
        LocalDateTime emittedAt
) {
    public static final String TOPIC = "transaction.aggregated";
    public static final String TRANSACTION_SERVICE = "TRANSACTION_SERVICE";
}
