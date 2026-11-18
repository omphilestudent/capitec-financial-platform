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

    @Mock
    private LedgerBalanceClient ledgerBalances;

    @InjectMocks
    private AccountService accountService;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(ledgerBalances.postedBalance(any())).thenReturn(java.math.BigDecimal.ZERO);
    }

    @Test
    void fundedViewKeepsOnlyPositiveLedgerBalancesAndStillReturnsTheOthers() {
        var positive = new za.co.capitec.accountservice.account.entity.Account(customerId, "1000000001", AccountType.SAVINGS);
        var zero = new za.co.capitec.accountservice.account.entity.Account(customerId, "1000000002", AccountType.CHEQUE);
        var negative = new za.co.capitec.accountservice.account.entity.Account(customerId, "1000000003", AccountType.CREDIT);
        for (var a : java.util.List.of(positive, zero, negative)) {
            org.springframework.test.util.ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        }
        when(customerServiceClient.customerExists(customerId)).thenReturn(true);
        when(accountRepository.findByCustomerId(customerId)).thenReturn(java.util.List.of(positive, zero, negative));
        when(ledgerBalances.postedBalance(positive.getId())).thenReturn(new java.math.BigDecimal("8000.00"));
        when(ledgerBalances.postedBalance(zero.getId())).thenReturn(new java.math.BigDecimal("0.00"));
        when(ledgerBalances.postedBalance(negative.getId())).thenReturn(new java.math.BigDecimal("-125.50"));

        var all = accountService.getAccountsByCustomer(customerId);
        var funded = accountService.getFundedAccountsByCustomer(customerId);

        assertEquals(3, all.size());
        assertEquals(1, funded.size());
        assertEquals("1000000001", funded.get(0).accountNumber());
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
