package za.co.capitec.financialplatform.account.dto;

import za.co.capitec.financialplatform.account.entity.AccountStatus;
import za.co.capitec.financialplatform.account.entity.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountResponse(

    UUID id,
    UUID customerId,
    String accountNumber,
    AccountType accountType,
    BigDecimal balance,
    String currency,
    AccountStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
){

}
