package za.co.capitec.customerservice.customer.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerNameUpdatedEvent(UUID eventId, UUID customerId, String firstName, String lastName,
                                       LocalDateTime occurredAt) {

    public static final String TOPIC = "customer.updates";
    public static final String EVENT_TYPE = "CustomerNameUpdated";
}
