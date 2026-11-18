package za.co.capitec.frauddetection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Fraud rule settings, read from application.properties (prefix fraud.rules).
 * Nothing about thresholds or weights is hard-coded in the rule engine.
 */
@ConfigurationProperties(prefix = "fraud.rules")
public record FraudRulesProperties(
        HighAmount highAmount,
        Velocity velocity,
        Levels levels
) {

    /** A single transaction at or above the threshold. */
    public record HighAmount(boolean enabled, BigDecimal threshold, int weight) {}

    /** More than maxTransactions for one account within windowMinutes. */
    public record Velocity(boolean enabled, int maxTransactions, int windowMinutes, int weight) {}

    /** Score boundaries. A score at or above a level's minimum is that level. */
    public record Levels(int mediumMinScore, int highMinScore, int criticalMinScore) {}
}
