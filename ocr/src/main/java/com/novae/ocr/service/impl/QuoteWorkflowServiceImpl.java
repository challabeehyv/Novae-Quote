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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class QuoteWorkflowServiceImpl implements QuoteWorkflowService {
    private static final Pattern SIZE_PATTERN = Pattern.compile("\\b\\d+\\s*[xX]\\s*\\d+\\b");
    private static final Pattern SALES_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{2,}$");

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
        String authorization = currentAuthorizationHeader();

        var extractRequest = mcpClientRestClient.post()
                .uri(OcrConstants.MCP_CLIENT_EXTRACT)
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN);
        if (authorization != null && !authorization.isBlank()) {
            extractRequest = extractRequest.header("Authorization", authorization);
        }
        ExtractedQuote extracted = extractRequest
                .body(ocrText)
                .retrieve()
                .body(ExtractedQuote.class);

        if (extracted == null) {
            extracted = new ExtractedQuote();
        }

        var resolveRequest = mcpClientRestClient.post()
                .uri(OcrConstants.MCP_CLIENT_RESOLVE)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (authorization != null && !authorization.isBlank()) {
            resolveRequest = resolveRequest.header("Authorization", authorization);
        }
        ResolutionResult resolved = resolveRequest
                .body(extracted)
                .retrieve()
                .body(ResolutionResult.class);

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
            LineDetails details = resolveLineDetails(res, ext);
            line.setOptions(details.options());
            line.setSpecs(details.specs());
            line.setConfidence(resolveLineConfidence(ext, res));
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
                continue;
            }
            if (looksLikePrimaryDescription(text)) continue;
            if (isAccessoryOption(text)) {
                String key = normalize(text);
                if (!optionByDescription.containsKey(key)) {
                    OcrOptionDTO dto = new OcrOptionDTO();
                    if (looksLikeSalesCode(text)) {
                        dto.setSalesCode(text);
                        optionByDescription.put("sc:" + key, dto);
                    } else {
                        OcrOptionDTO primary = findBestPrimaryOption(optionByDescription, text);
                        if (primary != null) {
                            primary.setLongDescription(appendLongDescription(primary.getLongDescription(), text));
                        } else {
                            dto.setDescription(text);
                            optionByDescription.put(key, dto);
                        }
                    }
                }
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

    private static OcrOptionDTO findBestPrimaryOption(Map<String, OcrOptionDTO> options, String candidate) {
        if (isBlank(candidate) || options == null || options.isEmpty()) return null;
        double bestScore = 0.0;
        OcrOptionDTO best = null;
        for (OcrOptionDTO existing : options.values()) {
            if (existing == null) continue;
            if (!isBlank(existing.getSalesCode())) continue;
            if (isBlank(existing.getDescription())) continue;
            double score = similarityScore(existing.getDescription(), candidate);
            if (score > bestScore) {
                bestScore = score;
                best = existing;
            }
        }
        return bestScore >= 0.66 ? best : null;
    }

    private static double similarityScore(String left, String right) {
        if (isBlank(left) || isBlank(right)) return 0.0;
        String l = canonicalOptionText(left);
        String r = canonicalOptionText(right);
        if (l.equals(r)) return 1.0;
        if (l.contains(r) || r.contains(l)) return 0.9;

        List<String> lt = tokens(l);
        List<String> rt = tokens(r);
        if (lt.isEmpty() || rt.isEmpty()) return 0.0;
        int overlap = 0;
        for (String token : lt) {
            if (rt.contains(token)) overlap++;
        }
        return (double) overlap / Math.min(lt.size(), rt.size());
    }

    private static String canonicalOptionText(String value) {
        if (isBlank(value)) return "";
        String collapsed = collapseWhitespace(value.toUpperCase(Locale.ROOT)
                .replace(".", " ")
                .replace(",", " "));
        List<String> tokens = tokens(collapsed);
        if (tokens.size() % 2 == 0) {
            int half = tokens.size() / 2;
            boolean repeated = true;
            for (int i = 0; i < half; i++) {
                if (!tokens.get(i).equals(tokens.get(i + half))) {
                    repeated = false;
                    break;
                }
            }
            if (repeated) {
                return String.join(" ", tokens.subList(0, half));
            }
        }
        return String.join(" ", tokens);
    }

    private static List<String> tokens(String value) {
        if (isBlank(value)) return List.of();
        String[] raw = value.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String token : raw) {
            String cleaned = normalizeToken(token);
            if (!cleaned.isEmpty()) out.add(cleaned);
        }
        return out;
    }

    private static String appendLongDescription(String current, String extra) {
        String normalizedCurrent = trimToNull(current);
        String normalizedExtra = trimToNull(extra);
        if (normalizedExtra == null) return normalizedCurrent;
        if (normalizedCurrent == null) return normalizedExtra;
        if (canonicalOptionText(normalizedCurrent).equals(canonicalOptionText(normalizedExtra))) {
            return normalizedCurrent;
        }
        return normalizedCurrent + ", " + normalizedExtra;
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
