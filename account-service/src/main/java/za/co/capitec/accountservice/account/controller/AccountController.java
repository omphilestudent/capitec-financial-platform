package za.co.capitec.accountservice.account.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import za.co.capitec.accountservice.account.dto.AccountResponse;
import za.co.capitec.accountservice.account.dto.CreateAccountRequest;
import za.co.capitec.accountservice.account.service.AccountService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request
    ) {
        AccountResponse response = accountService.createAccount(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByCustomer(
            @PathVariable UUID customerId
    ) {
        return ResponseEntity.ok(
                accountService.getAccountsByCustomer(customerId)
        );
    }
}