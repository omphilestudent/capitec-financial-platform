package za.co.capitec.transactionservice.transaction.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import za.co.capitec.transactionservice.transaction.dto.CreateTransactionRequest;
import za.co.capitec.transactionservice.transaction.dto.TransactionFilter;
import za.co.capitec.transactionservice.transaction.dto.TransactionResponse;
import za.co.capitec.transactionservice.transaction.dto.TransactionSummaryResponse;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;
import za.co.capitec.transactionservice.transaction.service.TransactionService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(
            @Valid @RequestBody CreateTransactionRequest request) {
        return transactionService.createTransaction(request);
    }

    @GetMapping
    public List<TransactionResponse> getRecentTransactions() {
        return transactionService.getRecentTransactions();
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransaction(@PathVariable UUID id) {
        return transactionService.getTransactionById(id);
    }

    @GetMapping("/account/{accountId}")
    public List<TransactionResponse> getTransactionsByAccount(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type) {
        return transactionService.getTransactionsByAccount(accountId, new TransactionFilter(from, to, type));
    }

    @GetMapping("/account/{accountId}/summary")
    public TransactionSummaryResponse getTransactionSummary(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type) {
        return transactionService.getTransactionSummary(accountId, new TransactionFilter(from, to, type));
    }
}
