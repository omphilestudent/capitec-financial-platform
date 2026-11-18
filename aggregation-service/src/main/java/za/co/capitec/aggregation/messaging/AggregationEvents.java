package za.co.capitec.aggregation.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AggregationEvents {

    public static final String TOPIC = "transaction.aggregated";
    public static final String EVENT_TYPE = "TransactionAggregated";

    private AggregationEvents() {
    }

    public record TransactionAggregatedEvent(
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
            LocalDateTime emittedAt) {
    }
}
