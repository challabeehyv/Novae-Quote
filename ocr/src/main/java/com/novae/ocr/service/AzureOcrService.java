package com.novae.ocr.service;

import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * Extracts text from PDF using Azure Document Intelligence.
 */
public interface AzureOcrService {


    /**
     * Extract OCR text from PDF input stream (e.g. from file path).
     *
     * @param inputStream PDF content
     * @param fileName    optional file name for logging
     * @return extracted text, or null if OCR client not configured
     */
    String extractText(InputStream inputStream, @Nullable String fileName);

}
