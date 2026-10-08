package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.dto.CustomerRequest;
import com.keyStone.Playroom021.dto.CustomerResponse;
import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.security.CustomUserDetails;
import com.keyStone.Playroom021.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Customer CRUD. Which roles may call each endpoint is declared in SecurityConfig;
 * CustomerService adds the row-level rule that a CUSTOMER only ever sees their own record.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(customerService.search(search, page, size, sortBy, direction));
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(customerService.getOwn(principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> get(@AuthenticationPrincipal CustomUserDetails principal,
                                                @PathVariable Long id) {
        return ResponseEntity.ok(customerService.get(principal, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
