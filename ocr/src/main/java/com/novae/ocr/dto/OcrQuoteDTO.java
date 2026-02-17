package com.novae.ocr.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Final quote output from PDF-to-Quote pipeline (maps to dealer-portal QuoteDTO).
 */
public class OcrQuoteDTO {

    private String quoteName;
    private String customerId;
    private String brand;
    private String status;
    private String poNumber;
    private BigDecimal totalRetailPrice;
    private String notes;
    private List<String> warnings = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();
    private List<OcrQuoteLineDTO> quoteItems = new ArrayList<>();

    public String getQuoteName() {
        return quoteName;
    }

    public void setQuoteName(String quoteName) {
        this.quoteName = quoteName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public BigDecimal getTotalRetailPrice() {
        return totalRetailPrice;
    }

    public void setTotalRetailPrice(BigDecimal totalRetailPrice) {
        this.totalRetailPrice = totalRetailPrice;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public List<OcrQuoteLineDTO> getQuoteItems() {
        return quoteItems;
    }

    public void setQuoteItems(List<OcrQuoteLineDTO> quoteItems) {
        this.quoteItems = quoteItems != null ? quoteItems : new ArrayList<>();
    }
}
