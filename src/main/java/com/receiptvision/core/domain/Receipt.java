package com.receiptvision.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Strict owner. NULL means legacy anonymous row created before login existed.
     * Such rows are purged on startup (see OrphanReceiptPurgeRunner) and never
     * returned by any owner-scoped query, so nobody except the uploader can ever
     * see a receipt.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String ocrText;

    @Column(nullable = false, length = 32)
    private String language;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Receipt() {
        // JPA
    }

    public Receipt(AppUser owner, String fileName, String contentType, long size, String ocrText, String language) {
        if (owner == null) {
            throw new IllegalArgumentException("Receipt owner must not be null");
        }
        this.owner = owner;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.ocrText = ocrText;
        this.language = language;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (owner == null) {
            throw new IllegalStateException("Refusing to persist ownerless receipt (privacy)");
        }
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public Long getOwnerId() {
        return owner == null ? null : owner.getId();
    }

    public String getOwnerUsername() {
        return owner == null ? null : owner.getUsername();
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getOcrText() {
        return ocrText;
    }

    public String getLanguage() {
        return language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
