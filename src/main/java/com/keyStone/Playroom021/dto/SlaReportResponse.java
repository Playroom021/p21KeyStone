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
public class SlaReportResponse {
    private Instant generatedAt;
    private SlaSummary summary;
    /** Open breached work orders, most overdue first (capped). */
    private List<SlaWorkOrderItem> breachedWorkOrders;
    /** Open at-risk work orders, soonest due first (capped). */
    private List<SlaWorkOrderItem> atRiskWorkOrders;
    /** Cap applied to each list. */
    private int listLimit;
}
