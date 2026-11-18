package za.co.capitec.statementservice.statement.web.dto;

import za.co.capitec.statementservice.statement.domain.Statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record StatementResponse(
        UUID id,
        String statementReference,
        UUID customerId,
        UUID accountId,
        String accountNumber,
        String accountType,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        String currency,
        String status,
        String failureReason,
        LocalDateTime generatedAt,
        int versionNumber,
        String versionStatus,
        UUID supersededBy,
        String regenerationReason) {

    public static StatementResponse from(Statement s) {
        return new StatementResponse(
                s.getId(), s.getStatementReference(), s.getCustomerId(), s.getAccountId(),
                s.getAccountNumber(), s.getAccountType(), s.getPeriodStart(), s.getPeriodEnd(),
                s.getOpeningBalance(), s.getClosingBalance(), s.getTotalCredits(), s.getTotalDebits(),
                s.getCurrency(), s.getStatus().name(), s.getFailureReason(), s.getGeneratedAt(),
                s.getVersionNumber(), s.getVersionStatus(), s.getSupersededBy(), s.getRegenerationReason());
    }
}
