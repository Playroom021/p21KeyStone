package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.dto.WorkOrderDetailResponse;
import com.keyStone.Playroom021.dto.WorkOrderRequest;
import com.keyStone.Playroom021.dto.WorkOrderResponse;
import com.keyStone.Playroom021.dto.WorkOrderStatusUpdateRequest;
import com.keyStone.Playroom021.entity.Priority;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.security.CustomUserDetails;
import com.keyStone.Playroom021.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Work Order CRUD and status lifecycle (Step 5). Role gating is in SecurityConfig;
 * WorkOrderService enforces the row-level rules: a CUSTOMER only ever reaches their
 * own work orders, and a TECHNICIAN only ever reaches ones assigned to them.
 *
 * This is separate from the older customer-portal endpoints under
 * {@code /api/customer/work-orders} (raise a service request as a customer), which are
 * unchanged and still the way a CUSTOMER creates a work order.
 */
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                      @Valid @RequestBody WorkOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workOrderService.create(principal, request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<WorkOrderResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(workOrderService.search(principal, customerId, siteId, technicianId,
                status, priority, search, page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderDetailResponse> get(@AuthenticationPrincipal CustomUserDetails principal,
                                                         @PathVariable Long id) {
        return ResponseEntity.ok(workOrderService.get(principal, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> update(@AuthenticationPrincipal CustomUserDetails principal,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody WorkOrderRequest request) {
        return ResponseEntity.ok(workOrderService.update(principal, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails principal,
                                        @PathVariable Long id) {
        workOrderService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<WorkOrderDetailResponse> transitionStatus(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderStatusUpdateRequest request) {
        return ResponseEntity.ok(workOrderService.transitionStatus(principal, id, request));
    }
}
