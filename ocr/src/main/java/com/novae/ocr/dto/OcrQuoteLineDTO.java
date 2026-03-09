package com.novae.ocr.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single quote line in final OCR quote output (maps to QuoteItemDTO).
 */
public class OcrQuoteLineDTO {

    private String modelId;
    private String description;
    private Integer qty;
    private String colorParam;
    private BigDecimal basePrice;
    private List<OcrOptionDTO> options = new ArrayList<>();
    /** Raw OCR-extracted option strings. Stored in-memory for Phase 2 lookup; never serialized. */
    @JsonIgnore
    private List<String> rawOptions = new ArrayList<>();
    private Map<String, String> specs = new LinkedHashMap<>();
    private Double confidence;
    private List<String> rowFlags = new ArrayList<>();
    private List<ProductSuggestion> suggestions = new ArrayList<>();

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public String getColorParam() {
        return colorParam;
    }

    public void setColorParam(String colorParam) {
        this.colorParam = colorParam;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public List<OcrOptionDTO> getOptions() {
        return options;
    }

    public void setOptions(List<OcrOptionDTO> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    public List<String> getRawOptions() {
        return rawOptions;
    }

    public void setRawOptions(List<String> rawOptions) {
        this.rawOptions = rawOptions != null ? rawOptions : new ArrayList<>();
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, String> specs) {
        this.specs = specs != null ? specs : new LinkedHashMap<>();
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
