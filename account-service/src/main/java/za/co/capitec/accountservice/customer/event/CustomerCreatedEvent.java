package za.co.capitec.accountservice.customer.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The customer.events payload published by customer-service. Only the fields
 * this service needs are read; unknown fields are ignored.
 */
public record CustomerCreatedEvent(
        UUID eventId,
        UUID customerId,
        String firstName,
        String lastName,
        String email,
        String clientType,
        Instant occurredAt
) {}
