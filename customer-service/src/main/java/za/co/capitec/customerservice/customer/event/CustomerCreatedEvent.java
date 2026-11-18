package za.co.capitec.customerservice.customer.event;

import za.co.capitec.customerservice.customer.entity.ClientType;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@link #TOPIC} when a customer is created. The message key is the
 * customer ID. Other services keep their own copy of the fields they need.
 */
public record CustomerCreatedEvent(
        UUID eventId,
        UUID customerId,
        String firstName,
        String lastName,
        String email,
        ClientType clientType,
        Instant occurredAt
) {
    public static final String TOPIC = "customer.events";
    public static final String EVENT_TYPE = "CustomerCreated";
}
