package za.co.capitec.frauddetection.common.messaging;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaProperties kafkaProperties) {
        // The dead-letter record keeps the event as JSON. The main producer sends strings.
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", JsonSerializer.class);
        props.put("spring.json.add.type.headers", false);

        KafkaTemplate<Object, Object> deadLetterTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(deadLetterTemplate),
                new FixedBackOff(1000L, 3L)
        );
        // Invalid payloads cannot succeed on retry; send them to the dead-letter topic at once.
        handler.addNotRetryableExceptions(
                za.co.capitec.frauddetection.common.exception.InvalidEventException.class);
        return handler;
    }
}
