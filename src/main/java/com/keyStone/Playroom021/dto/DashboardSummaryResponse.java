package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {
    private Instant generatedAt;
    private long totalWorkOrders;
    /** NEW + ASSIGNED + IN_PROGRESS + ON_HOLD. */
    private long openWorkOrders;
    /** COMPLETED + CLOSED. */
    private long completedWorkOrders;
    private long cancelledWorkOrders;
    /** Open work orders past their SLA due time. */
    private long overdueWorkOrders;
    private Map<String, Long> countsByStatus;
    private SlaSummary sla;
}
