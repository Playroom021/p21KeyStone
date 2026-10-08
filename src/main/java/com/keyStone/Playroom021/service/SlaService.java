package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.entity.Priority;
import com.keyStone.Playroom021.entity.SlaStatus;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * All SLA business rules live here (nothing SLA-related is decided in controllers or repositories).
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li><b>Target by priority</b> (hours from creation): CRITICAL 4, HIGH 24, MEDIUM 48, LOW 72
 *       (configurable via {@code app.sla.*-hours}). {@code slaDueAt = createdAt + target}.</li>
 *   <li><b>Open work</b> (NEW, ASSIGNED, IN_PROGRESS, ON_HOLD) — the clock keeps running while ON_HOLD:
 *       <ul>
 *         <li>{@code BREACHED} if now is strictly after the due time;</li>
 *         <li>{@code AT_RISK} if not breached and the time remaining is at most
 *             {@code app.sla.at-risk-percent} (default 25%) of the whole SLA window (inclusive);</li>
 *         <li>{@code ON_TRACK} otherwise.</li>
 *       </ul></li>
 *   <li><b>Finished work</b> (COMPLETED, CLOSED) is judged by when it was completed: on or before the due time
 *       is {@code ON_TRACK} (SLA met), after it is {@code BREACHED}. Never AT_RISK. If the completion time is
 *       unknown the status is null.</li>
 *   <li><b>CANCELLED</b> work, or work with no due time, has no SLA status (null).</li>
 *   <li><b>Priority change</b> on open work re-derives the due time from the original creation time with the
 *       new priority's target, unless the work order is already BREACHED (a breach cannot be edited away).
 *       Finished work is never re-timed. Raising priority on old work can therefore breach it immediately.</li>
 * </ul>
 */
@Service
public class SlaService {

    public static final Set<WorkOrderStatus> OPEN_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD));

    public static final Set<WorkOrderStatus> FINISHED_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED));

    private final Clock clock;
    private final Map<Priority, Duration> targets = new EnumMap<>(Priority.class);
    private final int atRiskPercent;

    public SlaService(Clock clock,
                      @Value("${app.sla.critical-hours:4}") long criticalHours,
                      @Value("${app.sla.high-hours:24}") long highHours,
                      @Value("${app.sla.medium-hours:48}") long mediumHours,
                      @Value("${app.sla.low-hours:72}") long lowHours,
                      @Value("${app.sla.at-risk-percent:25}") int atRiskPercent) {
        if (criticalHours <= 0 || highHours <= 0 || mediumHours <= 0 || lowHours <= 0) {
            throw new IllegalArgumentException("SLA target hours must all be greater than 0");
        }
        if (atRiskPercent < 0 || atRiskPercent > 100) {
            throw new IllegalArgumentException("app.sla.at-risk-percent must be between 0 and 100");
        }
        this.clock = clock;
        this.targets.put(Priority.CRITICAL, Duration.ofHours(criticalHours));
        this.targets.put(Priority.HIGH, Duration.ofHours(highHours));
        this.targets.put(Priority.MEDIUM, Duration.ofHours(mediumHours));
        this.targets.put(Priority.LOW, Duration.ofHours(lowHours));
        this.atRiskPercent = atRiskPercent;
    }

    public Instant now() {
        return clock.instant();
    }

    // ---- calculation ----

    public Duration targetFor(Priority priority) {
        return targets.get(priority);
    }

    public Instant calculateDueAt(Priority priority, Instant from) {
        return from.plus(targetFor(priority));
    }

    /** Called on a brand-new work order, before it is first saved. */
    public void initialize(WorkOrder wo) {
        wo.setSlaDueAt(calculateDueAt(wo.getPriority(), now()));
        wo.setSlaStatus(SlaStatus.ON_TRACK);
        wo.setSlaAtRiskAt(null);
        wo.setSlaBreachedAt(null);
    }

    // ---- status ----

    /** Live SLA status, or null when none applies (see class rules). */
    public SlaStatus evaluate(WorkOrder wo, Instant now) {
        return evaluate(wo.getStatus(), wo.getPriority(), wo.getCreatedAt(), wo.getSlaDueAt(), wo.getCompletedAt(), now);
    }

    public SlaStatus evaluate(WorkOrderStatus status, Priority priority, Instant createdAt, Instant dueAt,
                              Instant completedAt, Instant now) {
        if (status == WorkOrderStatus.CANCELLED || dueAt == null) {
            return null;
        }
        if (FINISHED_STATUSES.contains(status)) {
            if (completedAt == null) {
                return null;
            }
            return completedAt.isAfter(dueAt) ? SlaStatus.BREACHED : SlaStatus.ON_TRACK;
        }
        if (now.isAfter(dueAt)) {
            return SlaStatus.BREACHED;
        }
        Duration window = createdAt != null ? Duration.between(createdAt, dueAt) : targetFor(priority);
        Duration remaining = Duration.between(now, dueAt);
        // Integer arithmetic (no floating point): remaining <= window * percent / 100
        if (remaining.toMillis() * 100 <= window.toMillis() * atRiskPercent) {
            return SlaStatus.AT_RISK;
        }
        return SlaStatus.ON_TRACK;
    }

    /** Name of the live status for API responses; null when none applies. */
    public String currentStatusName(WorkOrder wo) {
        SlaStatus status = evaluate(wo, now());
        return status == null ? null : status.name();
    }

    /**
     * Re-evaluates and records the SLA state on the entity (status plus first-seen AT_RISK/BREACHED times).
     * The caller owns persistence. Returns true if the recorded status changed.
     */
    public boolean applyTo(WorkOrder wo, Instant now) {
        SlaStatus fresh = evaluate(wo, now);
        boolean changed = fresh != wo.getSlaStatus();
        wo.setSlaStatus(fresh);
        if (fresh == SlaStatus.AT_RISK && wo.getSlaAtRiskAt() == null) {
            wo.setSlaAtRiskAt(now);
        }
        if (fresh == SlaStatus.BREACHED && wo.getSlaBreachedAt() == null) {
            wo.setSlaBreachedAt(now);
        }
        return changed;
    }

    /**
     * Call after {@code wo.priority} has been changed. Re-times open, not-yet-breached work from its
     * creation time; otherwise leaves the due time alone (see class rules).
     */
    public void onPriorityChange(WorkOrder wo, Instant now) {
        if (!OPEN_STATUSES.contains(wo.getStatus()) || wo.getCreatedAt() == null) {
            return;
        }
        if (evaluate(wo, now) == SlaStatus.BREACHED) {
            return;
        }
        wo.setSlaDueAt(calculateDueAt(wo.getPriority(), wo.getCreatedAt()));
        wo.setSlaAtRiskAt(null);
        wo.setSlaBreachedAt(null);
        applyTo(wo, now);
    }
}
