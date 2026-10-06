package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.DashboardSummaryResponse;
import com.keyStone.Playroom021.dto.SlaReportResponse;
import com.keyStone.Playroom021.dto.TechnicianWorkload;
import com.keyStone.Playroom021.dto.TechnicianWorkloadResponse;
import com.keyStone.Playroom021.entity.Priority;
import com.keyStone.Playroom021.entity.Role;
import com.keyStone.Playroom021.entity.User;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.repository.UserRepository;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import com.keyStone.Playroom021.repository.WorkOrderSlaRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deterministic unit tests of the dashboard aggregation (mocked repositories, fixed clock), including the
 * compliance arithmetic. NOT RUN in the authoring environment.
 */
class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private WorkOrderRepository workOrderRepository;
    private UserRepository userRepository;
    private DashboardService dashboard;

    private static WorkOrderSlaRow row(long id, WorkOrderStatus status, Instant created, Instant due,
                                       Long techId, String techName) {
        return new WorkOrderSlaRow(id, "WO-" + id, "Job " + id, Priority.HIGH, status, due, created, techId, techName);
    }

    private static User tech(long id, String name) {
        return User.builder().id(id).fullName(name).role(Role.TECHNICIAN).build();
    }

    @BeforeEach
    void setUp() {
        workOrderRepository = mock(WorkOrderRepository.class);
        userRepository = mock(UserRepository.class);
        SlaService sla = new SlaService(Clock.fixed(NOW, ZoneOffset.UTC), 4, 24, 48, 72, 25);
        dashboard = new DashboardService(workOrderRepository, userRepository, sla);

        // HIGH = 24h window, at risk with <= 6h left.
        Instant onTrackCreated = NOW.minus(Duration.ofHours(10));      // due NOW+14h  -> ON_TRACK
        Instant atRiskCreated = NOW.minus(Duration.ofHours(20));       // due NOW+4h   -> AT_RISK
        Instant overdue1Created = NOW.minus(Duration.ofHours(30));     // due NOW-6h   -> BREACHED (360 min)
        Instant overdue2Created = NOW.minus(Duration.ofHours(25));     // due NOW-1h   -> BREACHED (60 min)
        List<WorkOrderSlaRow> openRows = List.of(
                row(1, WorkOrderStatus.NEW, onTrackCreated, onTrackCreated.plus(Duration.ofHours(24)), null, null),
                row(2, WorkOrderStatus.NEW, overdue2Created, overdue2Created.plus(Duration.ofHours(24)), null, null),
                row(3, WorkOrderStatus.ASSIGNED, atRiskCreated, atRiskCreated.plus(Duration.ofHours(24)), 1L, "Alice"),
                row(4, WorkOrderStatus.IN_PROGRESS, overdue1Created, overdue1Created.plus(Duration.ofHours(24)), 1L, "Alice"));

        when(workOrderRepository.findSlaRowsByStatusIn(any())).thenReturn(openRows);
        when(workOrderRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{WorkOrderStatus.NEW, 2L},
                new Object[]{WorkOrderStatus.ASSIGNED, 1L},
                new Object[]{WorkOrderStatus.IN_PROGRESS, 1L},
                new Object[]{WorkOrderStatus.COMPLETED, 3L},
                new Object[]{WorkOrderStatus.CLOSED, 1L},
                new Object[]{WorkOrderStatus.CANCELLED, 2L}));
        when(workOrderRepository.countFinishedWithinSla(any())).thenReturn(3L);
        when(workOrderRepository.countFinishedLate(any())).thenReturn(1L);
        when(workOrderRepository.countByTechnicianAndStatusIn(any())).thenReturn(List.<Object[]>of(new Object[]{1L, 2L}));
        when(userRepository.findByRole(Role.TECHNICIAN)).thenReturn(List.of(tech(2, "Bob"), tech(1, "Alice")));
    }

    @Test
    void summary_countsByStatusGroup() {
        DashboardSummaryResponse s = dashboard.summary();
        assertEquals(10, s.getTotalWorkOrders());
        assertEquals(4, s.getOpenWorkOrders());          // NEW 2 + ASSIGNED 1 + IN_PROGRESS 1 + ON_HOLD 0
        assertEquals(4, s.getCompletedWorkOrders());     // COMPLETED 3 + CLOSED 1
        assertEquals(2, s.getCancelledWorkOrders());
        assertEquals(0, s.getCountsByStatus().get("ON_HOLD"));
        assertEquals(2, s.getCountsByStatus().get("NEW"));
        assertEquals(7, s.getCountsByStatus().size());
    }

    @Test
    void summary_overdueSlaBreachesAndCompliance() {
        DashboardSummaryResponse s = dashboard.summary();
        assertEquals(2, s.getOverdueWorkOrders());
        assertEquals(1, s.getSla().getOnTrackOpen());
        assertEquals(1, s.getSla().getAtRisk());
        assertEquals(2, s.getSla().getBreachedOpen());
        assertEquals(1, s.getSla().getBreachedCompleted());
        assertEquals(3, s.getSla().getTotalBreaches());   // 2 overdue + 1 finished late
        assertEquals(3, s.getSla().getMetOnTime());
        assertEquals(50.00, s.getSla().getCompliancePercent());   // 3 met / (3 met + 3 breaches)
    }

    @Test
    void compliance_arithmeticAndEmptyCase() {
        assertNull(DashboardService.compliance(0, 0));
        assertEquals(100.0, DashboardService.compliance(5, 0));
        assertEquals(0.0, DashboardService.compliance(0, 4));
        assertEquals(33.33, DashboardService.compliance(1, 2));
        assertEquals(66.67, DashboardService.compliance(2, 1));
    }

    @Test
    void slaReport_listsSortedWithMinutes() {
        SlaReportResponse r = dashboard.slaReport();
        assertEquals(2, r.getBreachedWorkOrders().size());
        assertEquals(4L, r.getBreachedWorkOrders().get(0).getId());          // most overdue (due earliest) first
        assertEquals(360L, r.getBreachedWorkOrders().get(0).getMinutesOverdue());
        assertEquals(60L, r.getBreachedWorkOrders().get(1).getMinutesOverdue());
        assertEquals("BREACHED", r.getBreachedWorkOrders().get(0).getSlaStatus());
        assertEquals(1, r.getAtRiskWorkOrders().size());
        assertEquals(3L, r.getAtRiskWorkOrders().get(0).getId());
        assertEquals(240L, r.getAtRiskWorkOrders().get(0).getMinutesRemaining());
        assertNull(r.getAtRiskWorkOrders().get(0).getMinutesOverdue());
        assertEquals(DashboardService.LIST_LIMIT, r.getListLimit());
        assertEquals(3, r.getSummary().getTotalBreaches());
    }

    @Test
    void technicianWorkload_perTechnicianIncludingIdleOnes() {
        TechnicianWorkloadResponse r = dashboard.technicianWorkload();
        assertEquals(2, r.getUnassignedOpen());
        assertEquals(2, r.getTechnicians().size());

        TechnicianWorkload alice = r.getTechnicians().get(0);       // busiest first
        assertEquals("Alice", alice.getTechnician());
        assertEquals(1, alice.getAssigned());
        assertEquals(1, alice.getInProgress());
        assertEquals(0, alice.getOnHold());
        assertEquals(2, alice.getOpenTotal());
        assertEquals(1, alice.getOverdue());
        assertEquals(1, alice.getAtRisk());
        assertEquals(2, alice.getCompleted());

        TechnicianWorkload bob = r.getTechnicians().get(1);
        assertEquals("Bob", bob.getTechnician());
        assertEquals(0, bob.getOpenTotal());
        assertEquals(0, bob.getCompleted());
    }
}
