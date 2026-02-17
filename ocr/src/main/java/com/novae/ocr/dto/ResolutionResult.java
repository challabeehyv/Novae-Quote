package com.novae.ocr.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of resolution phase (LLM agent + tool callbacks).
 */
public class ResolutionResult {

    private List<ResolvedLine> lines = new ArrayList<>();
    private String resolvedVendorId;
    private double overallConfidence;
    private List<String> warnings = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();

    public List<ResolvedLine> getLines() {
        return lines;
    }

    public void setLines(List<ResolvedLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public String getResolvedVendorId() {
        return resolvedVendorId;
    }

    public void setResolvedVendorId(String resolvedVendorId) {
        this.resolvedVendorId = resolvedVendorId;
    }

    public double getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(double overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields != null ? missingFields : new ArrayList<>();
    }
}
