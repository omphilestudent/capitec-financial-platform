package za.co.capitec.transactionservice.transaction.service;

/** account-service could not be reached or failed while the account was being checked. */
public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
