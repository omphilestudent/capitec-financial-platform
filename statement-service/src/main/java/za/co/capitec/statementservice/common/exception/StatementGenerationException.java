package za.co.capitec.statementservice.common.exception;

public class StatementGenerationException extends RuntimeException {

    public StatementGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
