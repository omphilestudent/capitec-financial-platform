package za.co.capitec.frauddetection.messaging.event;

import java.math.BigDecimal;

/** Checks a transaction event before it is used. Returns the first problem, or null if the event is valid. */
public final class FraudDetectionValidator {

    private FraudDetectionValidator() {
    }

    public static String validate(TransactionCreatedEvent e) {
        if (e == null) return "event is missing";
        if (e.transactionId() == null) return "transactionId is missing";
        if (e.accountId() == null) return "accountId is missing";
        if (e.amount() == null || e.amount().compareTo(BigDecimal.ZERO) <= 0) return "amount must be greater than zero";
        if (e.currency() == null || !e.currency().matches("[A-Z]{3}")) return "currency must be a three-letter code";
        if (e.type() == null || (!e.type().equals("CREDIT") && !e.type().equals("DEBIT"))) return "type must be CREDIT or DEBIT";
        if (e.createdAt() == null) return "createdAt is missing";
        return null;
    }
}
