package za.co.capitec.accountservice.customer.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitec.accountservice.customer.entity.CustomerRef;
import za.co.capitec.accountservice.customer.repository.CustomerRefRepository;

/**
 * Stores each customer announced on customer.events. Saving is idempotent, so
 * a redelivered event just rewrites the same row.
 */
@Component
public class CustomerCreatedListener {

    public static final String TOPIC = "customer.events";

    private static final Logger log = LoggerFactory.getLogger(CustomerCreatedListener.class);

    private final CustomerRefRepository customerRefRepository;

    public CustomerCreatedListener(CustomerRefRepository customerRefRepository) {
        this.customerRefRepository = customerRefRepository;
    }

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onCustomerCreated(CustomerCreatedEvent event) {
        customerRefRepository.save(new CustomerRef(
                event.customerId(),
                event.firstName(),
                event.lastName(),
                event.email(),
                event.clientType()
        ));
        log.info("Recorded customer {} from {}", event.customerId(), TOPIC);
    }
}
