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
public class TimeLogResponse {
    private Long id;
    private Long workOrderId;
    private Long technicianId;
    private String technician;
    private Instant startedAt;
    private Instant endedAt;
    private int minutes;
    private String note;
    private Instant createdAt;
}
