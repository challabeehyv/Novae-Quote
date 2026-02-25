package com.novae.ocr.service.impl;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzeResult;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentPage;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentSpan;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.SyncPoller;
import com.novae.ocr.constants.OcrConstants;
import com.novae.ocr.exception.OcrProcessingException;
import com.novae.ocr.service.AzureOcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class AzureOcrServiceImpl implements AzureOcrService {

    private static final Logger log = LoggerFactory.getLogger(AzureOcrServiceImpl.class);
    private static final String MODEL_PREBUILT_LAYOUT = "prebuilt-layout";

    private final DocumentAnalysisClient documentAnalysisClient;

    public AzureOcrServiceImpl(@Nullable DocumentAnalysisClient documentAnalysisClient) {
        this.documentAnalysisClient = documentAnalysisClient;
    }

    @Override
    public String extractText(MultipartFile file) {
        if (documentAnalysisClient == null) {
            return "";
        }
        try {
            return extractText(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new OcrProcessingException(OcrConstants.ERROR_OCR_FAILED, e);
        }
    }

    @Override
    public String extractText(InputStream inputStream, @Nullable String fileName) {
        if (documentAnalysisClient == null) {
            return "";
        }
        try {
            return doExtract(inputStream, fileName);
        } catch (Exception e) {
            log.warn("Azure OCR failed for file={}", fileName, e);
            if (isUnknownHost(e)) {
                throw new OcrProcessingException(OcrConstants.ERROR_OCR_DNS_FAILED, e);
            }
            throw new OcrProcessingException(OcrConstants.ERROR_OCR_FAILED, e);
        }
    }

    @Override
    public List<String> extractTextByPages(InputStream inputStream, @Nullable String fileName) {
        if (documentAnalysisClient == null) {
            return List.of();
        }
        try {
            return doExtractByPages(inputStream, fileName);
        } catch (Exception e) {
            log.warn("Azure OCR by-pages failed for file={}", fileName, e);
            if (isUnknownHost(e)) {
                throw new OcrProcessingException(OcrConstants.ERROR_OCR_DNS_FAILED, e);
            }
            throw new OcrProcessingException(OcrConstants.ERROR_OCR_FAILED, e);
        }
    }

    private String doExtract(InputStream inputStream, @Nullable String fileName) throws IOException {
        AnalyzeResult result = analyzeDocument(inputStream, fileName);
        if (result == null) return "";
        String content = result.getContent();
        return content != null ? content : "";
    }

    private List<String> doExtractByPages(InputStream inputStream, @Nullable String fileName) throws IOException {
        AnalyzeResult result = analyzeDocument(inputStream, fileName);
        if (result == null || result.getPages() == null) return List.of();
        String content = result.getContent();
        if (content == null) content = "";
        List<String> pageTexts = new ArrayList<>();
        for (DocumentPage page : result.getPages()) {
            if (page.getSpans() == null || page.getSpans().isEmpty()) {
                pageTexts.add("");
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (DocumentSpan span : page.getSpans()) {
                int offset = span.getOffset();
                int length = span.getLength();
                if (offset >= 0 && offset + length <= content.length()) {
                    sb.append(content, offset, offset + length);
                }
            }
            pageTexts.add(sb.toString());
        }
        return pageTexts;
    }

    private AnalyzeResult analyzeDocument(InputStream inputStream, @Nullable String fileName) throws IOException {
        byte[] bytes = readFully(inputStream);
        BinaryData document = BinaryData.fromBytes(bytes);
        SyncPoller<?, AnalyzeResult> poller =
                documentAnalysisClient.beginAnalyzeDocument(MODEL_PREBUILT_LAYOUT, document);
        return poller.getFinalResult();
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean isUnknownHost(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof java.net.UnknownHostException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
