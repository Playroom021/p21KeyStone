package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderDetailResponse {
    private Long id;
    private String code;
    private String title;
    private String description;
    private Long customerId;
    private String customerName;
    private Long siteId;
    private String siteName;
    private String priority;
    private String status;
    private Long assignedTechnicianId;
    private String assignedTechnician;
    private Instant slaDueAt;
    /** ON_TRACK / AT_RISK / BREACHED, evaluated live; null for cancelled work. */
    private String slaStatus;
    private Instant createdAt;
    private Instant updatedAt;
    private List<StatusHistoryResponse> history;
}
