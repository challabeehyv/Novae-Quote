package com.novae.ocr.config;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Azure Document Intelligence (Form Recognizer) client.
 */
@Configuration
public class AzureDocumentIntelligenceConfig {

    @Value("${azure.document-intelligence.endpoint:}")
    private String endpoint;

    @Value("${azure.document-intelligence.key:}")
    private String key;

    @Bean
    public DocumentAnalysisClient documentAnalysisClient() {
        if (endpoint == null || endpoint.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        return new DocumentAnalysisClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildClient();
    }
}
