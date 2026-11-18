package za.co.capitec.transactionservice.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionRequest(

        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @NotNull(message = "Transaction amount is required")
        @DecimalMin(value = "0.01", message = "Transaction amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must contain exactly 3 characters")
        String currency,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
) {}
