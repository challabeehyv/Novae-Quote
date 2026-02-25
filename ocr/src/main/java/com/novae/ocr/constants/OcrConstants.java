package com.novae.ocr.constants;

import java.util.List;

/**
 * Application-wide constants for OCR quote pipeline.
 */
public final class OcrConstants {

    private OcrConstants() {}

    // Validation thresholds
    public static final double MIN_OVERALL_CONFIDENCE = 0.75;
    public static final double MIN_LINE_RESOLUTION_RATE = 0.70;
    public static final List<String> MIN_CRITICAL_FIELDS = List.of("qty", "brand", "size");

    // Status
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_VALIDATED = "VALIDATED";

    // Document types
    public static final String DOC_TYPE_STRUCTURED = "STRUCTURED_TABLE";
    public static final String DOC_TYPE_SUMMARY = "SUMMARY_LIST";
    public static final String DOC_TYPE_QUOTE = "QUOTE";

    // Row flags (grid)
    public static final String ROW_FLAG_UNRESOLVED_PRODUCT = "UNRESOLVED_PRODUCT";
    public static final String ROW_FLAG_MISSING_QTY = "MISSING_QTY";
    public static final String ROW_FLAG_MISSING_PRICE = "MISSING_PRICE";

    // Missing field path prefixes
    public static final String MISSING_FIELD_CUSTOMER_ID = "customer.customerId";
    public static final String MISSING_FIELD_LINES_QTY = "lines[%d].qty";
    public static final String MISSING_FIELD_LINES_UNIT_PRICE = "lines[%d].unitPrice";

    // Error messages
    public static final String ERROR_OCR_FAILED = "Azure OCR processing failed";
    public static final String ERROR_OCR_DNS_FAILED = "Azure OCR endpoint DNS resolution failed";
    public static final String ERROR_EXTRACTION_FAILED = "LLM extraction failed";
    public static final String ERROR_RESOLUTION_FAILED = "Resolution agent failed";
    public static final String ERROR_VALIDATION_FAILED = "Validation failed";
    public static final String ERROR_LOW_CONFIDENCE = "Overall confidence below threshold";
    public static final String ERROR_MISSING_FIELDS = "Missing critical fields";
    public static final String ERROR_UNRESOLVED_LINES = "Too many unresolved lines";
    public static final String ERROR_STRUCTURED_MISSING_UNIT_PRICE = "Structured document missing unitPrice";

    // API endpoints (MCP Client)
    public static final String MCP_CLIENT_EXTRACT = "/api/quote/extract";
    public static final String MCP_CLIENT_RESOLVE = "/api/quote/resolve";

    // Config keys
    public static final String CONFIG_MCP_CLIENT_BASE_URL = "mcp.client.base-url";
    public static final String CONFIG_MIN_CONFIDENCE = "ocr.validation.min-confidence";
    public static final String CONFIG_MIN_RESOLUTION_RATE = "ocr.validation.min-resolution-rate";
    public static final String CONFIG_DEALER_PORTAL_BASE_URL = "ocr.dealer-portal.base-url";
    public static final String CONFIG_MAX_PAGES_PER_BATCH = "ocr.process.max-pages-per-batch";
}
