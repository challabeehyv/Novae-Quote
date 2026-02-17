package com.novae.ocr.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Single line extracted from OCR text by LLM (extraction phase).
 */
public class ExtractedLine {

    private Integer qty;
    private String brand;
    /** Full line description as shown on the document (e.g. "6x12 utility trailer"). Use with brand for resolution. */
    private String description;
    private String size;
    private String capacity;
    private String model;
    private String color;
    private List<String> options = new ArrayList<>();
    private BigDecimal unitPrice;

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
