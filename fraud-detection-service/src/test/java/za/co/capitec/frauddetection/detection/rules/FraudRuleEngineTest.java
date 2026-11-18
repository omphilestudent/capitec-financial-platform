package za.co.capitec.frauddetection.detection.rules;

import org.junit.jupiter.api.Test;
import za.co.capitec.frauddetection.config.FraudRulesProperties;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FraudRuleEngineTest {

    private final FraudRuleEngine engine = new FraudRuleEngine(new FraudRulesProperties(
            new FraudRulesProperties.HighAmount(true, new BigDecimal("50000"), 70),
            new FraudRulesProperties.Velocity(true, 5, 10, 50),
            new FraudRulesProperties.Levels(30, 60, 80),
            new FraudRulesProperties.CrossBorder(false, 60),
            new FraudRulesProperties.LargeEft(false, new BigDecimal("20000"), 65)
    ));

    @Test
    void ordinaryTransactionIsNotDetected() {
        Assessment a = engine.evaluate(new BigDecimal("250.00"), 1);
        assertFalse(a.detected());
        assertEquals(0, a.riskScore());
        assertEquals(RiskLevel.LOW, a.riskLevel());
    }

    @Test
    void highAmountAloneIsHighRiskAndNeedsInvestigation() {
        Assessment a = engine.evaluate(new BigDecimal("50000.00"), 1);
        assertTrue(a.detected());
        assertEquals(FraudRuleEngine.HIGH_AMOUNT, a.primaryRule().ruleId());
        assertEquals(70, a.riskScore());
        assertEquals(RiskLevel.HIGH, a.riskLevel());
        assertTrue(a.riskLevel().requiresInvestigation());
    }

    @Test
    void amountJustBelowThresholdIsNotDetected() {
        assertFalse(engine.evaluate(new BigDecimal("49999.99"), 1).detected());
    }

    @Test
    void velocityLimitIsExclusive() {
        // Five transactions is the allowed maximum; the sixth is the first to trigger.
        assertFalse(engine.evaluate(new BigDecimal("100"), 5).detected());
        Assessment a = engine.evaluate(new BigDecimal("100"), 6);
        assertEquals(FraudRuleEngine.VELOCITY, a.primaryRule().ruleId());
        // Velocity alone is MEDIUM: recorded as a detection, but it does not open a case.
        assertEquals(50, a.riskScore());
        assertEquals(RiskLevel.MEDIUM, a.riskLevel());
        assertFalse(a.riskLevel().requiresInvestigation());
    }

    @Test
    void bothRulesCombineAndCapAtOneHundred() {
        Assessment a = engine.evaluate(new BigDecimal("60000"), 9);
        assertEquals(2, a.triggered().size());
        assertEquals(100, a.riskScore());
        assertEquals(RiskLevel.CRITICAL, a.riskLevel());
        // The primary rule is the one with the largest weight.
        assertEquals(FraudRuleEngine.HIGH_AMOUNT, a.primaryRule().ruleId());
    }

    @Test
    void explanationNamesTheRealFigures() {
        Assessment a = engine.evaluate(new BigDecimal("50000"), 1);
        assertTrue(a.primaryRule().explanation().contains("50000"));
    }
}
