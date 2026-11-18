
package za.co.capitec.customerservice.customer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import za.co.capitec.customerservice.customer.entity.ClientType;
import za.co.capitec.customerservice.customer.entity.Customer;
import za.co.capitec.customerservice.customer.exception.DuplicateCustomerException;
import za.co.capitec.customerservice.common.messaging.OutboxWriter;
import za.co.capitec.customerservice.customer.exception.CustomerNotFoundException;
import za.co.capitec.customerservice.customer.repository.CustomerRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OutboxWriter outboxWriter;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, outboxWriter);
    }

    @Test
    void shouldCreateCustomerSuccessfully() {
        when(customerRepository.existsByEmail("thabo@example.com"))
                .thenReturn(false);

        // JPA assigns the ID on persist; the stub does the same.
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer saved = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(
                            saved, "id", UUID.randomUUID());
                    return saved;
                });

        Customer customer = customerService.createCustomer(
                "Thabo",
                "Mokoena",
                "thabo@example.com",
                "0821234567",
                ClientType.INDIVIDUAL
        );

        assertNotNull(customer);
        assertEquals("Thabo", customer.getFirstName());
        assertEquals("thabo@example.com", customer.getEmail());

        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        when(customerRepository.existsByEmail("thabo@example.com"))
                .thenReturn(true);

        assertThrows(DuplicateCustomerException.class, () ->
                customerService.createCustomer(
                        "Thabo",
                        "Mokoena",
                        "thabo@example.com",
                        "0821234567",
                        ClientType.INDIVIDUAL
                )
        );

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void shouldRetrieveCustomerById() {
        UUID customerId = UUID.randomUUID();

        Customer customer = new Customer(
                "Thabo",
                "Mokoena",
                "thabo@example.com",
                "0821234567",
                ClientType.INDIVIDUAL
        );

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        Customer result = customerService.getCustomerById(customerId);

        assertNotNull(result);
        assertEquals("Thabo", result.getFirstName());
    }

    @Test
    void shouldThrowExceptionWhenCustomerDoesNotExist() {
        UUID customerId = UUID.randomUUID();

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () ->
                customerService.getCustomerById(customerId)
        );
    }
}
