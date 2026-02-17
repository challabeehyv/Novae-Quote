package com.novae.ocr.controller;

import com.novae.ocr.dto.ExtractedQuote;
import com.novae.ocr.dto.ResolutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: controller + workflow with mocked OCR and MCP Client.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuoteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private com.novae.ocr.service.AzureOcrService azureOcrService;
    @MockitoBean
    private RestClient mcpClientRestClient;

    @Test
    void uploadAndProcess_returnsOk() throws Exception {
        when(azureOcrService.extractText(any(org.springframework.web.multipart.MultipartFile.class)))
                .thenReturn("Sample OCR text");

        RestClient.RequestBodyUriSpec requestBodyUriSpec = org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = org.mockito.Mockito.mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);

        when(mcpClientRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(any(org.springframework.http.MediaType.class))).thenReturn(requestBodySpec);
        lenient().doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        ExtractedQuote extracted = new ExtractedQuote();
        extracted.setPoNumber("PO-1");
        extracted.setLines(java.util.List.of());
        ResolutionResult resolved = new ResolutionResult();
        resolved.setLines(java.util.List.of());
        resolved.setOverallConfidence(0.8);

        when(responseSpec.body(ExtractedQuote.class)).thenReturn(extracted);
        when(responseSpec.body(ResolutionResult.class)).thenReturn(resolved);

        MockMultipartFile file = new MockMultipartFile("file", "quote.pdf", "application/pdf", "sample".getBytes());
        mockMvc.perform(multipart("/api/ocr/upload").file(file))
                .andExpect(status().isOk());
    }
}
