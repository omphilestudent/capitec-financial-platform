package za.co.capitec.statementservice.statement.pdf;

import java.math.BigDecimal;
import java.util.List;

public record Ledger(BigDecimal openingBalance, BigDecimal closingBalance, BigDecimal totalCredits,
                     BigDecimal totalDebits, List<LedgerRow> rows, int excludedFailedCount) {
}
