package za.co.capitec.frauddetection.detection.rules;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** HIGH and CRITICAL warrant a case for investigation. */
    public boolean requiresInvestigation() {
        return this == HIGH || this == CRITICAL;
    }
}
