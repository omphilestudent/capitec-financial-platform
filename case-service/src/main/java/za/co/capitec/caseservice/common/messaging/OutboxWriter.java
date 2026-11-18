package za.co.capitec.caseservice.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores an event in the outbox. Must be called inside the business transaction,
 * so the event is saved only if the business change is saved.
 */
@Component
public class OutboxWriter {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final OutboxEventRepository outboxEventRepository;

    public OutboxWriter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(java.util.UUID eventId, String topic, String messageKey,
                      String eventType, Object event) {
        outboxEventRepository.save(new OutboxEvent(
                eventId, topic, messageKey, eventType, toJson(event)
        ));
    }

    private static String toJson(Object event) {
        try {
            return JSON.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Event could not be serialised", exception);
        }
    }
}
