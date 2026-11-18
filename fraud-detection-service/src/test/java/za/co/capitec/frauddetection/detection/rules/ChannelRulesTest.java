package za.co.capitec.frauddetection.detection.rules;

import org.junit.jupiter.api.Test;
import za.co.capitec.frauddetection.config.FraudRulesProperties;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ChannelRulesTest {

    private final FraudRuleEngine engine = new FraudRuleEngine(new FraudRulesProperties(
            new FraudRulesProperties.HighAmount(true, new BigDecimal("50000"), 70),
            new FraudRulesProperties.Velocity(true, 5, 10, 50),
            new FraudRulesProperties.Levels(30, 60, 80),
            new FraudRulesProperties.CrossBorder(true, 60),
            new FraudRulesProperties.LargeEft(true, new BigDecimal("20000"), 65)));

    private boolean fired(Assessment a, String rule) {
        return a.triggered().stream().anyMatch(r -> r.ruleId().equals(rule));
    }

    @Test
    void foreignCurrencyTriggersCrossBorder() {
        assertTrue(fired(engine.evaluate(new BigDecimal("300.00"), 1, "USD", "Online purchase"), FraudRuleEngine.CROSS_BORDER));
        assertFalse(fired(engine.evaluate(new BigDecimal("300.00"), 1, "ZAR", "Online purchase"), FraudRuleEngine.CROSS_BORDER));
    }

    @Test
    void largeEftTriggersLargeEftButSmallEftDoesNot() {
        assertTrue(fired(engine.evaluate(new BigDecimal("25000.00"), 1, "ZAR", "EFT payment to supplier"), FraudRuleEngine.LARGE_EFT));
        assertFalse(fired(engine.evaluate(new BigDecimal("5000.00"), 1, "ZAR", "EFT payment to supplier"), FraudRuleEngine.LARGE_EFT));
    }

    @Test
    void largeNonEftTransactionIsNotAnEftFlag() {
        assertFalse(fired(engine.evaluate(new BigDecimal("25000.00"), 1, "ZAR", "Salary deposit"), FraudRuleEngine.LARGE_EFT));
    }
}
