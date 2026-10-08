package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.dto.*;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.security.CustomUserDetails;
import com.keyStone.Playroom021.service.CustomerPortalService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Every endpoint here is additionally restricted to ROLE_LOCAL_CUSTOMER in
 * SecurityConfig (/api/customer/** -> hasRole("LOCAL_CUSTOMER")), and every
 * call into CustomerPortalService is scoped by the caller's own customer id,
 * so one customer can never read or write another customer's data.
 */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerPortalController {

    private final CustomerPortalService customerPortalService;

    @GetMapping("/sites")
    public ResponseEntity<?> listSites(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(customerPortalService.listSites(principal));
    }

    @PostMapping("/sites")
    public ResponseEntity<?> createSite(@AuthenticationPrincipal CustomUserDetails principal,
                                         @Valid @RequestBody SiteRequest request) {
        SiteResponse created = customerPortalService.createSite(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/work-orders")
    public ResponseEntity<?> listWorkOrders(@AuthenticationPrincipal CustomUserDetails principal,
                                             @RequestParam(required = false) WorkOrderStatus status) {
        return ResponseEntity.ok(customerPortalService.listWorkOrders(principal, status));
    }

    @GetMapping("/work-orders/{id}")
    public ResponseEntity<?> getWorkOrder(@AuthenticationPrincipal CustomUserDetails principal,
                                           @PathVariable Long id) {
        try {
            return ResponseEntity.ok(customerPortalService.getWorkOrder(principal, id));
        } catch (EntityNotFoundException ex) {
            // Same 404 whether the id doesn't exist or belongs to another
            // customer — never reveal that someone else's work order exists.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiError.builder().status(404).message("Work order not found").build());
        }
    }

    @PostMapping("/work-orders")
    public ResponseEntity<?> raiseServiceRequest(@AuthenticationPrincipal CustomUserDetails principal,
                                                  @Valid @RequestBody WorkOrderRequest request) {
        try {
            WorkOrderResponse created = customerPortalService.raiseServiceRequest(principal, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiError.builder().status(404).message("Selected site not found").build());
        }
    }
}
