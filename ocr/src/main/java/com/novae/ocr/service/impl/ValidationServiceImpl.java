package com.novae.ocr.service.impl;

import com.novae.ocr.dto.ExtractedQuote;
import com.novae.ocr.dto.ResolutionResult;
import com.novae.ocr.dto.ValidationResult;
import com.novae.ocr.service.ValidationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.novae.ocr.constants.OcrConstants;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ValidationServiceImpl implements ValidationService {

    @Value("${ocr.validation.min-confidence:0.75}")
    private double minConfidence;

    @Value("${ocr.validation.min-resolution-rate:0.70}")
    private double minResolutionRate;

    @Override
    public ValidationResult validate(ExtractedQuote extracted, ResolutionResult resolved) {
        List<String> reasons = new ArrayList<>();
        boolean draft = false;

        if (resolved.getOverallConfidence() < minConfidence) {
            draft = true;
            reasons.add(OcrConstants.ERROR_LOW_CONFIDENCE);
        }

        int totalLines = extracted.getLines() != null ? extracted.getLines().size() : 0;
        if (totalLines > 0 && resolved.getLines() != null) {
            long resolvedCount = resolved.getLines().stream()
                    .filter(l -> l.getSku() != null && !l.getSku().isBlank())
                    .count();
            double rate = (double) resolvedCount / totalLines;
            if (rate < minResolutionRate) {
                draft = true;
                reasons.add(OcrConstants.ERROR_UNRESOLVED_LINES);
            }
        }

        if (extracted.getLines() != null) {
            for (var line : extracted.getLines()) {
                if (line.getQty() == null || isBlank(line.getBrand()) || isBlank(line.getSize())) {
                    draft = true;
                    reasons.add(OcrConstants.ERROR_MISSING_FIELDS);
                    break;
                }
            }
        }

        boolean structuredMissingUnitPrice = OcrConstants.DOC_TYPE_STRUCTURED
                .equals(extracted.getDocumentType())
                && extracted.getLines() != null
                && extracted.getLines().stream().anyMatch(l -> l.getUnitPrice() == null);
        if (structuredMissingUnitPrice) {
            draft = true;
            reasons.add(OcrConstants.ERROR_STRUCTURED_MISSING_UNIT_PRICE);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        if (extracted.getLines() != null && resolved.getLines() != null && extracted.getLines().size() == resolved.getLines().size()) {
            for (int i = 0; i < extracted.getLines().size(); i++) {
                var ext = extracted.getLines().get(i);
                BigDecimal qty = ext.getQty() != null ? BigDecimal.valueOf(ext.getQty()) : BigDecimal.ONE;
                BigDecimal unitPrice = ext.getUnitPrice() != null ? ext.getUnitPrice() : BigDecimal.ZERO;
                subtotal = subtotal.add(unitPrice.multiply(qty));
            }
        }
        BigDecimal total = subtotal;

        return new ValidationResult(draft, reasons, subtotal, total);
    }

    @Override
    public List<String> getMissingFieldPaths(ExtractedQuote extracted, ResolutionResult resolved) {
        List<String> paths = new ArrayList<>();
        if (resolved == null || isBlank(resolved.getResolvedVendorId())) {
            paths.add(OcrConstants.MISSING_FIELD_CUSTOMER_ID);
        }
        if (extracted != null && extracted.getLines() != null) {
            for (int i = 0; i < extracted.getLines().size(); i++) {
                var line = extracted.getLines().get(i);
                if (line.getQty() == null) {
                    paths.add(String.format(OcrConstants.MISSING_FIELD_LINES_QTY, i));
                }
                if (line.getUnitPrice() == null) {
                    paths.add(String.format(OcrConstants.MISSING_FIELD_LINES_UNIT_PRICE, i));
                }
            }
        }
        return paths;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
