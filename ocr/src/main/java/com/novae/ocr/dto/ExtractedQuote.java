package com.novae.ocr.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured quote extracted from OCR text by LLM (extraction phase).
 */
public class ExtractedQuote {

    private String poNumber;
    private String vendorName;
    private List<ExtractedLine> lines = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String documentType;

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public List<ExtractedLine> getLines() {
        return lines;
    }

    public void setLines(List<ExtractedLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }
}
