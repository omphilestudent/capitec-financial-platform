
package za.co.capitec.customerservice.customer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitec.customerservice.customer.entity.Customer;
import za.co.capitec.customerservice.customer.entity.ClientType;
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

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
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

        return customerRepository.save(customer);
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
}
