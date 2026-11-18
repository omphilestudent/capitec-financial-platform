package za.co.capitec.statementservice.common.exception;

public class DownloadLinkException extends RuntimeException {

    public enum Reason { INVALID, EXPIRED, REVOKED, FORBIDDEN }

    private final Reason reason;

    public DownloadLinkException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
