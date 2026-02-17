package com.novae.ocr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient for MCP Client and (optional) dealer portal.
 */
@Configuration
public class RestClientConfig {

    @Value("${mcp.client.base-url:http://localhost:8082}")
    private String mcpClientBaseUrl;

    @Value("${mcp.client.read-timeout:120}")
    private int mcpClientReadTimeoutSeconds;

    @Value("${mcp.client.connect-timeout:10}")
    private int mcpClientConnectTimeoutSeconds;

    @Value("${ocr.dealer-portal.base-url:}")
    private String dealerPortalBaseUrl;

    @Bean
    public RestClient mcpClientRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(mcpClientConnectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(mcpClientReadTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(mcpClientBaseUrl)
                .requestFactory(factory)
                .build();
    }

    @Bean(name = "dealerPortalRestClient")
    public RestClient dealerPortalRestClient() {
        String base = dealerPortalBaseUrl != null ? dealerPortalBaseUrl.trim() : "";
        return RestClient.builder()
                .baseUrl(base.isEmpty() ? "http://localhost" : base)
                .build();
    }
}
