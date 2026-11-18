package za.co.capitec.financialplatform.customer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import za.co.capitec.financialplatform.entity.ClientType;

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
}
