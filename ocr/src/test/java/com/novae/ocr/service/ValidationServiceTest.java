package com.novae.ocr.service;

import com.novae.ocr.dto.ExtractedLine;
import com.novae.ocr.dto.ExtractedQuote;
import com.novae.ocr.dto.ResolvedLine;
import com.novae.ocr.dto.ResolutionResult;
import com.novae.ocr.dto.ValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { com.novae.ocr.OcrApplication.class })
class ValidationServiceTest {

    @Autowired
    private ValidationService validationService;

    @Test
    void validate_highConfidence_allCriticalFieldsPresent_returnsValidated() {
        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setDocumentType("SUMMARY_LIST");
        ExtractedLine line = new ExtractedLine();
        line.setQty(1);
        line.setBrand("ST");
        line.setSize("6x12");
        line.setUnitPrice(BigDecimal.valueOf(10000));
        extracted.setLines(List.of(line));

        ResolutionResult resolved = new ResolutionResult();
        resolved.setOverallConfidence(0.9);
        ResolvedLine rLine = new ResolvedLine();
        rLine.setSku("SKU-001");
        rLine.setConfidence(0.9);
        resolved.setLines(List.of(rLine));

        ValidationResult result = validationService.validate(extracted, resolved);

        assertThat(result.isDraft()).isFalse();
        assertThat(result.getComputedSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    void validate_lowOverallConfidence_marksDraft() {
        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setLines(List.of(new ExtractedLine()));
        ResolutionResult resolved = new ResolutionResult();
        resolved.setOverallConfidence(0.5);
        resolved.setLines(List.of(new ResolvedLine()));

        ValidationResult result = validationService.validate(extracted, resolved);

        assertThat(result.isDraft()).isTrue();
        assertThat(result.getReasons()).contains(com.novae.ocr.constants.OcrConstants.ERROR_LOW_CONFIDENCE);
    }

    @Test
    void validate_missingCriticalFields_marksDraft() {
        ExtractedQuote extracted = new ExtractedQuote();
        ExtractedLine line = new ExtractedLine();
        line.setQty(1);
        line.setBrand(null);
        line.setSize("6x12");
        extracted.setLines(List.of(line));

        ResolutionResult resolved = new ResolutionResult();
        resolved.setOverallConfidence(0.9);
        resolved.setLines(List.of(new ResolvedLine()));

        ValidationResult result = validationService.validate(extracted, resolved);

        assertThat(result.isDraft()).isTrue();
        assertThat(result.getReasons()).contains(com.novae.ocr.constants.OcrConstants.ERROR_MISSING_FIELDS);
    }

    @Test
    void validate_structuredMissingUnitPrice_marksDraft() {
        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setDocumentType(com.novae.ocr.constants.OcrConstants.DOC_TYPE_STRUCTURED);
        ExtractedLine line = new ExtractedLine();
        line.setQty(1);
        line.setBrand("ST");
        line.setSize("6x12");
        line.setUnitPrice(null);
        extracted.setLines(List.of(line));

        ResolutionResult resolved = new ResolutionResult();
        resolved.setOverallConfidence(0.9);
        resolved.setLines(List.of(new ResolvedLine()));

        ValidationResult result = validationService.validate(extracted, resolved);

        assertThat(result.isDraft()).isTrue();
        assertThat(result.getReasons()).anyMatch(r -> r.contains("unitPrice"));
    }

    @Test
    void getMissingFieldPaths_returnsCustomerIdWhenVendorUnresolved() {
        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setLines(List.of());
        ResolutionResult resolved = new ResolutionResult();
        resolved.setResolvedVendorId(null);

        List<String> paths = validationService.getMissingFieldPaths(extracted, resolved);

        assertThat(paths).contains(com.novae.ocr.constants.OcrConstants.MISSING_FIELD_CUSTOMER_ID);
    }

    @Test
    void getMissingFieldPaths_returnsLinePathsWhenQtyOrUnitPriceMissing() {
        ExtractedQuote extracted = new ExtractedQuote();
        ExtractedLine line = new ExtractedLine();
        line.setQty(null);
        line.setUnitPrice(null);
        extracted.setLines(List.of(line));
        ResolutionResult resolved = new ResolutionResult();
        resolved.setResolvedVendorId("C1");
        resolved.setLines(List.of(new ResolvedLine()));

        List<String> paths = validationService.getMissingFieldPaths(extracted, resolved);

        assertThat(paths).anyMatch(p -> p.contains("lines[0].qty"));
        assertThat(paths).anyMatch(p -> p.contains("lines[0].unitPrice"));
    }
}
