package com.novae.ocr.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Single line after resolution (SKU, canonical name, confidence from tools).
 */
public class ResolvedLine {

    private String sku;
    private String canonicalName;
    private String normalizedColor;
    private List<String> normalizedOptions = new ArrayList<>();
    private List<OcrOptionDTO> optionDetails = new ArrayList<>();
    private double confidence;
    private List<String> rowFlags = new ArrayList<>();
    private List<ProductSuggestion> suggestions = new ArrayList<>();

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public String getNormalizedColor() {
        return normalizedColor;
    }

    public void setNormalizedColor(String normalizedColor) {
        this.normalizedColor = normalizedColor;
    }

    public List<String> getNormalizedOptions() {
        return normalizedOptions;
    }

    public void setNormalizedOptions(List<String> normalizedOptions) {
        this.normalizedOptions = normalizedOptions != null ? normalizedOptions : new ArrayList<>();
    }

    public List<OcrOptionDTO> getOptionDetails() {
        return optionDetails;
    }

    public void setOptionDetails(List<OcrOptionDTO> optionDetails) {
        this.optionDetails = optionDetails != null ? optionDetails : new ArrayList<>();
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<String> getRowFlags() {
        return rowFlags;
    }

    public void setRowFlags(List<String> rowFlags) {
        this.rowFlags = rowFlags != null ? rowFlags : new ArrayList<>();
    }

    public List<ProductSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ProductSuggestion> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }
}
