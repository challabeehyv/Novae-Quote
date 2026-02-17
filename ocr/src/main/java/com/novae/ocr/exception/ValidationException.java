package com.novae.ocr.exception;

/**
 * Thrown when quote validation fails or produces draft.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
