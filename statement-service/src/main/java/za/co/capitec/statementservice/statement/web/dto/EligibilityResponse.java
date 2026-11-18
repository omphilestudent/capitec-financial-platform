package za.co.capitec.statementservice.statement.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EligibilityResponse(UUID accountId, LocalDate from, LocalDate to, int eligibleCount,
                                  int excludedCount, boolean canGenerate) {
}
