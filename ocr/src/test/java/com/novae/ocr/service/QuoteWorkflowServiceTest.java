package com.novae.ocr.service;

import com.novae.ocr.dto.ExtractedLine;
import com.novae.ocr.dto.ExtractedQuote;
import com.novae.ocr.dto.OcrOptionDTO;
import com.novae.ocr.dto.OcrQuoteDTO;
import com.novae.ocr.dto.ResolvedLine;
import com.novae.ocr.dto.ResolutionResult;
import com.novae.ocr.dto.ValidationResult;
import com.novae.ocr.service.impl.QuoteWorkflowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuoteWorkflowServiceTest {

    @Mock
    private AzureOcrService azureOcrService;
    @Mock
    private ValidationService validationService;
    @Mock
    private MultipartFile multipartFile;
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private QuoteWorkflowService quoteWorkflowService;

    @BeforeEach
    void setUp() {
        quoteWorkflowService = new QuoteWorkflowServiceImpl(azureOcrService, validationService, restClient);
    }

    @Test
    void processPdf_returnsOcrQuoteDTOWithDraftStatusWhenValidationFails() throws Exception {
        when(azureOcrService.extractText(any(MultipartFile.class))).thenReturn("OCR text");
        when(multipartFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        when(multipartFile.getOriginalFilename()).thenReturn("quote.pdf");

        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setPoNumber("PO-123");
        extracted.setLines(List.of(createExtractedLine()));
        ResolutionResult resolved = new ResolutionResult();
        resolved.setOverallConfidence(0.8);
        resolved.setLines(List.of(createResolvedLine()));

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ExtractedQuote.class)).thenReturn(extracted);
        when(responseSpec.body(ResolutionResult.class)).thenReturn(resolved);
        when(validationService.validate(any(ExtractedQuote.class), any(ResolutionResult.class)))
                .thenReturn(new ValidationResult(true, List.of("Low confidence"),
                        BigDecimal.ZERO, BigDecimal.ZERO));
        when(validationService.getMissingFieldPaths(any(ExtractedQuote.class), any(ResolutionResult.class)))
                .thenReturn(List.of());

        OcrQuoteDTO result = quoteWorkflowService.processPdf(multipartFile);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(com.novae.ocr.constants.OcrConstants.STATUS_DRAFT);
        assertThat(result.getPoNumber()).isEqualTo("PO-123");
        assertThat(result.getQuoteItems()).hasSize(1);
        assertThat(result.getQuoteItems().get(0).getModelId()).isEqualTo("SKU-1");
        assertThat(result.getQuoteItems().get(0).getOptions()).isNotNull().isEmpty();
    }

    @Test
    void processPdf_mapsOptionDetailsToQuoteLineOptions() throws Exception {
        when(azureOcrService.extractText(any(MultipartFile.class))).thenReturn("OCR text");
        when(multipartFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        when(multipartFile.getOriginalFilename()).thenReturn("quote.pdf");

        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setPoNumber("PO-123");
        extracted.setLines(List.of(createExtractedLine()));
        ResolutionResult resolved = new ResolutionResult();
        resolved.setOverallConfidence(0.8);
        ResolvedLine resolvedLine = createResolvedLine();
        OcrOptionDTO optionDetail = new OcrOptionDTO();
        optionDetail.setSalesCode("SC1");
        optionDetail.setName("Option One");
        optionDetail.setDescription("First option");
        optionDetail.setPrice("100");
        resolvedLine.setOptionDetails(List.of(optionDetail));
        resolved.setLines(List.of(resolvedLine));

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ExtractedQuote.class)).thenReturn(extracted);
        when(responseSpec.body(ResolutionResult.class)).thenReturn(resolved);
        when(validationService.validate(any(ExtractedQuote.class), any(ResolutionResult.class)))
                .thenReturn(new ValidationResult(false, List.of(), BigDecimal.valueOf(10000), BigDecimal.valueOf(10000)));
        when(validationService.getMissingFieldPaths(any(ExtractedQuote.class), any(ResolutionResult.class)))
                .thenReturn(List.of());

        OcrQuoteDTO result = quoteWorkflowService.processPdf(multipartFile);

        assertThat(result.getQuoteItems()).hasSize(1);
        List<OcrOptionDTO> options = result.getQuoteItems().get(0).getOptions();
        assertThat(options).hasSize(1);
        assertThat(options.get(0).getSalesCode()).isEqualTo("SC1");
        assertThat(options.get(0).getName()).isEqualTo("Option One");
        assertThat(options.get(0).getDescription()).isEqualTo("First option");
        assertThat(options.get(0).getPrice()).isEqualTo("100");
    }

    @Test
    void processPdf_mapsExtractedOptionStringsToOptionDtosWhenNoOptionDetails() throws Exception {
        when(azureOcrService.extractText(any(MultipartFile.class))).thenReturn("OCR text");
        when(multipartFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        when(multipartFile.getOriginalFilename()).thenReturn("quote.pdf");

        ExtractedLine extLine = createExtractedLine();
        extLine.setOptions(List.of("Raw option A", "Raw option B"));
        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setPoNumber("PO-123");
        extracted.setLines(List.of(extLine));
        ResolutionResult resolved = new ResolutionResult();
        resolved.setLines(List.of(createResolvedLine()));
        resolved.setOverallConfidence(0.8);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ExtractedQuote.class)).thenReturn(extracted);
        when(responseSpec.body(ResolutionResult.class)).thenReturn(resolved);
        when(validationService.validate(any(ExtractedQuote.class), any(ResolutionResult.class)))
                .thenReturn(new ValidationResult(false, List.of(), BigDecimal.valueOf(10000), BigDecimal.valueOf(10000)));
        when(validationService.getMissingFieldPaths(any(ExtractedQuote.class), any(ResolutionResult.class)))
                .thenReturn(List.of());

        OcrQuoteDTO result = quoteWorkflowService.processPdf(multipartFile);

        List<OcrOptionDTO> options = result.getQuoteItems().get(0).getOptions();
        assertThat(options).hasSize(2);
        assertThat(options.get(0).getDescription()).isEqualTo("Raw option A");
        assertThat(options.get(1).getDescription()).isEqualTo("Raw option B");
    }

    private static ExtractedLine createExtractedLine() {
        ExtractedLine line = new ExtractedLine();
        line.setQty(1);
        line.setBrand("ST");
        line.setSize("6x12");
        line.setUnitPrice(BigDecimal.valueOf(10000));
        return line;
    }

    private static ResolvedLine createResolvedLine() {
        ResolvedLine r = new ResolvedLine();
        r.setSku("SKU-1");
        r.setCanonicalName("Trailer 6x12");
        r.setConfidence(0.8);
        return r;
    }
}
