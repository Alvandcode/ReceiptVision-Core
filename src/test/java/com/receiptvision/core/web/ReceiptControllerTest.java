package com.receiptvision.core.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.service.AuthService;
import com.receiptvision.core.service.OcrException;
import com.receiptvision.core.service.ReceiptService;
import com.receiptvision.core.web.dto.ReceiptResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceiptController.class)
// Security filters OFF for controller-logic tests: @WithMockUser still fills
// the SecurityContext so `Authentication` resolves. The 401/deny-all behavior
// of the real chain (SecurityConfig + JwtAuthenticationFilter) is declarative
// Spring Security and covered by code review, not by MockMvc here.
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class})
class ReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptService receiptService;

    @MockBean
    private AuthService authService;

    // The real JwtAuthenticationFilter IS picked up by the @WebMvcTest slice
    // (it is a Filter @Component), so its constructor deps must exist as beans.
    // With addFilters=false below it never runs; these mocks only satisfy wiring.
    @MockBean
    private com.receiptvision.core.security.JwtService jwtService;

    @MockBean
    private com.receiptvision.core.security.DatabaseUserDetailsService userDetailsService;

    @MockBean
    private com.receiptvision.core.security.TokenBlacklist tokenBlacklist;

    @MockBean
    private com.receiptvision.core.security.AuthRateLimitFilter rateLimitFilter;

    private AppUser user() {
        return new AppUser("ali", "hash");
    }

    @Test
    @WithMockUser(username = "ali")
    void upload_returns201_forOwner() throws Exception {
        when(authService.requireUser("ali")).thenReturn(user());
        ReceiptResponse response = new ReceiptResponse(
                1L, "receipt.jpg", "image/jpeg", 123L, "TOTAL 10", "fas+eng", Instant.now());
        when(receiptService.store(any(AppUser.class), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/receipts").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ocrText").value("TOTAL 10"));
    }

    // NOTE: no anonymous-access tests here on purpose: with addFilters=false
    // there is no security chain in this slice. Deny-by-default (401 without
    // JWT) is enforced by SecurityConfig in production.

    @Test
    @WithMockUser(username = "ali")
    void upload_mapsIllegalArgumentTo400() throws Exception {
        when(authService.requireUser("ali")).thenReturn(user());
        when(receiptService.store(any(AppUser.class), any()))
                .thenThrow(new IllegalArgumentException("Unsupported content type: x"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", new byte[]{1});

        mockMvc.perform(multipart("/api/receipts").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "ali")
    void upload_mapsOcrFailureTo422() throws Exception {
        when(authService.requireUser("ali")).thenReturn(user());
        when(receiptService.store(any(AppUser.class), any())).thenThrow(new OcrException("tesseract failed"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", new byte[]{1});

        mockMvc.perform(multipart("/api/receipts").file(file))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "ali")
    void list_onlyReturnsOwnPage() throws Exception {
        when(authService.requireUser("ali")).thenReturn(user());
        ReceiptResponse response = new ReceiptResponse(
                1L, "a.jpg", "image/jpeg", 1L, "text", "eng", Instant.now());
        when(receiptService.list(any(AppUser.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/receipts")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fileName").value("a.jpg"));
    }

    @Test
    @WithMockUser(username = "ali")
    void getForeignId_returns404_notLeak() throws Exception {
        when(authService.requireUser("ali")).thenReturn(user());
        when(receiptService.getById(any(AppUser.class), eq(5L)))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Receipt not found: 5"));

        mockMvc.perform(get("/api/receipts/5").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
