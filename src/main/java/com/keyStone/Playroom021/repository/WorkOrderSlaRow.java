package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.Priority;
import com.keyStone.Playroom021.entity.WorkOrderStatus;

import java.time.Instant;

/** Lightweight read model for dashboard/SLA aggregation (no entity graph loaded). */
public record WorkOrderSlaRow(Long id, String code, String title, Priority priority, WorkOrderStatus status,
                              Instant slaDueAt, Instant createdAt, Long technicianId, String technicianName) {
}
