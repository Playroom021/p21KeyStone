package com.keyStone.Playroom021.dto;

import com.keyStone.Playroom021.entity.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for {@code POST /api/work-orders/{id}/status}.
 * {@code technicianId} is required (and only meaningful) when {@code status == ASSIGNED};
 * it is ignored for every other target status.
 */
@Data
public class WorkOrderStatusUpdateRequest {

    @NotNull(message = "status is required")
    private WorkOrderStatus status;

    private Long technicianId;

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;
}
