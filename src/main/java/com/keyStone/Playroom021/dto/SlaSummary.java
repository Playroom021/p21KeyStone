package com.keyStone.Playroom021.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaSummary {
    /** Open work still comfortably inside its SLA window. */
    private long onTrackOpen;
    /** Open work not yet late but inside the at-risk window. */
    private long atRisk;
    /** Open work already past its due time (same set as "overdue"). */
    private long breachedOpen;
    /** Finished work that was completed after its due time. */
    private long breachedCompleted;
    /** breachedOpen + breachedCompleted. */
    private long totalBreaches;
    /** Finished work completed on or before its due time. */
    private long metOnTime;
    /** metOnTime / (metOnTime + totalBreaches) * 100, 2 decimals; null when nothing has been decided yet. */
    private Double compliancePercent;
}
