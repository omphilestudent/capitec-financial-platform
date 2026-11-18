package za.co.capitec.transactionservice.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionSummaryResponse(
        LocalDate from,
        LocalDate to,
        String type,
        long transactionCount,
        BigDecimal creditTotal,
        BigDecimal debitTotal,
        BigDecimal netTotal
) {
}
