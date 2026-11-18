package za.co.capitec.frauddetection.detection.rules;

/** One rule that fired, with the reason in plain language. */
public record RuleResult(String ruleId, int weight, String explanation) {}
