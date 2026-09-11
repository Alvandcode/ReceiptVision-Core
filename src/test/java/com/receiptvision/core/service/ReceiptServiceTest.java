package com.receiptvision.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Optional;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.domain.Receipt;
import com.receiptvision.core.repository.ReceiptRepository;
import com.receiptvision.core.web.dto.ReceiptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private ReceiptRepository repository;

    @Mock
    private OcrService ocrService;

    private ReceiptService service;
    private AppUser ali;
    private AppUser sara;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(repository, ocrService, 10 * 1024 * 1024);
        ali = new AppUser("ali", "hash");
        sara = new AppUser("sara", "hash");
        when(ocrService.getLanguages()).thenReturn("fas+eng");
    }

    @Test
    void store_savesUnderOwnerOnly() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(ocrService.extractText(any(InputStream.class), any()))
                .thenReturn("TOTAL 100");

        Receipt saved = new Receipt(ali, "receipt.jpg", "image/jpeg", 3, "TOTAL 100", "fas+eng");
        when(repository.save(any(Receipt.class))).thenReturn(saved);

        ReceiptResponse response = service.store(ali, file);

        assertThat(response.fileName()).isEqualTo("receipt.jpg");
        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isSameAs(ali);
    }

    @Test
    void store_rejectsNullOwner_failClosed() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> service.store(null, file))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void store_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.store(ali, empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void store_rejectsUnsupportedContentType() {
        MockMultipartFile exe = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> service.store(ali, exe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void list_usesOwnerScopedQuery() {
        Receipt receipt = new Receipt(ali, "a.jpg", "image/jpeg", 1, "text", "eng");
        when(repository.findByOwner(eq(ali), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(receipt)));

        Page<ReceiptResponse> page = service.list(ali, PageRequest.of(0, 1000));

        assertThat(page.getContent()).hasSize(1);
        // Other user's data must never leak through ali's listing (mock would fail otherwise).
        verify(repository).findByOwner(eq(ali), any(PageRequest.class));
    }

    @Test
    void getById_neverReturnsForeignReceipt() {
        // sara's receipt id=1 must be invisible to ali: repository returns empty -> 404, not 403 with content.
        when(repository.findByIdAndOwner(1L, ali)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(ali, 1L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    void delete_onlyDeletesOwnRow() {
        when(repository.existsByIdAndOwner(1L, ali)).thenReturn(true);
        service.deleteById(ali, 1L);
        verify(repository).deleteByIdAndOwner(1L, ali);
    }

    @Test
    void receipt_refusesOwnerlessConstruction() {
        assertThatThrownBy(() -> new Receipt(null, "a.jpg", "image/jpeg", 1, "x", "eng"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
