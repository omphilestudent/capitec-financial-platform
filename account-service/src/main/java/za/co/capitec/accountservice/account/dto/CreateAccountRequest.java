package za.co.capitec.accountservice.account.dto;

import jakarta.validation.constraints.NotNull;
import za.co.capitec.accountservice.account.entity.AccountType;

import java.util.UUID;

public record CreateAccountRequest(

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Account type is required")
        AccountType accountType

) {}