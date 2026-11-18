package za.co.capitec.aggregation.aggregation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.aggregation.account.AccountClient;
import za.co.capitec.aggregation.categorisation.Categoriser;
import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;
import za.co.capitec.aggregation.domain.SourceSystem;
import za.co.capitec.aggregation.messaging.TransactionEventPayload;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class TransactionEventsListener {

    static final String TOPIC = "transaction.events";

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final JdbcTemplate jdbc;
    private final AccountClient accounts;
    private final AggregationService aggregation;
    private final Categoriser categoriser;

    public TransactionEventsListener(JdbcTemplate jdbc, AccountClient accounts,
                                     AggregationService aggregation, Categoriser categoriser) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.aggregation = aggregation;
        this.categoriser = categoriser;
    }

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onTransactionEvent(String json) throws JsonProcessingException {
        TransactionEventPayload event = JSON.readValue(json, TransactionEventPayload.class);
        if (event.eventId() == null || event.accountId() == null || event.amount() == null || event.type() == null) {
            throw new IllegalArgumentException("Transaction event is missing required fields");
        }
        int claimed = jdbc.update(
                "INSERT INTO processed_inbound_events (event_id, source, processed_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (event_id) DO NOTHING",
                event.eventId(), SourceSystem.TRANSACTION_SERVICE.name(), LocalDateTime.now());
        if (claimed == 0) {
            return;
        }

        var account = accounts.require(event.accountId());
        Direction direction = Direction.valueOf(event.type().toUpperCase(Locale.ROOT));
        NormalizedTransaction tx = new NormalizedTransaction(
                SourceSystem.TRANSACTION_SERVICE,
                event.transactionId().toString(),
                account.customerId(),
                event.accountId(),
                direction,
                event.amount(),
                event.currency(),
                event.description(),
                event.status(),
                event.createdAt(),
                categoriser.categorise(event.description(), null, direction));
        aggregation.ingest(tx);
    }
}
