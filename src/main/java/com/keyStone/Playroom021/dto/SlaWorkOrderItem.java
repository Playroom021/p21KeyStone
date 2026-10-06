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
public class SlaWorkOrderItem {
    private Long id;
    private String code;
    private String title;
    private String priority;
    private String status;
    private Long assignedTechnicianId;
    private String assignedTechnician;
    private Instant slaDueAt;
    private String slaStatus;
    /** Set for BREACHED items. */
    private Long minutesOverdue;
    /** Set for AT_RISK items. */
    private Long minutesRemaining;
}
