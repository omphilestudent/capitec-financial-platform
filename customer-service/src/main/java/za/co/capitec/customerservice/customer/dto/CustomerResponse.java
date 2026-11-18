package za.co.capitec.customerservice.customer.dto;

import za.co.capitec.customerservice.customer.entity.ClientType;
import za.co.capitec.customerservice.customer.entity.Customer;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        ClientType clientType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CustomerResponse from(Customer customer) {
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