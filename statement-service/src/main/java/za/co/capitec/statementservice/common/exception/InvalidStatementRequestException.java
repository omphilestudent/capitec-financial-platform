package za.co.capitec.statementservice.common.exception;

public class InvalidStatementRequestException extends RuntimeException {

    public InvalidStatementRequestException(String message) {
        super(message);
    }
}
