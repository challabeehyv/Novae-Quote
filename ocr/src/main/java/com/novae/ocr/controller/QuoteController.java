package com.novae.ocr.controller;

import com.novae.ocr.dto.OcrQuoteDTO;
import com.novae.ocr.service.QuoteWorkflowService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for PDF-to-Quote pipeline.
 */
@RestController
@RequestMapping("/api/ocr/")
public class QuoteController {

    private final QuoteWorkflowService quoteWorkflowService;

    public QuoteController(QuoteWorkflowService quoteWorkflowService) {
        this.quoteWorkflowService = quoteWorkflowService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrQuoteDTO> uploadAndProcess(@RequestParam("file") MultipartFile file) {
        OcrQuoteDTO result = quoteWorkflowService.processPdf(file);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/process")
    public ResponseEntity<OcrQuoteDTO> processByPath(@RequestParam("path") String filePath) {
        OcrQuoteDTO result = quoteWorkflowService.processPdfByPath(filePath);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<String> getStatus(@PathVariable String id) {
        return ResponseEntity.ok("NOT_IMPLEMENTED");
    }
}
