package za.co.capitec.financialplatform.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitec.financialplatform.account.dto.AccountResponse;
import za.co.capitec.financialplatform.account.dto.CreateAccountRequest;
import za.co.capitec.financialplatform.account.dto.UpdateAccountRequest;
import za.co.capitec.financialplatform.account.entity.Account;
import za.co.capitec.financialplatform.account.entity.AccountStatus;
import za.co.capitec.financialplatform.account.exception.AccountNotFoundException;
import za.co.capitec.financialplatform.account.exception.AccountStateException;
import za.co.capitec.financialplatform.account.repository.AccountRepository;
import za.co.capitec.financialplatform.customer.exception.CustomerNotFoundException;
import za.co.capitec.financialplatform.customer.repository.CustomerRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            AccountNumberGenerator accountNumberGenerator) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.accountNumberGenerator = accountNumberGenerator;
    }

    public AccountResponse createAccount(CreateAccountRequest request) {

        if (!customerRepository.existsById(request.customerId())) {
            throw new CustomerNotFoundException(
                    "Customer not found with ID: " + request.customerId()
            );
        }

        String accountNumber = generateUniqueAccountNumber();

        Account account = new Account(
                request.customerId(),
                accountNumber,
                request.accountType()
        );

        Account savedAccount = accountRepository.save(account);

        return mapToResponse(savedAccount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomer(UUID customerId) {

        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException(
                    "Customer not found with ID: " + customerId
            );
        }

        return accountRepository.findByCustomerId(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        return mapToResponse(findAccount(accountId));
    }

    public AccountResponse updateAccount(UUID accountId, UpdateAccountRequest request) {

        Account account = findAccount(accountId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountStateException("A closed account cannot be updated");
        }

        if (request.accountType() != null) {
            account.setAccountType(request.accountType());
        }

        if (request.status() != null) {
            if (request.status() == AccountStatus.CLOSED) {
                throw new AccountStateException("Use DELETE to close an account");
            }
            account.setStatus(request.status());
        }

        return mapToResponse(accountRepository.save(account));
    }

    /**
     * Removes an account from service by closing it. The row is kept so the
     * account history survives. An account can only be closed once its
     * balance is zero.
     */
    public void closeAccount(UUID accountId) {

        Account account = findAccount(accountId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountStateException("Account is already closed");
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountStateException(
                    "Account balance must be zero before the account can be closed"
            );
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    private Account findAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found with ID: " + accountId
                ));
    }

    private String generateUniqueAccountNumber() {

        for (int attempt = 0; attempt < 5; attempt++) {
            String accountNumber = accountNumberGenerator.generate();

            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a unique account number"
        );
    }

    private AccountResponse mapToResponse(Account account) {

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
