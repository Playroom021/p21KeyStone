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
    private String customerName;
    private String siteName;
    private String priority;
    private String status;
    private String assignedTechnician;
    private Instant slaDueAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<StatusHistoryResponse> history;
}
