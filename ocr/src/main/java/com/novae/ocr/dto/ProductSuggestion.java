package com.novae.ocr.dto;

/**
 * Product candidate from resolve tool for dropdown suggestions.
 */
public class ProductSuggestion {

    private String sku;
    private String name;
    private double confidence;

    public ProductSuggestion() {}

    public ProductSuggestion(String sku, String name, double confidence) {
        this.sku = sku;
        this.name = name;
        this.confidence = confidence;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
