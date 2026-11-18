package za.co.capitec.statementservice.statement.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record GenerateStatementRequest(
        @NotNull(message = "accountId is required") UUID accountId,
        @NotNull(message = "periodType is required (MONTHLY or CUSTOM)") PeriodType periodType,
        Integer year,
        Integer month,
        LocalDate from,
        LocalDate to,
        String accountType) {

    public GenerateStatementRequest(UUID accountId, PeriodType periodType, Integer year, Integer month,
                                    LocalDate from, LocalDate to) {
        this(accountId, periodType, year, month, from, to, null);
    }

    public enum PeriodType { MONTHLY, CUSTOM }
}
