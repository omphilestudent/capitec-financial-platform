package za.co.capitec.statementservice.common.exception;

public class AccessDeniedStatementException extends RuntimeException {

    public AccessDeniedStatementException(String message) {
        super(message);
    }
}
