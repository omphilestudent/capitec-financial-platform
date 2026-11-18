package za.co.capitec.transactionservice.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import za.co.capitec.transactionservice.transaction.entity.EntryKind;

import java.math.BigDecimal;
import java.util.UUID;

public record FundingRequest(
        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Entry kind is required (OPENING_BALANCE or FUNDING)")
        EntryKind entryKind,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least R0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most two decimal places")
        BigDecimal amount,

        @Pattern(regexp = "ZAR", message = "Only ZAR funding is supported")
        String currency,

        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 100, message = "idempotencyKey cannot exceed 100 characters")
        String idempotencyKey,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
) {}
