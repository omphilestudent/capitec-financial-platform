package za.co.capitec.statementservice.statement.service;

public class InsufficientEligibleTransactionsException extends RuntimeException {

    private final int eligibleCount;

    public InsufficientEligibleTransactionsException(int eligibleCount) {
        super("A statement needs at least 2 eligible transactions in the selected period; this account has "
                + eligibleCount + ".");
        this.eligibleCount = eligibleCount;
    }

    public int getEligibleCount() {
        return eligibleCount;
    }
}
