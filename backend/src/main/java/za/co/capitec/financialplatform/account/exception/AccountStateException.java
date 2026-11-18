package za.co.capitec.financialplatform.account.exception;

/** Thrown when an operation is not allowed in the account's current state. */
public class AccountStateException extends RuntimeException {

    public AccountStateException(String message) {
        super(message);
    }
}
