package za.co.capitec.accountservice.account.dto;

import za.co.capitec.accountservice.account.entity.Account;
import za.co.capitec.accountservice.account.entity.AccountStatus;
import za.co.capitec.accountservice.account.entity.AccountType;

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
) {

    public static AccountResponse from(Account account, BigDecimal ledgerBalance) {
        return new AccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getAccountNumber(),
                account.getAccountType(),
                ledgerBalance,
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}