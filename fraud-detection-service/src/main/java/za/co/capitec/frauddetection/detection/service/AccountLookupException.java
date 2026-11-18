package za.co.capitec.frauddetection.detection.service;

/** account-service could not answer, so the transaction cannot be assessed yet. Retried. */
public class AccountLookupException extends RuntimeException {
    public AccountLookupException(String message, Throwable cause) { super(message, cause); }
}
