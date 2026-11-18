package za.co.capitec.frauddetection.detection.rules;

import org.springframework.stereotype.Component;
import za.co.capitec.frauddetection.config.FraudRulesProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a transaction against the configured rules. It only uses facts that
 * exist: the transaction amount and the number of transactions this account has
 * recorded in a time window. Each rule that fires is explained.
 */
@Component
public class FraudRuleEngine {

    public static final String HIGH_AMOUNT = "HIGH_AMOUNT";
    public static final String VELOCITY = "VELOCITY";
    public static final String CROSS_BORDER = "CROSS_BORDER";
    public static final String LARGE_EFT = "LARGE_EFT";

    private final FraudRulesProperties rules;

    public FraudRuleEngine(FraudRulesProperties rules) {
        this.rules = rules;
    }

    /**
     * @param amount            amount of the transaction under review
     * @param recentCount       transactions for the same account in the velocity window,
     *                          including this one
     */
    public Assessment evaluate(BigDecimal amount, long recentCount) {
        return evaluate(amount, recentCount, null, null);
    }

    public Assessment evaluate(BigDecimal amount, long recentCount, String currency, String description) {
        List<RuleResult> triggered = new ArrayList<>();

        FraudRulesProperties.HighAmount high = rules.highAmount();
        if (high.enabled() && amount.compareTo(high.threshold()) >= 0) {
            triggered.add(new RuleResult(HIGH_AMOUNT, high.weight(),
                    "Amount " + amount.toPlainString() + " is at or above the threshold of "
                            + high.threshold().toPlainString()));
        }

        FraudRulesProperties.Velocity velocity = rules.velocity();
        if (velocity.enabled() && recentCount > velocity.maxTransactions()) {
            triggered.add(new RuleResult(VELOCITY, velocity.weight(),
                    recentCount + " transactions for this account in the last "
                            + velocity.windowMinutes() + " minutes; the limit is "
                            + velocity.maxTransactions()));
        }

        FraudRulesProperties.CrossBorder crossBorder = rules.crossBorder();
        if (crossBorder != null && crossBorder.enabled() && currency != null && !"ZAR".equalsIgnoreCase(currency)) {
            triggered.add(new RuleResult(CROSS_BORDER, crossBorder.weight(),
                    "Transaction in " + currency.toUpperCase(java.util.Locale.ROOT) + ", not ZAR (international)"));
        }

        FraudRulesProperties.LargeEft eft = rules.largeEft();
        if (eft != null && eft.enabled() && description != null
                && description.toUpperCase(java.util.Locale.ROOT).contains("EFT")
                && amount.compareTo(eft.threshold()) >= 0) {
            triggered.add(new RuleResult(LARGE_EFT, eft.weight(),
                    "EFT of " + amount.toPlainString() + " is at or above the threshold of " + eft.threshold().toPlainString()));
        }

        int score = Math.min(100, triggered.stream().mapToInt(RuleResult::weight).sum());
        return new Assessment(score, levelFor(score), List.copyOf(triggered));
    }

    /** The start of the velocity window ending at {@code now}. */
    public LocalDateTime velocityWindowStart(LocalDateTime now) {
        return now.minus(Duration.ofMinutes(rules.velocity().windowMinutes()));
    }

    RiskLevel levelFor(int score) {
        FraudRulesProperties.Levels levels = rules.levels();
        if (score >= levels.criticalMinScore()) return RiskLevel.CRITICAL;
        if (score >= levels.highMinScore()) return RiskLevel.HIGH;
        if (score >= levels.mediumMinScore()) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}
