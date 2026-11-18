package za.co.capitec.frauddetection.common.exception;

/** An incoming event failed validation. Retrying cannot fix it, so it goes straight to the dead-letter topic. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) { super(message); }
}
