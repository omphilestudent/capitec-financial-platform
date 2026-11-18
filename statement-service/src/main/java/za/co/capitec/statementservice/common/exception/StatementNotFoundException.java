package za.co.capitec.statementservice.common.exception;

public class StatementNotFoundException extends RuntimeException {

    public StatementNotFoundException(String message) {
        super(message);
    }
}
