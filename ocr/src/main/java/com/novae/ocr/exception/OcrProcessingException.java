package com.novae.ocr.exception;

/**
 * Thrown when OCR or downstream processing (extraction, resolution) fails.
 */
public class OcrProcessingException extends RuntimeException {

    public OcrProcessingException(String message) {
        super(message);
    }

    public OcrProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
