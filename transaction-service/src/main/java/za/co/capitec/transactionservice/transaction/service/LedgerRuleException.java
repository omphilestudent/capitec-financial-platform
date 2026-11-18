package za.co.capitec.transactionservice.transaction.service;

public class LedgerRuleException extends RuntimeException {

    public LedgerRuleException(String message) {
        super(message);
    }
}
