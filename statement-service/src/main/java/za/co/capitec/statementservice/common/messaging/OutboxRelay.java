package za.co.capitec.statementservice.common.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int maxAttempts;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.max-attempts:10}") int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.Status.PENDING, PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(new ProducerRecord<>(event.getTopic(), null, event.getMessageKey(), event.getPayload(),
                        List.of(
                                new RecordHeader("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8)),
                                new RecordHeader("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8)))))
                        .get(10, TimeUnit.SECONDS);
                event.markPublished();
                log.info("Published {} event {} to {} (key {})",
                        event.getEventType(), event.getId(), event.getTopic(), event.getMessageKey());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Outbox relay interrupted; remaining events will be retried");
                return;
            } catch (Exception exception) {
                event.recordFailure(exception.getMessage(), maxAttempts);
                log.error("Failed to publish {} event {} (attempt {} of {})",
                        event.getEventType(), event.getId(), event.getAttempts(), maxAttempts, exception);
            }
        }
    }
}
