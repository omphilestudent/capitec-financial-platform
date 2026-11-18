package za.co.capitec.accountservice.common.exception;

public class LedgerUnavailableException extends RuntimeException {

    public LedgerUnavailableException(String message) {
        super(message);
    }
}
