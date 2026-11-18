package za.co.capitec.statementservice.common.messaging;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import za.co.capitec.statementservice.common.exception.InvalidEventException;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", StringSerializer.class);

        KafkaTemplate<Object, Object> deadLetterTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(deadLetterTemplate),
                new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
