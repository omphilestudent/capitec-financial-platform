package za.co.capitec.financialplatform.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import za.co.capitec.financialplatform.entity.ClientType;

public record CreateCustomerRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters")
        String phoneNumber,

        @NotNull(message = "Client type is required")
        ClientType clientType

) {
}
