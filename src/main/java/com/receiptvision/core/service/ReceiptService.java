package com.receiptvision.core.service;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.domain.Receipt;
import com.receiptvision.core.repository.ReceiptRepository;
import com.receiptvision.core.web.dto.ReceiptResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReceiptService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/tiff", "image/bmp");

    private final ReceiptRepository repository;
    private final OcrService ocrService;
    private final long maxFileSize;

    public ReceiptService(
            ReceiptRepository repository,
            OcrService ocrService,
            @Value("${app.upload.max-file-size:10485760}") long maxFileSize) {
        this.repository = repository;
        this.ocrService = ocrService;
        this.maxFileSize = maxFileSize;
    }

    private static AppUser requireOwner(AppUser owner) {
        if (owner == null) {
            // Should never happen behind SecurityConfig, but fail closed for privacy.
            throw new SecurityException("Authentication required");
        }
        return owner;
    }

    @Transactional
    public ReceiptResponse store(AppUser owner, MultipartFile file) {
        requireOwner(owner);
        validate(file);

        String contentType = file.getContentType();
        String text;
        try {
            text = ocrService.extractText(file.getInputStream(), contentType);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file", e);
        }

        String fileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename());
        Receipt saved = repository.save(new Receipt(
                owner,
                fileName,
                contentType,
                file.getSize(),
                text == null ? "" : text,
                ocrService.getLanguages()));

        return ReceiptResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ReceiptResponse> list(AppUser owner, Pageable pageable) {
        requireOwner(owner);
        int size = Math.min(pageable.getPageSize(), 100);
        Pageable capped = PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
        // Owner-scoped query: other users' rows are invisible at the SQL level.
        return repository.findByOwner(owner, capped).map(ReceiptResponse::from);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getById(AppUser owner, Long id) {
        requireOwner(owner);
        // 404 for both missing AND foreign-owned ids: no existence oracle for attackers.
        Receipt receipt = repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new EntityNotFoundException("Receipt not found: " + id));
        return ReceiptResponse.from(receipt);
    }

    @Transactional
    public void deleteById(AppUser owner, Long id) {
        requireOwner(owner);
        if (!repository.existsByIdAndOwner(id, owner)) {
            throw new EntityNotFoundException("Receipt not found: " + id);
        }
        repository.deleteByIdAndOwner(id, owner);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File too large. Max allowed: " + maxFileSize + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + contentType
                            + ". Allowed: " + new TreeSet<>(ALLOWED_CONTENT_TYPES));
        }
        String filename = file.getOriginalFilename();
        if (filename != null && (filename.contains("..") || filename.contains("/") || filename.contains("\\"))) {
            throw new IllegalArgumentException("Invalid file name");
        }
    }
}
