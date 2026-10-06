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
public class TechnicianWorkloadResponse {
    private Instant generatedAt;
    /** Every technician (including those with no work), busiest first. */
    private List<TechnicianWorkload> technicians;
    /** Open work orders with no technician (e.g. NEW). */
    private long unassignedOpen;
}
