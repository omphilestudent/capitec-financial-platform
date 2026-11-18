package za.co.capitec.customerservice.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerNameRequest(
        @NotBlank(message = "firstName is required") @Size(max = 100) String firstName,
        @NotBlank(message = "lastName is required") @Size(max = 100) String lastName) {
}
