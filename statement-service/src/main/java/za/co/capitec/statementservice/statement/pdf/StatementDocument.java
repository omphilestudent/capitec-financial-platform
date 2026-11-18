package za.co.capitec.statementservice.statement.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StatementDocument(
        String statementReference,
        String customerName,
        String customerId,
        String accountNumber,
        String accountType,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currency,
        LocalDateTime generatedAt,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        int excludedFailedCount,
        List<LedgerRow> rows) {
}
