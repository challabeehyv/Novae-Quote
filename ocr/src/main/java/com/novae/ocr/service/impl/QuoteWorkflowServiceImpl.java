package com.novae.ocr.service.impl;

import com.novae.ocr.constants.OcrConstants;
import com.novae.ocr.dto.ExtractedLine;
import com.novae.ocr.dto.ExtractedQuote;
import com.novae.ocr.dto.OcrOptionDTO;
import com.novae.ocr.dto.OcrQuoteDTO;
import com.novae.ocr.dto.OcrQuoteLineDTO;
import com.novae.ocr.dto.ResolvedLine;
import com.novae.ocr.dto.ResolutionResult;
import com.novae.ocr.dto.ValidationResult;
import com.novae.ocr.service.AzureOcrService;
import com.novae.ocr.service.QuoteWorkflowService;
import com.novae.ocr.service.ValidationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class QuoteWorkflowServiceImpl implements QuoteWorkflowService {

    private final AzureOcrService azureOcrService;
    private final ValidationService validationService;
    private final RestClient mcpClientRestClient;

    public QuoteWorkflowServiceImpl(
            AzureOcrService azureOcrService,
            ValidationService validationService,
            @Qualifier("mcpClientRestClient") RestClient mcpClientRestClient) {
        this.azureOcrService = azureOcrService;
        this.validationService = validationService;
        this.mcpClientRestClient = mcpClientRestClient;
    }

    @Override
    public OcrQuoteDTO processPdf(MultipartFile file) {
        String ocrText = azureOcrService.extractText(file);
        return processOcrText(ocrText);
    }

    @Override
    public OcrQuoteDTO processPdfByPath(String filePath) {
        try (var is = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(filePath))) {
            String ocrText = azureOcrService.extractText(is, filePath);
            return processOcrText(ocrText);
        } catch (Exception e) {
            throw new com.novae.ocr.exception.OcrProcessingException(
                    OcrConstants.ERROR_OCR_FAILED, e);
        }
    }

    private OcrQuoteDTO processOcrText(String ocrText) {
        ExtractedQuote extracted = mcpClientRestClient.post()
                .uri(OcrConstants.MCP_CLIENT_EXTRACT)
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(ocrText)
                .retrieve()
                .body(ExtractedQuote.class);

        if (extracted == null) {
            extracted = new ExtractedQuote();
        }

        ResolutionResult resolved = mcpClientRestClient.post()
                .uri(OcrConstants.MCP_CLIENT_RESOLVE)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(extracted)
                .retrieve()
                .body(ResolutionResult.class);

        if (resolved == null) {
            resolved = new ResolutionResult();
        }

        ValidationResult validation = validationService.validate(extracted, resolved);
        List<String> missingFields = validationService.getMissingFieldPaths(extracted, resolved);

        OcrQuoteDTO dto = new OcrQuoteDTO();
        dto.setStatus(validation.isDraft() ? OcrConstants.STATUS_DRAFT : OcrConstants.STATUS_VALIDATED);
        dto.setTotalRetailPrice(validation.getComputedTotal());
        dto.setNotes(validation.getReasons().isEmpty() ? null : String.join("; ", validation.getReasons()));
        dto.setPoNumber(extracted.getPoNumber());
        dto.setQuoteName(extracted.getPoNumber() != null ? "Quote-" + extracted.getPoNumber() : "Quote-OCR");
        dto.setCustomerId(resolved.getResolvedVendorId());
        dto.setBrand(extracted.getLines().isEmpty() ? null : extracted.getLines().get(0).getBrand());
        List<String> warnings = new ArrayList<>();
        if (validation.getReasons() != null) warnings.addAll(validation.getReasons());
        if (resolved.getWarnings() != null) warnings.addAll(resolved.getWarnings());
        dto.setWarnings(warnings);
        List<String> allMissing = new ArrayList<>();
        if (missingFields != null) allMissing.addAll(missingFields);
        if (resolved.getMissingFields() != null) allMissing.addAll(resolved.getMissingFields());
        dto.setMissingFields(allMissing);
        dto.setQuoteItems(mapQuoteItems(extracted, resolved));
        return dto;
    }

    private static List<OcrQuoteLineDTO> mapQuoteItems(ExtractedQuote extracted, ResolutionResult resolved) {
        List<OcrQuoteLineDTO> items = new ArrayList<>();
        List<ExtractedLine> extLines = extracted.getLines() != null ? extracted.getLines() : Collections.emptyList();
        List<ResolvedLine> resLines = resolved.getLines() != null ? resolved.getLines() : Collections.emptyList();
        for (int i = 0; i < extLines.size(); i++) {
            ExtractedLine ext = extLines.get(i);
            ResolvedLine res = i < resLines.size() ? resLines.get(i) : null;
            String canonicalName = res != null ? res.getCanonicalName() : null;
            String setDesc = (canonicalName != null && !canonicalName.isBlank()) ? canonicalName : effectiveDescription(ext);
            OcrQuoteLineDTO line = new OcrQuoteLineDTO();
            line.setModelId(res != null ? res.getSku() : null);
            line.setDescription(setDesc);
            line.setQty(ext.getQty());
            line.setColorParam(res != null ? res.getNormalizedColor() : ext.getColor());
            line.setBasePrice(ext.getUnitPrice());
            line.setOptions(resolveOptions(res, ext));
            line.setConfidence(res != null ? res.getConfidence() : null);
            List<String> rowFlags = new ArrayList<>();
            if (res != null && res.getRowFlags() != null) rowFlags.addAll(res.getRowFlags());
            if (res == null || res.getSku() == null || res.getSku().isBlank()) rowFlags.add(OcrConstants.ROW_FLAG_UNRESOLVED_PRODUCT);
            if (ext.getQty() == null) rowFlags.add(OcrConstants.ROW_FLAG_MISSING_QTY);
            if (ext.getUnitPrice() == null) rowFlags.add(OcrConstants.ROW_FLAG_MISSING_PRICE);
            line.setRowFlags(rowFlags);
            if (res != null && res.getSuggestions() != null && !res.getSuggestions().isEmpty()) {
                line.setSuggestions(new ArrayList<>(res.getSuggestions()));
            }
            items.add(line);
        }
        return items;
    }

    private static List<OcrOptionDTO> resolveOptions(ResolvedLine res, ExtractedLine ext) {
        if (res != null && res.getOptionDetails() != null && !res.getOptionDetails().isEmpty()) {
            return new ArrayList<>(res.getOptionDetails());
        }
        List<String> raw = ext.getOptions() != null ? ext.getOptions() : Collections.emptyList();
        List<OcrOptionDTO> options = new ArrayList<>();
        for (String s : raw) {
            OcrOptionDTO dto = new OcrOptionDTO();
            dto.setDescription(s);
            options.add(dto);
        }
        return options;
    }

    /**
     * Description for display/lookup: description field, else model, else first option if it looks like a product line (LLM sometimes puts Description column in options).
     */
    private static String effectiveDescription(ExtractedLine ext) {
        if (ext.getDescription() != null && !ext.getDescription().isBlank()) return ext.getDescription();
        if (ext.getModel() != null && !ext.getModel().isBlank()) return ext.getModel();
        if (ext.getOptions() != null && !ext.getOptions().isEmpty()) {
            String first = ext.getOptions().get(0);
            if (first != null && first.length() > 15 && !first.startsWith("Body:") && !first.startsWith("Construction:") && !first.startsWith("Gate:") && !first.startsWith("Floor") && !first.startsWith("Hitch:") && !first.startsWith("Axles:") && !first.startsWith("Color:") && !first.startsWith("Empty Weight") && !first.startsWith("GVW:") && !first.startsWith("Length:") && !first.startsWith("Width:") && !first.startsWith("Height")) {
                return first;
            }
        }
        return null;
    }
}
