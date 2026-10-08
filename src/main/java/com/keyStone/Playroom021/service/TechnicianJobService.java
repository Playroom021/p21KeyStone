package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.WorkOrderDetailResponse;
import com.keyStone.Playroom021.dto.WorkOrderStatusUpdateRequest;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.exception.ConflictException;
import com.keyStone.Playroom021.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Technician job actions: start, hold, resume, complete. Each is a strictly-checked wrapper
 * over {@link WorkOrderService#transitionStatus}, so the status graph, role check and
 * StatusHistory row are the same ones Step 5 already enforces - no parallel logic.
 *
 * A work order not assigned to the calling technician is a 404 (WorkOrderService.findAccessible).
 */
@Service
@RequiredArgsConstructor
public class TechnicianJobService {

    public enum JobAction {
        START(WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS),
        HOLD(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD),
        RESUME(WorkOrderStatus.ON_HOLD, WorkOrderStatus.IN_PROGRESS),
        COMPLETE(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);

        final WorkOrderStatus requiredFrom;
        final WorkOrderStatus target;

        JobAction(WorkOrderStatus requiredFrom, WorkOrderStatus target) {
            this.requiredFrom = requiredFrom;
            this.target = target;
        }
    }

    private final WorkOrderService workOrderService;

    @Transactional
    public WorkOrderDetailResponse perform(CustomUserDetails principal, Long workOrderId, JobAction action, String note) {
        WorkOrder wo = workOrderService.findAccessible(principal, workOrderId);
        if (wo.getStatus() != action.requiredFrom) {
            throw new ConflictException("Cannot " + action.name().toLowerCase() + " a work order that is "
                    + wo.getStatus() + " (must be " + action.requiredFrom + ")");
        }
        WorkOrderStatusUpdateRequest request = new WorkOrderStatusUpdateRequest();
        request.setStatus(action.target);
        request.setNote(note);
        return workOrderService.transitionStatus(principal, workOrderId, request);
    }
}
