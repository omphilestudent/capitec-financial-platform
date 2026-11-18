package za.co.capitec.transactionservice.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceResponse(
        UUID accountId,
        String currency,
        BigDecimal postedCredits,
        BigDecimal postedDebits,
        BigDecimal balance) {
}
