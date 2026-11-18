package za.co.capitec.statementservice.statement.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionView(UUID id, UUID accountId, String transactionReference, String type,
                              BigDecimal amount, String currency, String description, String status,
                              LocalDateTime createdAt) {
}
