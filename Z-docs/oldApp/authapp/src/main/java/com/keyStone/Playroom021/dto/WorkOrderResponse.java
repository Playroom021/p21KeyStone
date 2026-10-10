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
    private String customerName;
    private String siteName;
    private String priority;
    private String status;
    private String assignedTechnician; // null -> "Unassigned" is handled client-side
    private Instant slaDueAt;
    private Instant createdAt;
}
