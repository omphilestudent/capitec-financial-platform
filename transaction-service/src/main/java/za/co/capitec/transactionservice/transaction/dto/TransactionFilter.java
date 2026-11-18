package za.co.capitec.transactionservice.transaction.dto;

import za.co.capitec.transactionservice.transaction.entity.TransactionType;

import java.time.LocalDate;

public record TransactionFilter(LocalDate from, LocalDate to, TransactionType type) {
}
