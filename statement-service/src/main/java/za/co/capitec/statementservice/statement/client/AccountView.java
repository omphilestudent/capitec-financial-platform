package za.co.capitec.statementservice.statement.client;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(UUID id, UUID customerId, String accountNumber, String accountType,
                          BigDecimal balance, String currency, String status) {
}
