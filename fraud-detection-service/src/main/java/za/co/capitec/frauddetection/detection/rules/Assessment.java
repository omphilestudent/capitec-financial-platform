package za.co.capitec.frauddetection.detection.rules;

import java.util.List;

/** The outcome of evaluating one transaction. Empty rules means nothing was detected. */
public record Assessment(int riskScore, RiskLevel riskLevel, List<RuleResult> triggered) {

    public boolean detected() {
        return !triggered.isEmpty();
    }

    /** The rule with the largest weight. It is the detection's primary rule. */
    public RuleResult primaryRule() {
        return triggered.stream()
                .max((a, b) -> Integer.compare(a.weight(), b.weight()))
                .orElseThrow();
    }
}
