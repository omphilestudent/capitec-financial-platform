package za.co.capitec.transactionservice.transaction.service;

public class InvalidTransactionFilterException extends RuntimeException {

    public InvalidTransactionFilterException(String message) {
        super(message);
    }
}
