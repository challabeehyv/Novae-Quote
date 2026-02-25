package com.novae.ocr.service.impl;

import com.novae.ocr.constants.OcrConstants;
import com.novae.ocr.dto.ExtractedLine;
import com.novae.ocr.dto.ExtractedQuote;
import com.novae.ocr.dto.OcrOptionDTO;
import com.novae.ocr.dto.OcrQuoteDTO;
import com.novae.ocr.dto.OcrQuoteLineDTO;
import com.novae.ocr.dto.ProductSuggestion;
import com.novae.ocr.dto.ResolvedLine;
import com.novae.ocr.dto.ResolutionResult;
import com.novae.ocr.dto.ValidationResult;
import com.novae.ocr.exception.OcrProcessingException;
import com.novae.ocr.service.AzureOcrService;
import com.novae.ocr.service.QuoteWorkflowService;
import com.novae.ocr.service.ValidationService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class QuoteWorkflowServiceImpl implements QuoteWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(QuoteWorkflowServiceImpl.class);
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "\\b\\d+\\s*[xX]\\s*\\d+(?:\\s*\\(\\s*\\d+\\s*\\+\\s*\\d+\\s*\\))?\\b");
    private static final Pattern SALES_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{2,}$");

    private final AzureOcrService azureOcrService;
    private final ValidationService validationService;
    private final RestClient mcpClientRestClient;
    private final int resolveMaxLinesPerBatch;
    private final int resolveParallelism;
    private final Semaphore resolveRequestLimiter;
    private final ExecutorService resolveExecutor;

    public QuoteWorkflowServiceImpl(
            AzureOcrService azureOcrService,
            ValidationService validationService,
            @Qualifier("mcpClientRestClient") RestClient mcpClientRestClient,
            @Value("${ocr.resolve.max-lines-per-batch:8}") int resolveMaxLinesPerBatch,
            @Value("${ocr.resolve.parallelism:2}") int resolveParallelism,
            @Value("${ocr.resolve.max-inflight-requests:1}") int resolveMaxInflightRequests) {
        this.azureOcrService = azureOcrService;
        this.validationService = validationService;
        this.mcpClientRestClient = mcpClientRestClient;
        this.resolveMaxLinesPerBatch = Math.max(1, resolveMaxLinesPerBatch);
        this.resolveParallelism = Math.max(1, resolveParallelism);
        this.resolveRequestLimiter = new Semaphore(Math.max(1, resolveMaxInflightRequests), true);
        this.resolveExecutor = Executors.newFixedThreadPool(this.resolveParallelism);
    }

    @PreDestroy
    void shutdownResolveExecutor() {
        resolveExecutor.shutdown();
    }

    @Override
    public OcrQuoteDTO processPdf(MultipartFile file) {
        String ocrText = azureOcrService.extractText(file);
        return processOcrText(ocrText, currentAuthorizationHeader());
    }

    @Override
    public OcrQuoteDTO processPdf(MultipartFile file, String authorizationHeader) {
        String ocrText = azureOcrService.extractText(file);
        return processOcrText(ocrText, authorizationHeader);
    }

    @Override
    public OcrQuoteDTO processPdfBytes(byte[] fileBytes, String fileName, String authorizationHeader) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new OcrProcessingException(OcrConstants.ERROR_OCR_FAILED);
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(fileBytes)) {
            String ocrText = azureOcrService.extractText(in, fileName);
            return processOcrText(ocrText, authorizationHeader);
        } catch (Exception e) {
            throw new OcrProcessingException(OcrConstants.ERROR_OCR_FAILED, e);
        }
    }

    @Override
    public OcrQuoteDTO processPdfByPath(String filePath) {
        try (var is = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(filePath))) {
            String ocrText = azureOcrService.extractText(is, filePath);
            return processOcrText(ocrText, currentAuthorizationHeader());
        } catch (Exception e) {
            throw new com.novae.ocr.exception.OcrProcessingException(
                    OcrConstants.ERROR_OCR_FAILED, e);
        }
    }

    private OcrQuoteDTO processOcrText(String ocrText, String authorizationHeader) {
        ExtractedQuote extracted = extractQuote(ocrText, authorizationHeader);

        if (extracted == null) {
            extracted = new ExtractedQuote();
        }

        ResolutionResult resolved = resolveQuote(extracted, authorizationHeader);

        if (resolved == null) {
            resolved = new ResolutionResult();
        }

        hydrateMissingSizes(extracted);
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

    private ExtractedQuote extractQuote(String ocrText, String authorization) {
        try {
            var extractRequest = mcpClientRestClient.post()
                    .uri(OcrConstants.MCP_CLIENT_EXTRACT)
                    .contentType(MediaType.TEXT_PLAIN)
                    .accept(MediaType.APPLICATION_JSON);
            if (authorization != null && !authorization.isBlank()) {
                extractRequest = extractRequest.header("Authorization", authorization);
            }
            return extractRequest
                    .body(ocrText)
                    .retrieve()
                    .body(ExtractedQuote.class);
        } catch (RestClientException e) {
            throw toMcpException(OcrConstants.ERROR_EXTRACTION_FAILED, OcrConstants.MCP_CLIENT_EXTRACT, e);
        }
    }

    private ResolutionResult resolveQuote(ExtractedQuote extracted, String authorization) {
        long startNs = System.nanoTime();
        ResolutionResult result = resolveQuoteWithRetry(extracted, authorization);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        int lineCount = extracted != null && extracted.getLines() != null ? extracted.getLines().size() : 0;
        log.info("Resolve completed in {} ms for {} lines (single-request mode)", elapsedMs, lineCount);
        return result;
    }

    private List<ResolutionResult> resolveWave(List<ExtractedQuote> wave, String authorization, int startBatchIndex) {
        List<CompletableFuture<ResolutionResult>> futures = new ArrayList<>();
        for (int i = 0; i < wave.size(); i++) {
            ExtractedQuote batch = wave.get(i);
            final int batchIndex = startBatchIndex + i + 1;
            futures.add(CompletableFuture.supplyAsync(
                    () -> resolveBatchWithTiming(batch, authorization, batchIndex), resolveExecutor));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new OcrProcessingException(OcrConstants.ERROR_RESOLUTION_FAILED, e);
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private ResolutionResult resolveBatchWithTiming(ExtractedQuote batch, String authorization, int batchIndex) {
        int lineCount = batch.getLines() != null ? batch.getLines().size() : 0;
        long startNs = System.nanoTime();
        log.info("Resolve batch {} started (lines={})", batchIndex, lineCount);
        try {
            ResolutionResult result = resolveQuoteWithoutRetry(batch, authorization);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("Resolve batch {} succeeded in {} ms", batchIndex, elapsedMs);
            return result;
        } catch (RuntimeException ex) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.warn("Resolve batch {} failed after {} ms", batchIndex, elapsedMs, ex);
            throw ex;
        }
    }

    private ResolutionResult resolveBatchesSequentially(List<ExtractedQuote> batches, String authorization) {
        List<ResolutionResult> partials = new ArrayList<>();
        for (ExtractedQuote batch : batches) {
            partials.add(resolveQuoteWithRetry(batch, authorization));
        }
        return mergeResolutionResults(partials, batches.stream()
                .map(ExtractedQuote::getLines)
                .filter(java.util.Objects::nonNull)
                .mapToInt(List::size)
                .sum());
    }

    private ResolutionResult resolveQuoteWithRetry(ExtractedQuote extracted, String authorization) {
        try {
            return executeResolveQuote(extracted, authorization);
        } catch (RestClientException first) {
            if (isReadTimeout(first)) {
                log.warn("MCP resolve timed out, retrying once endpoint={}", OcrConstants.MCP_CLIENT_RESOLVE);
                try {
                    return executeResolveQuote(extracted, authorization);
                } catch (RestClientException second) {
                    throw toMcpException(OcrConstants.ERROR_RESOLUTION_FAILED, OcrConstants.MCP_CLIENT_RESOLVE, second);
                }
            }
            throw toMcpException(OcrConstants.ERROR_RESOLUTION_FAILED, OcrConstants.MCP_CLIENT_RESOLVE, first);
        }
    }

    private ResolutionResult resolveQuoteWithoutRetry(ExtractedQuote extracted, String authorization) {
        try {
            return executeResolveQuote(extracted, authorization);
        } catch (RestClientException exception) {
            throw toMcpException(OcrConstants.ERROR_RESOLUTION_FAILED, OcrConstants.MCP_CLIENT_RESOLVE, exception);
        }
    }

    private static List<ExtractedQuote> partitionExtractedQuote(ExtractedQuote extracted, int batchSize) {
        List<ExtractedLine> lines = extracted.getLines() != null ? extracted.getLines() : Collections.emptyList();
        List<ExtractedQuote> batches = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += batchSize) {
            int end = Math.min(lines.size(), i + batchSize);
            ExtractedQuote batch = new ExtractedQuote();
            batch.setPoNumber(extracted.getPoNumber());
            batch.setVendorName(extracted.getVendorName());
            batch.setSubtotal(extracted.getSubtotal());
            batch.setTax(extracted.getTax());
            batch.setTotal(extracted.getTotal());
            batch.setDocumentType(extracted.getDocumentType());
            batch.setLines(new ArrayList<>(lines.subList(i, end)));
            batches.add(batch);
        }
        return batches;
    }

    private static ResolutionResult mergeResolutionResults(List<ResolutionResult> partials, int totalLines) {
        ResolutionResult merged = new ResolutionResult();
        List<ResolvedLine> mergedLines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        String vendorId = null;
        double confidenceTotal = 0.0;
        int confidenceWeight = 0;

        for (ResolutionResult partial : partials) {
            if (partial == null) {
                continue;
            }
            List<ResolvedLine> lines = partial.getLines() != null ? partial.getLines() : Collections.emptyList();
            mergedLines.addAll(lines);
            int weight = lines.size();
            confidenceTotal += partial.getOverallConfidence() * weight;
            confidenceWeight += weight;
            if (vendorId == null && partial.getResolvedVendorId() != null && !partial.getResolvedVendorId().isBlank()) {
                vendorId = partial.getResolvedVendorId();
            }
            if (partial.getWarnings() != null) {
                for (String warning : partial.getWarnings()) {
                    if (warning != null && !warnings.contains(warning)) {
                        warnings.add(warning);
                    }
                }
            }
            if (partial.getMissingFields() != null) {
                for (String field : partial.getMissingFields()) {
                    if (field != null && !missingFields.contains(field)) {
                        missingFields.add(field);
                    }
                }
            }
        }

        merged.setLines(mergedLines);
        merged.setResolvedVendorId(vendorId);
        if (confidenceWeight > 0) {
            merged.setOverallConfidence(confidenceTotal / confidenceWeight);
        } else if (totalLines > 0) {
            merged.setOverallConfidence(confidenceTotal / totalLines);
        } else {
            merged.setOverallConfidence(0.0);
        }
        merged.setWarnings(warnings);
        merged.setMissingFields(missingFields);
        return merged;
    }

    private ResolutionResult executeResolveQuote(ExtractedQuote extracted, String authorization) {
        long waitStartNs = System.nanoTime();
        try {
            resolveRequestLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrProcessingException(OcrConstants.ERROR_RESOLUTION_FAILED, e);
        }
        long waitedMs = (System.nanoTime() - waitStartNs) / 1_000_000;
        if (waitedMs > 0) {
            log.info("Resolve request waited {} ms for inflight slot", waitedMs);
        }
        try {
            var resolveRequest = mcpClientRestClient.post()
                    .uri(OcrConstants.MCP_CLIENT_RESOLVE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (authorization != null && !authorization.isBlank()) {
                resolveRequest = resolveRequest.header("Authorization", authorization);
            }
            return resolveRequest
                    .body(extracted)
                    .retrieve()
                    .body(ResolutionResult.class);
        } finally {
            resolveRequestLimiter.release();
        }
    }

    private OcrProcessingException toMcpException(String message, String endpoint, RestClientException exception) {
        String suffix = "";
        if (isReadTimeout(exception)) {
            suffix = " (downstream read timeout)";
        }
        log.warn("MCP call failed endpoint={} message={}", endpoint, exception.getMessage(), exception);
        return new OcrProcessingException(message + suffix, exception);
    }

    private static boolean isReadTimeout(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private List<OcrQuoteLineDTO> mapQuoteItems(ExtractedQuote extracted, ResolutionResult resolved) {
        List<ExtractedLine> extLines = extracted.getLines() != null ? extracted.getLines() : Collections.emptyList();
        List<ResolvedLine> resLines = resolved.getLines() != null ? resolved.getLines() : Collections.emptyList();
        if (extLines.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<OcrQuoteLineDTO>> futures = new ArrayList<>(extLines.size());
        for (int i = 0; i < extLines.size(); i++) {
            final int lineIndex = i;
            futures.add(CompletableFuture.supplyAsync(
                    () -> mapQuoteItem(extLines, resLines, lineIndex),
                    resolveExecutor));
        }
        List<OcrQuoteLineDTO> items = new ArrayList<>(extLines.size());
        for (CompletableFuture<OcrQuoteLineDTO> future : futures) {
            items.add(future.join());
        }
        return items;
    }

    private static OcrQuoteLineDTO mapQuoteItem(List<ExtractedLine> extLines, List<ResolvedLine> resLines, int index) {
        ExtractedLine ext = extLines.get(index);
        ResolvedLine res = index < resLines.size() ? resLines.get(index) : null;
        String canonicalName = res != null ? res.getCanonicalName() : null;
        String ocrDescription = effectiveDescription(ext);
        String setDesc = (ocrDescription != null && !ocrDescription.isBlank()) ? ocrDescription : canonicalName;
        String resolvedModelId = resolveModelId(res);
        OcrQuoteLineDTO line = new OcrQuoteLineDTO();
        line.setModelId(resolvedModelId);
        line.setDescription(setDesc);
        line.setQty(ext.getQty());
        line.setColorParam(res != null ? res.getNormalizedColor() : ext.getColor());
        line.setBasePrice(ext.getUnitPrice());
        LineDetails details = resolveLineDetails(res, ext);
        line.setOptions(details.options());
        line.setSpecs(details.specs());
        line.setConfidence(resolveLineConfidence(ext, res));
        List<String> rowFlags = new ArrayList<>();
        if (res != null && res.getRowFlags() != null) rowFlags.addAll(res.getRowFlags());
        if (resolvedModelId == null || resolvedModelId.isBlank()) rowFlags.add(OcrConstants.ROW_FLAG_UNRESOLVED_PRODUCT);
        if (ext.getQty() == null) rowFlags.add(OcrConstants.ROW_FLAG_MISSING_QTY);
        if (ext.getUnitPrice() == null) rowFlags.add(OcrConstants.ROW_FLAG_MISSING_PRICE);
        line.setRowFlags(rowFlags);
        if (res != null && res.getSuggestions() != null && !res.getSuggestions().isEmpty()) {
            line.setSuggestions(new ArrayList<>(res.getSuggestions()));
        }
        return line;
    }

    private static LineDetails resolveLineDetails(ResolvedLine res, ExtractedLine ext) {
        Map<String, OcrOptionDTO> optionByDescription = new LinkedHashMap<>();
        Map<String, String> specs = new LinkedHashMap<>();

        if (res != null && res.getOptionDetails() != null) {
            for (OcrOptionDTO detail : res.getOptionDetails()) {
                if (detail == null) continue;
                OcrOptionDTO cleaned = sanitizeOptionDetail(detail);
                String salesCode = trimToNull(cleaned.getSalesCode());
                if (salesCode != null) {
                    optionByDescription.putIfAbsent("sc:" + normalize(salesCode), cleaned);
                    continue;
                }
                String text = optionText(cleaned);
                if (isBlank(text)) continue;
                SpecEntry spec = extractSpec(text);
                if (spec != null) {
                    specs.putIfAbsent(spec.key(), spec.value());
                    continue;
                }
                if (looksLikePrimaryDescription(text)) continue;
                if (isAccessoryOption(text)) {
                    optionByDescription.put(normalize(text), cleaned);
                }
            }
        }

        List<String> raw = mergeRawOptions(res, ext);
        for (String rawOption : raw) {
            if (isBlank(rawOption)) continue;
            String text = rawOption.trim();
            SpecEntry spec = extractSpec(text);
            if (spec != null) {
                specs.putIfAbsent(spec.key(), spec.value());
            }
        }

        return new LineDetails(new ArrayList<>(optionByDescription.values()), specs);
    }

    private static List<String> mergeRawOptions(ResolvedLine res, ExtractedLine ext) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (res != null && res.getNormalizedOptions() != null) {
            for (String option : res.getNormalizedOptions()) {
                if (!isBlank(option)) merged.add(option.trim());
            }
        }
        if (ext != null && ext.getOptions() != null) {
            for (String option : ext.getOptions()) {
                if (!isBlank(option)) merged.add(option.trim());
            }
        }
        return new ArrayList<>(merged);
    }

    private static String resolveModelId(ResolvedLine res) {
        if (res == null) {
            return null;
        }
        String sku = trimToNull(res.getSku());
        if (sku != null) {
            return sku;
        }
        List<ProductSuggestion> suggestions = res.getSuggestions();
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }
        for (ProductSuggestion suggestion : suggestions) {
            if (suggestion == null) continue;
            String suggestionSku = trimToNull(suggestion.getSku());
            if (suggestionSku != null) {
                return suggestionSku;
            }
        }
        ProductSuggestion first = suggestions.get(0);
        if (first == null) {
            return null;
        }
        String firstModelId = trimToNull(first.getModelId());
        if (firstModelId != null) {
            return firstModelId;
        }
        String firstSku = trimToNull(first.getSku());
        if (firstSku != null) {
            return firstSku;
        }
        return trimToNull(first.getName());
    }

    /**
     * Description for display/lookup: OCR description first, then product-like option fallback, then model.
     */
    private static String effectiveDescription(ExtractedLine ext) {
        if (ext.getDescription() != null && !ext.getDescription().isBlank()) return ext.getDescription();
        if (ext.getOptions() != null && !ext.getOptions().isEmpty()) {
            String first = ext.getOptions().get(0);
            if (first != null && first.length() > 15 && !first.startsWith("Body:") && !first.startsWith("Construction:") && !first.startsWith("Gate:") && !first.startsWith("Floor") && !first.startsWith("Hitch:") && !first.startsWith("Axles:") && !first.startsWith("Color:") && !first.startsWith("Empty Weight") && !first.startsWith("GVW:") && !first.startsWith("Length:") && !first.startsWith("Width:") && !first.startsWith("Height")) {
                return first;
            }
        }
        if (ext.getModel() != null && !ext.getModel().isBlank()) return ext.getModel();
        return null;
    }

    private static Double resolveLineConfidence(ExtractedLine ext, ResolvedLine res) {
        if (res != null && res.getConfidence() > 0.0) {
            return roundConfidence(res.getConfidence());
        }

        int signals = 0;
        int total = 0;

        total++;
        if (res != null && res.getSku() != null && !res.getSku().isBlank()) signals++;
        total++;
        if (res != null && res.getCanonicalName() != null && !res.getCanonicalName().isBlank()) signals++;
        total++;
        if (res != null && res.getSuggestions() != null && !res.getSuggestions().isEmpty()) signals++;
        total++;
        if (res != null && res.getOptionDetails() != null && !res.getOptionDetails().isEmpty()) signals++;
        total++;
        if (ext != null && ext.getBrand() != null && !ext.getBrand().isBlank()) signals++;
        total++;
        String description = ext != null ? effectiveDescription(ext) : null;
        if (description != null && !description.isBlank()) signals++;
        total++;
        if (ext != null && ext.getSize() != null && !ext.getSize().isBlank()) signals++;
        total++;
        if (ext != null && ext.getCapacity() != null && !ext.getCapacity().isBlank()) signals++;

        if (total == 0) return 0.0;
        double ratio = (double) signals / total;
        return roundConfidence(Math.max(1.0 / (total + 1.0), Math.min(1.0, ratio)));
    }

    private static double roundConfidence(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static boolean looksLikePrimaryDescription(String value) {
        if (isBlank(value)) return false;
        String s = value.trim();
        if (s.length() < 12) return false;
        return SIZE_PATTERN.matcher(s).find() || s.toUpperCase(Locale.ROOT).contains("TUBE TOP");
    }

    private static boolean isAccessoryOption(String value) {
        if (isBlank(value)) return false;
        String s = value.trim();
        if (s.contains(":")) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        return !lower.startsWith("length")
                && !lower.startsWith("width")
                && !lower.startsWith("height")
                && !lower.startsWith("gvw")
                && !lower.startsWith("empty weight");
    }

    private static SpecEntry extractSpec(String value) {
        if (isBlank(value)) return null;
        String s = value.trim();
        if (s.startsWith("Body:")) return new SpecEntry("body", s.substring("Body:".length()).trim());
        if (s.startsWith("Construction:")) return new SpecEntry("construction", s.substring("Construction:".length()).trim());
        if (s.startsWith("Gate:")) return new SpecEntry("gate", s.substring("Gate:".length()).trim());
        if (s.startsWith("Floor Layout:")) return new SpecEntry("floorLayout", s.substring("Floor Layout:".length()).trim());
        if (s.startsWith("Hitch:")) return new SpecEntry("hitch", s.substring("Hitch:".length()).trim());
        if (s.startsWith("Axles:")) return new SpecEntry("axles", s.substring("Axles:".length()).trim());
        if (s.startsWith("Brakes:")) return new SpecEntry("brakes", s.substring("Brakes:".length()).trim());
        if (s.startsWith("Empty Weight:")) return new SpecEntry("emptyWeight", s.substring("Empty Weight:".length()).trim());
        if (s.startsWith("Length:")) return new SpecEntry("length", s.substring("Length:".length()).trim());
        if (s.startsWith("Width:")) return new SpecEntry("width", s.substring("Width:".length()).trim());
        if (s.startsWith("Height")) return new SpecEntry("height", trimAfterPrefix(s, "Height"));
        return null;
    }

    private static String trimAfterPrefix(String value, String prefix) {
        return value.substring(Math.min(prefix.length(), value.length())).replace(":", "").trim();
    }

    private static String optionText(OcrOptionDTO option) {
        if (!isBlank(option.getDescription())) return option.getDescription().trim();
        if (!isBlank(option.getName())) return option.getName().trim();
        if (!isBlank(option.getOption())) return option.getOption().trim();
        if (!isBlank(option.getSalesCode())) return option.getSalesCode().trim();
        return null;
    }

    private static OcrOptionDTO sanitizeOptionDetail(OcrOptionDTO source) {
        OcrOptionDTO out = new OcrOptionDTO();
        out.setConfidence(source.getConfidence());
        out.setSalesCode(source.getSalesCode());
        out.setStandard(source.getStandard());

        String salesCode = trimToNull(source.getSalesCode());
        String description = trimToNull(source.getDescription());
        String name = trimToNull(source.getName());
        String option = trimToNull(source.getOption());
        String longDescription = trimToNull(source.getLongDescription());
        String price = trimToNull(source.getPrice());

        if (equalsIgnoreCase(description, salesCode)) description = null;
        if (equalsIgnoreCase(name, salesCode)) name = null;
        if (equalsIgnoreCase(option, salesCode)) option = null;
        if (equalsIgnoreCase(longDescription, salesCode)) longDescription = null;

        out.setDescription(description);
        out.setName(name);
        out.setOption(option);
        out.setLongDescription(longDescription);
        out.setPrice(price);
        return out;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) return false;
        return left.equalsIgnoreCase(right);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeSalesCode(String value) {
        if (isBlank(value)) return false;
        String s = value.trim();
        if (s.contains(" ")) return false;
        if (!SALES_CODE_PATTERN.matcher(s).matches()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) return true;
        }
        return false;
    }

    private static String normalizeToken(String token) {
        return token == null ? "" : token.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static String collapseWhitespace(String text) {
        return text == null ? null : text.replaceAll("\\s+", " ").trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void hydrateMissingSizes(ExtractedQuote extracted) {
        if (extracted == null || extracted.getLines() == null) return;
        for (ExtractedLine line : extracted.getLines()) {
            if (line == null || !isBlank(line.getSize())) continue;
            String description = line.getDescription();
            if (isBlank(description)) continue;
            Matcher matcher = SIZE_PATTERN.matcher(description);
            if (matcher.find()) {
                line.setSize(matcher.group().replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
            }
        }
    }

    private static String currentAuthorizationHeader() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        var request = servletAttrs.getRequest();
        return request != null ? request.getHeader("Authorization") : null;
    }

    private record SpecEntry(String key, String value) {}

    private record LineDetails(List<OcrOptionDTO> options, Map<String, String> specs) {}
}
