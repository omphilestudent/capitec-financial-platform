package za.co.capitec.caseservice.common.exception;

public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException(String message) { super(message); }
}
