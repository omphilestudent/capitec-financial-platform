package za.co.capitec.aggregation.source;

import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MockSpec(String externalId, LocalDateTime occurredAt, Direction direction, BigDecimal amount,
                       String description, String mcc, String status) {
}
