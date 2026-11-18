package za.co.capitec.accountservice.common.messaging;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the outbox relay's scheduled polling. */
@Configuration
@EnableScheduling
public class MessagingConfig {
}
