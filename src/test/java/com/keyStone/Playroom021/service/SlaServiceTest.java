package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.entity.Priority;
import com.keyStone.Playroom021.entity.SlaStatus;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests of the SLA rules (no Spring, fixed clock). NOT RUN in the authoring environment. */
class SlaServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    private final SlaService sla = new SlaService(Clock.fixed(T0, ZoneOffset.UTC), 4, 24, 48, 72, 25);

    private WorkOrder wo(WorkOrderStatus status, Priority priority, Instant createdAt) {
        WorkOrder wo = WorkOrder.builder()
                .status(status).priority(priority).createdAt(createdAt)
                .slaDueAt(createdAt.plus(sla.targetFor(priority)))
                .build();
        return wo;
    }

    // ---------------------------------------------------------------- calculation

    @Test
    void calculateDueAt_usesTargetHoursByPriority() {
        assertEquals(T0.plus(Duration.ofHours(4)), sla.calculateDueAt(Priority.CRITICAL, T0));
        assertEquals(T0.plus(Duration.ofHours(24)), sla.calculateDueAt(Priority.HIGH, T0));
        assertEquals(T0.plus(Duration.ofHours(48)), sla.calculateDueAt(Priority.MEDIUM, T0));
        assertEquals(T0.plus(Duration.ofHours(72)), sla.calculateDueAt(Priority.LOW, T0));
    }

    @Test
    void targets_areConfigurable() {
        SlaService custom = new SlaService(Clock.fixed(T0, ZoneOffset.UTC), 1, 2, 3, 5, 10);
        assertEquals(Duration.ofHours(1), custom.targetFor(Priority.CRITICAL));
        assertEquals(Duration.ofHours(5), custom.targetFor(Priority.LOW));
    }

    @Test
    void invalidConfiguration_isRejected() {
        Clock c = Clock.fixed(T0, ZoneOffset.UTC);
        assertThrows(IllegalArgumentException.class, () -> new SlaService(c, 0, 24, 48, 72, 25));
        assertThrows(IllegalArgumentException.class, () -> new SlaService(c, 4, 24, 48, -1, 25));
        assertThrows(IllegalArgumentException.class, () -> new SlaService(c, 4, 24, 48, 72, 101));
        assertThrows(IllegalArgumentException.class, () -> new SlaService(c, 4, 24, 48, 72, -1));
    }

    @Test
    void initialize_setsDueFromClockAndStartsOnTrack() {
        WorkOrder wo = WorkOrder.builder().priority(Priority.CRITICAL).status(WorkOrderStatus.NEW).build();
        sla.initialize(wo);
        assertEquals(T0.plus(Duration.ofHours(4)), wo.getSlaDueAt());
        assertEquals(SlaStatus.ON_TRACK, wo.getSlaStatus());
        assertNull(wo.getSlaAtRiskAt());
        assertNull(wo.getSlaBreachedAt());
    }

    // ---------------------------------------------------------------- status: open work

    @Test
    void openWork_onTrackUntilTheAtRiskWindow() {
        WorkOrder wo = wo(WorkOrderStatus.IN_PROGRESS, Priority.HIGH, T0);   // due T0+24h, at-risk from T0+18h
        Instant due = wo.getSlaDueAt();
        assertEquals(SlaStatus.ON_TRACK, sla.evaluate(wo, T0));
        assertEquals(SlaStatus.ON_TRACK, sla.evaluate(wo, T0.plus(Duration.ofHours(17))));
        assertEquals(SlaStatus.ON_TRACK, sla.evaluate(wo, due.minus(Duration.ofHours(6)).minusMillis(1)));
    }

    @Test
    void openWork_atRiskFromExactlyTheThresholdUntilDueInclusive() {
        WorkOrder wo = wo(WorkOrderStatus.ASSIGNED, Priority.HIGH, T0);
        Instant due = wo.getSlaDueAt();
        assertEquals(SlaStatus.AT_RISK, sla.evaluate(wo, due.minus(Duration.ofHours(6))));     // exactly 25% left
        assertEquals(SlaStatus.AT_RISK, sla.evaluate(wo, due.minus(Duration.ofMinutes(1))));
        assertEquals(SlaStatus.AT_RISK, sla.evaluate(wo, due));                                 // due instant is not late
    }

    @Test
    void openWork_breachedOnlyStrictlyAfterDue() {
        WorkOrder wo = wo(WorkOrderStatus.NEW, Priority.CRITICAL, T0);
        Instant due = wo.getSlaDueAt();
        assertEquals(SlaStatus.BREACHED, sla.evaluate(wo, due.plusMillis(1)));
        assertEquals(SlaStatus.BREACHED, sla.evaluate(wo, due.plus(Duration.ofDays(30))));
    }

    @Test
    void onHold_keepsTheClockRunning() {
        WorkOrder wo = wo(WorkOrderStatus.ON_HOLD, Priority.LOW, T0);
        assertEquals(SlaStatus.ON_TRACK, sla.evaluate(wo, T0.plus(Duration.ofHours(10))));
        assertEquals(SlaStatus.AT_RISK, sla.evaluate(wo, T0.plus(Duration.ofHours(60))));
        assertEquals(SlaStatus.BREACHED, sla.evaluate(wo, T0.plus(Duration.ofHours(73))));
    }

    @Test
    void atRiskThreshold_scalesWithPriorityWindow() {
        WorkOrder critical = wo(WorkOrderStatus.NEW, Priority.CRITICAL, T0);   // 4h window -> at risk after 3h
        assertEquals(SlaStatus.ON_TRACK, sla.evaluate(critical, T0.plus(Duration.ofMinutes(179))));
        assertEquals(SlaStatus.AT_RISK, sla.evaluate(critical, T0.plus(Duration.ofHours(3))));
    }

    // ---------------------------------------------------------------- status: finished / n.a.

    @Test
    void finishedWork_isJudgedByCompletionTime() {
        WorkOrder onTime = wo(WorkOrderStatus.COMPLETED, Priority.HIGH, T0);
        onTime.setCompletedAt(onTime.getSlaDueAt());                                   // exactly on the due time = met
        // evaluated long afterwards: still met, never turns into a breach
        assertEquals(SlaStatus.ON_TRACK, sla.evaluate(onTime, T0.plus(Duration.ofDays(365))));

        WorkOrder late = wo(WorkOrderStatus.CLOSED, Priority.HIGH, T0);
        late.setCompletedAt(late.getSlaDueAt().plusMillis(1));
        assertEquals(SlaStatus.BREACHED, sla.evaluate(late, T0));
    }

    @Test
    void finishedWork_neverAtRisk() {
        WorkOrder wo = wo(WorkOrderStatus.COMPLETED, Priority.HIGH, T0);
        wo.setCompletedAt(wo.getSlaDueAt().minusSeconds(1));
        assertEquals(SlaStatus.ON_TRACK, sla.evaluate(wo, wo.getSlaDueAt().minusSeconds(1)));
    }

    @Test
    void finishedWithUnknownCompletion_hasNoStatus() {
        WorkOrder wo = wo(WorkOrderStatus.COMPLETED, Priority.HIGH, T0);
        assertNull(sla.evaluate(wo, T0));
    }

    @Test
    void cancelledOrUntimedWork_hasNoStatus() {
        assertNull(sla.evaluate(wo(WorkOrderStatus.CANCELLED, Priority.HIGH, T0), T0.plus(Duration.ofDays(9))));
        WorkOrder noDue = wo(WorkOrderStatus.NEW, Priority.HIGH, T0);
        noDue.setSlaDueAt(null);
        assertNull(sla.evaluate(noDue, T0));
        assertNull(sla.currentStatusName(wo(WorkOrderStatus.CANCELLED, Priority.LOW, T0)));
    }

    @Test
    void currentStatusName_usesTheClock() {
        // clock is fixed at T0, work order created 30h ago with a 24h target -> breached
        WorkOrder old = wo(WorkOrderStatus.NEW, Priority.HIGH, T0.minus(Duration.ofHours(30)));
        assertEquals("BREACHED", sla.currentStatusName(old));
        assertEquals("ON_TRACK", sla.currentStatusName(wo(WorkOrderStatus.NEW, Priority.HIGH, T0)));
    }

    // ---------------------------------------------------------------- applyTo (recorded state)

    @Test
    void applyTo_recordsStatusAndFirstSeenTimesOnce() {
        WorkOrder wo = wo(WorkOrderStatus.IN_PROGRESS, Priority.HIGH, T0);
        sla.initialize(wo);                                      // ON_TRACK recorded at T0 (due = T0+24h)
        wo.setCreatedAt(T0);

        Instant atRiskTime = T0.plus(Duration.ofHours(20));
        assertTrue(sla.applyTo(wo, atRiskTime));
        assertEquals(SlaStatus.AT_RISK, wo.getSlaStatus());
        assertEquals(atRiskTime, wo.getSlaAtRiskAt());
        assertNull(wo.getSlaBreachedAt());

        assertFalse(sla.applyTo(wo, atRiskTime.plusSeconds(60)));            // no change -> false
        assertEquals(atRiskTime, wo.getSlaAtRiskAt());                       // first-seen time kept

        Instant breachTime = T0.plus(Duration.ofHours(25));
        assertTrue(sla.applyTo(wo, breachTime));
        assertEquals(SlaStatus.BREACHED, wo.getSlaStatus());
        assertEquals(breachTime, wo.getSlaBreachedAt());
        assertEquals(atRiskTime, wo.getSlaAtRiskAt());

        assertFalse(sla.applyTo(wo, breachTime.plus(Duration.ofHours(5))));
        assertEquals(breachTime, wo.getSlaBreachedAt());
    }

    @Test
    void applyTo_cancelledClearsStatus() {
        WorkOrder wo = wo(WorkOrderStatus.CANCELLED, Priority.HIGH, T0);
        wo.setSlaStatus(SlaStatus.ON_TRACK);
        assertTrue(sla.applyTo(wo, T0));
        assertNull(wo.getSlaStatus());
    }

    // ---------------------------------------------------------------- priority change

    @Test
    void priorityChange_onOpenWork_retimesFromCreation() {
        WorkOrder wo = wo(WorkOrderStatus.ASSIGNED, Priority.LOW, T0);         // due T0+72h
        wo.setPriority(Priority.CRITICAL);
        sla.onPriorityChange(wo, T0.plus(Duration.ofHours(1)));
        assertEquals(T0.plus(Duration.ofHours(4)), wo.getSlaDueAt());
        assertEquals(SlaStatus.ON_TRACK, wo.getSlaStatus());
    }

    @Test
    void priorityChange_loweringPriorityExtendsDueWhileNotBreached() {
        WorkOrder wo = wo(WorkOrderStatus.NEW, Priority.CRITICAL, T0);         // due T0+4h
        wo.setPriority(Priority.LOW);
        sla.onPriorityChange(wo, T0.plus(Duration.ofHours(1)));
        assertEquals(T0.plus(Duration.ofHours(72)), wo.getSlaDueAt());
    }

    @Test
    void priorityChange_cannotEditAwayABreach() {
        WorkOrder wo = wo(WorkOrderStatus.IN_PROGRESS, Priority.CRITICAL, T0);   // due T0+4h
        Instant originalDue = wo.getSlaDueAt();
        wo.setPriority(Priority.LOW);
        sla.onPriorityChange(wo, T0.plus(Duration.ofHours(10)));                 // already breached
        assertEquals(originalDue, wo.getSlaDueAt());
    }

    @Test
    void priorityChange_onFinishedWork_doesNotRetime() {
        WorkOrder wo = wo(WorkOrderStatus.COMPLETED, Priority.CRITICAL, T0);
        wo.setCompletedAt(T0.plus(Duration.ofHours(1)));
        Instant originalDue = wo.getSlaDueAt();
        wo.setPriority(Priority.LOW);
        sla.onPriorityChange(wo, T0.plus(Duration.ofHours(2)));
        assertEquals(originalDue, wo.getSlaDueAt());
    }

    @Test
    void priorityChange_raisingPriorityOnOldWork_breachesImmediately() {
        WorkOrder wo = wo(WorkOrderStatus.NEW, Priority.LOW, T0);                // due T0+72h
        wo.setPriority(Priority.CRITICAL);                                       // new due T0+4h
        sla.onPriorityChange(wo, T0.plus(Duration.ofHours(10)));
        assertEquals(T0.plus(Duration.ofHours(4)), wo.getSlaDueAt());
        assertEquals(SlaStatus.BREACHED, wo.getSlaStatus());
        assertEquals(T0.plus(Duration.ofHours(10)), wo.getSlaBreachedAt());
    }
}
