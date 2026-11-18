package za.co.capitec.statementservice.statement.pdf;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerRow(LocalDateTime createdAt, String reference, String description, String type,
                        BigDecimal debit, BigDecimal credit, BigDecimal runningBalance) {
}
