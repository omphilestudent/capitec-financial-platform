
package za.co.capitec.customerservice.customer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitec.customerservice.customer.entity.Customer;
import za.co.capitec.customerservice.customer.entity.ClientType;
import za.co.capitec.customerservice.common.messaging.OutboxWriter;
import za.co.capitec.customerservice.customer.event.CustomerCreatedEvent;
import java.time.Instant;
import za.co.capitec.customerservice.customer.repository.CustomerRepository;

import za.co.capitec.customerservice.customer.exception.CustomerNotFoundException;
import za.co.capitec.customerservice.customer.exception.DuplicateCustomerException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OutboxWriter outboxWriter;

    public CustomerService(
            CustomerRepository customerRepository,
            OutboxWriter outboxWriter) {
        this.customerRepository = customerRepository;
        this.outboxWriter = outboxWriter;
    }

    public Customer createCustomer(
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            ClientType clientType
    ) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (customerRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateCustomerException(
                    "A customer with this email already exists"
            );
        }

        Customer customer = new Customer(
                firstName.trim(),
                lastName.trim(),
                normalizedEmail,
                phoneNumber == null || phoneNumber.isBlank()
                        ? null : phoneNumber.trim(),
                clientType
        );

        Customer saved = customerRepository.save(customer);

        // Written to the outbox in the same transaction as the customer.
        UUID eventId = UUID.randomUUID();
        outboxWriter.write(
                eventId,
                CustomerCreatedEvent.TOPIC,
                saved.getId().toString(),
                CustomerCreatedEvent.EVENT_TYPE,
                new CustomerCreatedEvent(
                        eventId,
                        saved.getId(),
                        saved.getFirstName(),
                        saved.getLastName(),
                        saved.getEmail(),
                        saved.getClientType(),
                        Instant.now()
                )
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + id
                ));
    }

    public Customer updateName(UUID customerId, String firstName, String lastName) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new za.co.capitec.customerservice.customer.exception.CustomerNotFoundException(
                        "Customer not found: " + customerId));
        customer.rename(firstName.trim(), lastName.trim());
        Customer saved = customerRepository.save(customer);
        java.util.UUID eventId = java.util.UUID.randomUUID();
        outboxWriter.write(eventId, za.co.capitec.customerservice.customer.event.CustomerNameUpdatedEvent.TOPIC,
                saved.getId().toString(),
                za.co.capitec.customerservice.customer.event.CustomerNameUpdatedEvent.EVENT_TYPE,
                new za.co.capitec.customerservice.customer.event.CustomerNameUpdatedEvent(eventId, saved.getId(),
                        saved.getFirstName(), saved.getLastName(), java.time.LocalDateTime.now()));
        return saved;
    }
}
