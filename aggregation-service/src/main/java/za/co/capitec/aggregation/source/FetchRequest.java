package za.co.capitec.aggregation.source;

import java.time.LocalDate;
import java.util.UUID;

public record FetchRequest(UUID customerId, UUID accountId, LocalDate from, LocalDate to, int count, long seed) {
}
