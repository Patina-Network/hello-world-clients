package org.patinanetwork.clients.utilities.exception;

public final class ValidationException extends RuntimeException {
    private final int status;

    public ValidationException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
