package com.receiptvision.core.web;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.service.AuthService;
import com.receiptvision.core.service.ReceiptService;
import com.receiptvision.core.web.dto.ReceiptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/receipts")
@Validated
@Tag(name = "Receipts", description = "Strictly per-user: every call only sees the caller's own receipts")
@SecurityRequirement(name = "bearerAuth")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final AuthService authService;

    public ReceiptController(ReceiptService receiptService, AuthService authService) {
        this.receiptService = receiptService;
        this.authService = authService;
    }

    private AppUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Not authenticated");
        }
        return authService.requireUser(authentication.getName());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a receipt image and run OCR (saved under your account only)")
    public ReceiptResponse upload(Authentication authentication, @RequestParam("file") MultipartFile file) {
        return receiptService.store(currentUser(authentication), file);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List ONLY your receipts (paginated)")
    public Page<ReceiptResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        Sort sortObj = parseSort(sort);
        return receiptService.list(currentUser(authentication), PageRequest.of(page, size, sortObj));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get one of YOUR receipts by id (others return 404)")
    public ReceiptResponse getById(Authentication authentication, @PathVariable Long id) {
        return receiptService.getById(currentUser(authentication), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of YOUR receipts by id")
    public void delete(Authentication authentication, @PathVariable Long id) {
        receiptService.deleteById(currentUser(authentication), id);
    }

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS =
            java.util.Set.of("id", "createdAt", "fileName", "size");

    private static Sort parseSort(String[] sort) {
        // Accepts "field,dir" e.g. "createdAt,desc" as either
        // ["createdAt","desc"] or a single ["createdAt,desc"]
        // (Spring binds defaultValue "id,desc" as one element).
        // Only allow-listed fields: prevents PropertyReferenceException -> 500
        // and sorting by sensitive/internal properties.
        try {
            String field = "id";
            String dir = "DESC";
            if (sort.length == 1 && sort[0].contains(",")) {
                String[] parts = sort[0].split(",", 2);
                field = parts[0].strip();
                dir = parts[1].strip();
            } else if (sort.length == 2) {
                field = sort[0].strip();
                dir = sort[1].strip();
            } else if (sort.length == 1) {
                field = sort[0].strip();
            }
            if (!ALLOWED_SORT_FIELDS.contains(field)) {
                return Sort.by(Sort.Direction.DESC, "id");
            }
            return Sort.by(Sort.Direction.fromString(dir), field);
        } catch (IllegalArgumentException ignored) {
            // fall through to default
        }
        return Sort.by(Sort.Direction.DESC, "id");
    }
}
