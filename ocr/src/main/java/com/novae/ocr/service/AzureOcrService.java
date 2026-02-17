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
     * Extract OCR text from uploaded PDF.
     *
     * @param file uploaded PDF
     * @return extracted text, or null if OCR client not configured
     */
    String extractText(MultipartFile file);

    /**
     * Extract OCR text from PDF input stream (e.g. from file path).
     *
     * @param inputStream PDF content
     * @param fileName    optional file name for logging
     * @return extracted text, or null if OCR client not configured
     */
    String extractText(InputStream inputStream, @Nullable String fileName);

    /**
     * Extract OCR text per page for chunked processing (e.g. multi-quote PDFs).
     *
     * @param inputStream PDF content
     * @param fileName    optional file name for logging
     * @return list of page texts (one per page), or empty if OCR not configured
     */
    List<String> extractTextByPages(InputStream inputStream, @Nullable String fileName);
}
