package com.novae.ocr.service;

import com.novae.ocr.dto.OcrQuoteDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates PDF → OCR → Extract → Resolve → Validate → OcrQuoteDTO.
 */
public interface QuoteWorkflowService {

    /**
     * Process uploaded PDF through full pipeline; returns OcrQuoteDTO.
     */
    OcrQuoteDTO processPdf(MultipartFile file);

    /**
     * Process PDF from file path (e.g. for testing or batch).
     */
    OcrQuoteDTO processPdfByPath(String filePath);
}
