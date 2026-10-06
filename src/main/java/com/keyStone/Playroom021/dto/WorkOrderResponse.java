package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderResponse {
    private Long id;
    private String code;
    private String title;
    private Long customerId;
    private String customerName;
    private Long siteId;
    private String siteName;
    private String priority;
    private String status;
    private Long assignedTechnicianId; // null -> unassigned
    private String assignedTechnician; // null -> "Unassigned" is handled client-side
    private Instant slaDueAt;
    /** ON_TRACK / AT_RISK / BREACHED, evaluated live; null for cancelled work. */
    private String slaStatus;
    private Instant createdAt;
}
