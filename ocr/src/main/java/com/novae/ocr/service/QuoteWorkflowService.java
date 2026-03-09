package com.novae.ocr.service;

import com.novae.ocr.dto.OcrOptionDTO;
import com.novae.ocr.dto.OcrQuoteDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Orchestrates PDF → OCR → Extract → Resolve → Validate → OcrQuoteDTO.
 */
public interface QuoteWorkflowService {

    /**
     * Process PDF bytes through full pipeline using explicit auth header.
     */
    OcrQuoteDTO processPdfBytes(byte[] fileBytes, String fileName, String authorizationHeader);


    /**
     * Step 2 of the two-step trailer resolution flow.
     * Given the modelId selected by the human and the original OCR options, resolves and returns
     * the best-matching option details via LLM.
     */
    List<OcrOptionDTO> resolveOptionsForSelectedModel(String modelId, List<String> options, String authorizationHeader);
}
