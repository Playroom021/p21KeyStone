package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianWorkload {
    private Long technicianId;
    private String technician;
    private long assigned;
    private long inProgress;
    private long onHold;
    /** assigned + inProgress + onHold. */
    private long openTotal;
    /** Open and past SLA due time. */
    private long overdue;
    /** Open and inside the at-risk window. */
    private long atRisk;
    /** COMPLETED + CLOSED. */
    private long completed;
}
