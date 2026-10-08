package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.PartUsageItem;
import com.keyStone.Playroom021.dto.PartUsageRequest;
import com.keyStone.Playroom021.dto.PartUsageResponse;
import com.keyStone.Playroom021.entity.Part;
import com.keyStone.Playroom021.entity.PartUsage;
import com.keyStone.Playroom021.entity.Role;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.exception.ConflictException;
import com.keyStone.Playroom021.repository.PartRepository;
import com.keyStone.Playroom021.repository.PartUsageRepository;
import com.keyStone.Playroom021.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Part usage on work orders.
 *
 * Transaction rules (see HANDOFF.md):
 * <ul>
 *   <li>{@link #record} is one {@code @Transactional} unit for the whole request: every item's
 *       stock decrement and usage row commit together or not at all. Any failure (unknown part,
 *       insufficient stock) throws a RuntimeException, which rolls back every earlier decrement
 *       in the same request.</li>
 *   <li>Each decrement is a single conditional UPDATE ({@code quantity_on_hand >= :qty}), so even
 *       concurrent requests cannot drive stock below zero; the DB CHECK constraint is a second guard.</li>
 *   <li>Removing a usage returns the stock in the same transaction as deleting the row.</li>
 * </ul>
 *
 * Access: MANAGER/DISPATCHER unrestricted; a TECHNICIAN only reaches work orders assigned to them
 * (404 otherwise, via WorkOrderService.findAccessible). CUSTOMERs are blocked in SecurityConfig.
 */
@Service
@RequiredArgsConstructor
public class PartUsageService {

    /** A technician may only consume/return parts while the job is actively being worked. */
    private static final Set<WorkOrderStatus> ACTIVE =
            EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);

    private final WorkOrderService workOrderService;
    private final PartRepository partRepository;
    private final PartUsageRepository partUsageRepository;

    @Transactional
    public List<PartUsageResponse> record(CustomUserDetails principal, Long workOrderId, PartUsageRequest request) {
        WorkOrder wo = workOrderService.findAccessible(principal, workOrderId);
        if (!ACTIVE.contains(wo.getStatus())) {
            throw new ConflictException("Parts can only be used on a work order that is IN_PROGRESS or ON_HOLD "
                    + "(current status: " + wo.getStatus() + ")");
        }

        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        List<PartUsageResponse> results = new ArrayList<>();

        for (PartUsageItem item : request.getItems()) {
            int qty = item.getQuantity();
            Part part = partRepository.findById(item.getPartId())
                    .orElseThrow(() -> new EntityNotFoundException("Part not found: " + item.getPartId()));

            int updated = partRepository.decrementStock(part.getId(), qty, Instant.now());
            if (updated == 0) {
                Integer available = partRepository.findQuantityOnHand(part.getId());
                throw new ConflictException("Insufficient stock for part " + part.getSku()
                        + " (requested " + qty + ", available " + (available == null ? 0 : available) + ")");
            }

            PartUsage usage = partUsageRepository.save(PartUsage.builder()
                    .workOrder(wo)
                    .part(part)
                    .quantity(qty)
                    .unitCostAtUse(part.getUnitCost())
                    .note(note)
                    .usedByEmail(principal.getUsername())
                    .usedByRole(principal.getUser().getRole().name())
                    .build());

            results.add(toResponse(usage, partRepository.findQuantityOnHand(part.getId())));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<PartUsageResponse> list(CustomUserDetails principal, Long workOrderId) {
        WorkOrder wo = workOrderService.findAccessible(principal, workOrderId);
        return partUsageRepository.findByWorkOrderIdOrderByUsedAtAscIdAsc(wo.getId()).stream()
                .map(u -> toResponse(u, null))
                .toList();
    }

    @Transactional
    public void remove(CustomUserDetails principal, Long workOrderId, Long usageId) {
        WorkOrder wo = workOrderService.findAccessible(principal, workOrderId);
        if (principal.getUser().getRole() == Role.TECHNICIAN && !ACTIVE.contains(wo.getStatus())) {
            throw new ConflictException("A technician can only change parts on a work order that is IN_PROGRESS or ON_HOLD");
        }
        PartUsage usage = partUsageRepository.findByIdAndWorkOrderId(usageId, wo.getId())
                .orElseThrow(() -> new EntityNotFoundException("Part usage not found"));

        partRepository.incrementStock(usage.getPart().getId(), usage.getQuantity(), Instant.now());
        partUsageRepository.delete(usage);
    }

    private PartUsageResponse toResponse(PartUsage u, Integer remainingStock) {
        return PartUsageResponse.builder()
                .id(u.getId())
                .workOrderId(u.getWorkOrder().getId())
                .partId(u.getPart().getId())
                .partSku(u.getPart().getSku())
                .partName(u.getPart().getName())
                .quantity(u.getQuantity())
                .unitCostAtUse(u.getUnitCostAtUse())
                .lineCost(u.getUnitCostAtUse().multiply(BigDecimal.valueOf(u.getQuantity())))
                .remainingStock(remainingStock)
                .note(u.getNote())
                .usedByEmail(u.getUsedByEmail())
                .usedByRole(u.getUsedByRole())
                .usedAt(u.getUsedAt())
                .build();
    }
}
