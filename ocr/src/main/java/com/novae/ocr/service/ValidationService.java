package com.novae.ocr.service;

import com.novae.ocr.dto.ExtractedQuote;
import com.novae.ocr.dto.ResolutionResult;
import com.novae.ocr.dto.ValidationResult;

import java.util.List;

/**
 * Validates extracted + resolved quote and computes draft flag and totals.
 */
public interface ValidationService {

    /**
     * Apply validation rules and compute deterministic totals.
     *
     * @param extracted from extraction phase
     * @param resolved  from resolution phase
     * @return draft flag, reasons, computed subtotal/total
     */
    ValidationResult validate(ExtractedQuote extracted, ResolutionResult resolved);

    /**
     * Return missing field paths for grid (e.g. "customer.customerId", "lines[0].qty").
     */
    List<String> getMissingFieldPaths(ExtractedQuote extracted, ResolutionResult resolved);
}
