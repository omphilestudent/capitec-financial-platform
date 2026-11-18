package za.co.capitec.financialplatform.customer.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitec.financialplatform.customer.dto.CreateCustomerRequest;
import za.co.capitec.financialplatform.customer.dto.CustomerResponse;
import za.co.capitec.financialplatform.customer.exception.CustomerNotFoundException;
import za.co.capitec.financialplatform.customer.exception.DuplicateCustomerEmailException;
import za.co.capitec.financialplatform.customer.repository.CustomerRepository;
import za.co.capitec.financialplatform.entity.Customer;

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

    public CustomerResponse createCustomer(CreateCustomerRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (customerRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateCustomerEmailException(
                    "A customer with this email address already exists"
            );
        }

        String phoneNumber = request.phoneNumber() == null
                ? null
                : request.phoneNumber().trim();

        Customer customer = Customer.create(
                request.firstName().trim(),
                request.lastName().trim(),
                normalizedEmail,
                phoneNumber,
                request.clientType()
        );

        Customer savedCustomer = customerRepository.save(customer);

        return mapToResponse(savedCustomer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listCustomers() {

        return customerRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID customerId) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found with ID: " + customerId
                ));

        return mapToResponse(customer);
    }

    private CustomerResponse mapToResponse(Customer customer) {

        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getClientType(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
