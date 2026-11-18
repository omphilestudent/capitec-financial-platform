package za.co.capitec.caseservice.common.exception;

/** The requested change is not allowed in the case's current state. */
public class InvalidCaseStateException extends RuntimeException {
    public InvalidCaseStateException(String message) { super(message); }
}
