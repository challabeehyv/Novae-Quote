package com.novae.ocr.service;

import com.novae.ocr.service.impl.AzureOcrServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AzureOcrServiceTest {

    @Test
    void extractText_whenClientNull_returnsEmptyString() {
        AzureOcrServiceImpl service = new AzureOcrServiceImpl(null);
        String result = service.extractText(new java.io.ByteArrayInputStream(new byte[0]), "test.pdf");
        assertThat(result).isEmpty();
    }
}
