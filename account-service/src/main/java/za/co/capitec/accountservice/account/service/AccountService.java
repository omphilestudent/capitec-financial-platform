package za.co.capitec.accountservice.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitec.accountservice.account.dto.AccountResponse;
import za.co.capitec.accountservice.account.dto.CreateAccountRequest;
import za.co.capitec.accountservice.account.entity.Account;
import za.co.capitec.accountservice.account.repository.AccountRepository;
import za.co.capitec.accountservice.common.exception.AccountNotFoundException;
import za.co.capitec.accountservice.common.exception.CustomerNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerServiceClient customerServiceClient;
    private final AccountNumberGenerator accountNumberGenerator;

    public AccountService(
            AccountRepository accountRepository,
            CustomerServiceClient customerServiceClient,
            AccountNumberGenerator accountNumberGenerator
    ) {
        this.accountRepository = accountRepository;
        this.customerServiceClient = customerServiceClient;
        this.accountNumberGenerator = accountNumberGenerator;
    }

    public AccountResponse createAccount(CreateAccountRequest request) {

        if (!customerServiceClient.customerExists(request.customerId())) {
            throw new CustomerNotFoundException(
                    "Customer not found: " + request.customerId()
            );
        }

        String accountNumber = generateUniqueAccountNumber();

        Account account = new Account(
                request.customerId(),
                accountNumber,
                request.accountType()
        );

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomer(UUID customerId) {

        if (!customerServiceClient.customerExists(customerId)) {
            throw new CustomerNotFoundException(
                    "Customer not found: " + customerId
            );
        }

        return accountRepository.findByCustomerId(customerId)
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountId
                ));

        return AccountResponse.from(account);
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
}
