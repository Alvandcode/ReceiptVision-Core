package com.receiptvision.core.web.dto;

import java.time.Instant;

import com.receiptvision.core.domain.Receipt;

public record ReceiptResponse(
        Long id,
        String fileName,
        String contentType,
        long size,
        String ocrText,
        String language,
        Instant createdAt) {

    public static ReceiptResponse from(Receipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getFileName(),
                receipt.getContentType(),
                receipt.getSize(),
                receipt.getOcrText(),
                receipt.getLanguage(),
                receipt.getCreatedAt());
    }
}
