package za.co.capitec.aggregation.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record NormalizedTransaction(
        SourceSystem source,
        String externalId,
        UUID customerId,
        UUID accountId,
        Direction direction,
        BigDecimal amount,
        String currency,
        String description,
        String status,
        LocalDateTime occurredAt,
        Category category) {

    public enum Direction { CREDIT, DEBIT }
}
