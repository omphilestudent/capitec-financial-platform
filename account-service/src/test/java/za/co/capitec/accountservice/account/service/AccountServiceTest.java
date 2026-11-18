package za.co.capitec.accountservice.account.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import za.co.capitec.accountservice.account.dto.CreateAccountRequest;
import za.co.capitec.accountservice.account.entity.Account;
import za.co.capitec.accountservice.account.entity.AccountType;
import za.co.capitec.accountservice.common.messaging.OutboxWriter;
import za.co.capitec.accountservice.account.repository.AccountRepository;
import za.co.capitec.accountservice.common.exception.AccountNotFoundException;
import za.co.capitec.accountservice.common.exception.CustomerNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerServiceClient customerServiceClient;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private OutboxWriter outboxWriter;

    @InjectMocks
    private AccountService accountService;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
    }

    @Test
    void shouldCreateAccountForExistingCustomer() {

        CreateAccountRequest request =
                new CreateAccountRequest(customerId, AccountType.SAVINGS);

        when(customerServiceClient.customerExists(customerId))
                .thenReturn(true);

        when(accountNumberGenerator.generate())
                .thenReturn("12345678901");

        when(accountRepository.existsByAccountNumber("12345678901"))
                .thenReturn(false);

        // JPA assigns the ID on persist; the stub does the same.
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> {
                    Account saved = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(
                            saved, "id", UUID.randomUUID());
                    return saved;
                });

        var response = accountService.createAccount(request);

        assertNotNull(response);
        assertEquals(customerId, response.customerId());
        assertEquals("12345678901", response.accountNumber());
        assertEquals(AccountType.SAVINGS, response.accountType());

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void shouldRejectAccountCreationForUnknownCustomer() {

        CreateAccountRequest request =
                new CreateAccountRequest(customerId, AccountType.SAVINGS);

        when(customerServiceClient.customerExists(customerId))
                .thenReturn(false);

        assertThrows(
                CustomerNotFoundException.class,
                () -> accountService.createAccount(request)
        );

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldRetrieveExistingAccount() {

        UUID accountId = UUID.randomUUID();

        Account account = new Account(
                customerId,
                "12345678901",
                AccountType.CHEQUE
        );

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.of(account));

        var response = accountService.getAccountById(accountId);

        assertNotNull(response);
        assertEquals(customerId, response.customerId());
        assertEquals(AccountType.CHEQUE, response.accountType());
    }

    @Test
    void shouldThrowExceptionWhenAccountDoesNotExist() {

        UUID accountId = UUID.randomUUID();

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getAccountById(accountId)
        );
    }
}
