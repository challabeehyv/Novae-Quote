package com.novae.ocr.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of validation phase: draft flag, reasons, computed totals.
 */
public class ValidationResult {

    private final boolean draft;
    private final List<String> reasons = new ArrayList<>();
    private final BigDecimal computedSubtotal;
    private final BigDecimal computedTotal;

    public ValidationResult(boolean draft, List<String> reasons, BigDecimal computedSubtotal, BigDecimal computedTotal) {
        this.draft = draft;
        if (reasons != null) {
            this.reasons.addAll(reasons);
        }
        this.computedSubtotal = computedSubtotal != null ? computedSubtotal : BigDecimal.ZERO;
        this.computedTotal = computedTotal != null ? computedTotal : BigDecimal.ZERO;
    }

    public boolean isDraft() {
        return draft;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public BigDecimal getComputedSubtotal() {
        return computedSubtotal;
    }

    public BigDecimal getComputedTotal() {
        return computedTotal;
    }
}
