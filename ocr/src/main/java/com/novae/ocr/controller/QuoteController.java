package com.novae.ocr.controller;

import com.novae.ocr.dto.OcrOptionDTO;
import com.novae.ocr.dto.OcrQuoteDTO;
import com.novae.ocr.dto.OcrQuoteLineDTO;
import com.novae.ocr.service.QuoteWorkflowService;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST API for PDF-to-Quote pipeline.
 */
@RestController
@RequestMapping("/api/ocr")
public class QuoteController {

    private enum JobStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    private static final class AsyncJobState {
        private final String jobId;
        private volatile JobStatus status;
        private volatile OcrQuoteDTO result;
        private volatile String error;

        private AsyncJobState(String jobId) {
            this.jobId = jobId;
            this.status = JobStatus.PROCESSING;
        }
    }

    private final Map<String, AsyncJobState> asyncJobs = new ConcurrentHashMap<>();
    private final QuoteWorkflowService quoteWorkflowService;
    private final ExecutorService asyncExecutor;

    public QuoteController(
            QuoteWorkflowService quoteWorkflowService,
            @Value("${ocr.async.parallelism:8}") int asyncParallelism) {
        this.quoteWorkflowService = quoteWorkflowService;
        this.asyncExecutor = Executors.newFixedThreadPool(Math.max(1, asyncParallelism));
    }

    @PreDestroy
    void shutdownAsyncExecutor() {
        asyncExecutor.shutdown();
    }


    @PostMapping(value = "/upload/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAndProcessAsync(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        final byte[] fileBytes;
        final String fileName = file != null ? file.getOriginalFilename() : null;
        try {
            fileBytes = file != null ? file.getBytes() : new byte[0];
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "FAILED",
                    "error", "Unable to read uploaded file"
            ));
        }

        String jobId = UUID.randomUUID().toString();
        AsyncJobState job = new AsyncJobState(jobId);
        asyncJobs.put(jobId, job);

        CompletableFuture.runAsync(() -> {
            try {
                job.result = quoteWorkflowService.processPdfBytes(fileBytes, fileName, authorizationHeader);
                job.status = JobStatus.COMPLETED;
            } catch (Exception ex) {
                job.error = ex.getMessage();
                job.status = JobStatus.FAILED;
            }
        }, asyncExecutor);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", job.status.name(),
                "statusUrl", "/api/ocr/" + jobId + "/status",
                "resultUrl", "/api/ocr/" + jobId + "/status"
        ));
    }

    /**
     * Step 2 of the two-step trailer resolution flow.
     * Frontend sends the jobId from Phase 1 and a list of {lineIndex, modelId} pairs for each
     * line the user selected. Raw OCR options are retrieved from the stored job — the frontend
     * never needs to provide them. The stored job result is updated in-place so GET /{jobId}/status
     * reflects the selections. Returns the full updated OcrQuoteDTO.
     */
    @PostMapping("/trailer/resolve-options")
    public ResponseEntity<?> resolveTrailerOptions(
            @RequestBody TrailerOptionsRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        AsyncJobState job = asyncJobs.get(request.jobId());
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Job not found", "jobId", request.jobId()));
        }
        if (job.status != JobStatus.COMPLETED || job.result == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Job is not completed yet", "status", job.status.name()));
        }
        List<OcrQuoteLineDTO> items = job.result.getQuoteItems();
        int size = items.size();
        for (LineSelection sel : request.lines()) {
            if (sel.lineIndex() < 0 || sel.lineIndex() >= size) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "lineIndex out of bounds", "lineIndex", sel.lineIndex()));
            }
        }
        for (LineSelection sel : request.lines()) {
            OcrQuoteLineDTO line = items.get(sel.lineIndex());
            List<OcrOptionDTO> resolved = quoteWorkflowService.resolveOptionsForSelectedModel(
                    sel.modelId(), line.getRawOptions(), authorizationHeader);
            line.setModelId(sel.modelId());
            line.setOptions(resolved);
        }
        return ResponseEntity.ok(job.result);
    }

    public record LineSelection(int lineIndex, @NotBlank String modelId) {}
    public record TrailerOptionsRequest(@NotBlank String jobId, @NotNull List<LineSelection> lines) {}

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String id) {
        AsyncJobState job = asyncJobs.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "jobId", id,
                    "status", "NOT_FOUND"
            ));
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jobId", job.jobId);
        response.put("status", job.status.name());
        if (job.status == JobStatus.COMPLETED && job.result != null) {
            response.put("result", job.result);
        }
        if (job.status == JobStatus.FAILED && job.error != null) {
            response.put("error", job.error);
        }
        return ResponseEntity.ok(response);
    }
}
