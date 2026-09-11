package com.receiptvision.core.service;

import java.io.InputStream;

/**
 * Abstraction over OCR engines so tests can mock it and future engines
 * (cloud OCR, Tess4J, ...) can be plugged in without touching the web layer.
 */
public interface OcrService {

    /**
     * @param imageStream image bytes (this method closes the stream)
     * @param contentType original content type, used to pick a temp-file suffix
     * @return extracted text, possibly empty but never null
     * @throws OcrException if the engine fails or times out
     */
    String extractText(InputStream imageStream, String contentType);

    String getLanguages();
}
