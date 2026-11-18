package za.co.capitec.financialplatform.account.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import za.co.capitec.financialplatform.account.dto.AccountResponse;
import za.co.capitec.financialplatform.account.dto.CreateAccountRequest;
import za.co.capitec.financialplatform.account.dto.UpdateAccountRequest;
import za.co.capitec.financialplatform.account.service.AccountService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        AccountResponse account = accountService.createAccount(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.id())
                .toUri();

        return ResponseEntity.created(location).body(account);
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<List<AccountResponse>> listAccountsForCustomer(
            @PathVariable UUID customerId) {

        return ResponseEntity.ok(accountService.getAccountsByCustomer(customerId));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @PutMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable UUID id,
            @RequestBody UpdateAccountRequest request) {

        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }

    /** Removes the account from service by closing it. The record is kept for audit. */
    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> closeAccount(@PathVariable UUID id) {
        accountService.closeAccount(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
