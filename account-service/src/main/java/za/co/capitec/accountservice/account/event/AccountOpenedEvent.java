package za.co.capitec.accountservice.account.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@link #TOPIC} when an account is opened. The message key is the
 * account ID.
 */
public record AccountOpenedEvent(
        UUID eventId,
        UUID accountId,
        UUID customerId,
        String accountNumber,
        String accountType,
        Instant occurredAt
) {
    public static final String TOPIC = "account.events";
    public static final String EVENT_TYPE = "AccountOpened";
}
