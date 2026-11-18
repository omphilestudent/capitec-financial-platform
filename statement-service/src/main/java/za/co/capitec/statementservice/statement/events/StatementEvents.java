package za.co.capitec.statementservice.statement.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class StatementEvents {

    public static final String TOPIC = "statement.events";

    private StatementEvents() {
    }

    public record StatementGeneratedEvent(UUID eventId, UUID statementId, String statementReference,
                                          UUID customerId, UUID accountId, LocalDate periodStart,
                                          LocalDate periodEnd, BigDecimal closingBalance, LocalDateTime occurredAt) {
        public static final String EVENT_TYPE = "StatementGenerated";
    }

    public record StatementGenerationFailedEvent(UUID eventId, UUID statementId, UUID customerId,
                                                 UUID accountId, LocalDate periodStart, LocalDate periodEnd,
                                                 String reason, LocalDateTime occurredAt) {
        public static final String EVENT_TYPE = "StatementGenerationFailed";
    }

    public record StatementDownloadRequestedEvent(UUID eventId, UUID statementId, UUID customerId,
                                                  LocalDateTime linkExpiresAt, LocalDateTime occurredAt) {
        public static final String EVENT_TYPE = "StatementDownloadRequested";
    }

    public record StatementDownloadedEvent(UUID eventId, UUID statementId, UUID customerId,
                                           LocalDateTime occurredAt) {
        public static final String EVENT_TYPE = "StatementDownloaded";
    }
}
