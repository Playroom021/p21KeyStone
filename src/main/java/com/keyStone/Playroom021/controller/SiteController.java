package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.dto.SiteDetailResponse;
import com.keyStone.Playroom021.dto.SiteRequest;
import com.keyStone.Playroom021.security.CustomUserDetails;
import com.keyStone.Playroom021.service.SiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Site CRUD. Sites are created and listed under their owning customer
 * ({@code /api/customers/{customerId}/sites}) and read, updated and deleted by id
 * ({@code /api/sites/{id}}). Role gating is in SecurityConfig; SiteService enforces
 * that a CUSTOMER only ever reaches sites of their own customer.
 *
 * This is separate from the older customer-portal endpoints under /api/customer/**,
 * which are unchanged.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @PostMapping("/customers/{customerId}/sites")
    public ResponseEntity<SiteDetailResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                     @PathVariable Long customerId,
                                                     @Valid @RequestBody SiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.create(principal, customerId, request));
    }

    @GetMapping("/customers/{customerId}/sites")
    public ResponseEntity<PageResponse<SiteDetailResponse>> listForCustomer(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long customerId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(siteService.search(principal, customerId, search, page, size, sortBy, direction));
    }

    @GetMapping("/sites")
    public ResponseEntity<PageResponse<SiteDetailResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(siteService.search(principal, customerId, search, page, size, sortBy, direction));
    }

    @GetMapping("/sites/{id}")
    public ResponseEntity<SiteDetailResponse> get(@AuthenticationPrincipal CustomUserDetails principal,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok(siteService.get(principal, id));
    }

    @PutMapping("/sites/{id}")
    public ResponseEntity<SiteDetailResponse> update(@AuthenticationPrincipal CustomUserDetails principal,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody SiteRequest request) {
        return ResponseEntity.ok(siteService.update(principal, id, request));
    }

    @DeleteMapping("/sites/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails principal,
                                       @PathVariable Long id) {
        siteService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
