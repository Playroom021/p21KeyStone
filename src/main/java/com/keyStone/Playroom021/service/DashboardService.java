package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.DashboardSummaryResponse;
import com.keyStone.Playroom021.dto.SlaReportResponse;
import com.keyStone.Playroom021.dto.SlaSummary;
import com.keyStone.Playroom021.dto.SlaWorkOrderItem;
import com.keyStone.Playroom021.dto.TechnicianWorkload;
import com.keyStone.Playroom021.dto.TechnicianWorkloadResponse;
import com.keyStone.Playroom021.entity.Role;
import com.keyStone.Playroom021.entity.SlaStatus;
import com.keyStone.Playroom021.entity.User;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.repository.UserRepository;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import com.keyStone.Playroom021.repository.WorkOrderSlaRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only dashboard/report aggregation (MANAGER/DISPATCHER; gated in SecurityConfig).
 *
 * Definitions:
 * <ul>
 *   <li><b>open</b> = NEW, ASSIGNED, IN_PROGRESS, ON_HOLD; <b>completed</b> = COMPLETED + CLOSED.</li>
 *   <li><b>overdue</b> = open and past its SLA due time right now.</li>
 *   <li><b>SLA breaches</b> = overdue + finished work completed after its due time.</li>
 *   <li><b>SLA compliance</b> = met / (met + breaches). Open work not yet due is undecided and excluded;
 *       cancelled work has no SLA. Null when there is nothing decided yet.</li>
 * </ul>
 * All statuses come from {@link SlaService}'s live evaluation, so the numbers never lag the scheduler.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int LIST_LIMIT = 50;

    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;
    private final SlaService slaService;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        Instant now = slaService.now();
        Map<WorkOrderStatus, Long> byStatus = countsByStatus();

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long open = sum(byStatus, SlaService.OPEN_STATUSES);
        long completed = sum(byStatus, SlaService.FINISHED_STATUSES);
        long cancelled = byStatus.get(WorkOrderStatus.CANCELLED);

        List<WorkOrderSlaRow> openRows = workOrderRepository.findSlaRowsByStatusIn(SlaService.OPEN_STATUSES);
        SlaSummary sla = buildSlaSummary(openRows, now);

        Map<String, Long> named = new LinkedHashMap<>();
        byStatus.forEach((k, v) -> named.put(k.name(), v));

        return DashboardSummaryResponse.builder()
                .generatedAt(now)
                .totalWorkOrders(total)
                .openWorkOrders(open)
                .completedWorkOrders(completed)
                .cancelledWorkOrders(cancelled)
                .overdueWorkOrders(sla.getBreachedOpen())
                .countsByStatus(named)
                .sla(sla)
                .build();
    }

    @Transactional(readOnly = true)
    public SlaReportResponse slaReport() {
        Instant now = slaService.now();
        List<WorkOrderSlaRow> openRows = workOrderRepository.findSlaRowsByStatusIn(SlaService.OPEN_STATUSES);

        List<SlaWorkOrderItem> breached = new ArrayList<>();
        List<SlaWorkOrderItem> atRisk = new ArrayList<>();
        for (WorkOrderSlaRow row : openRows) {
            SlaStatus status = statusOf(row, now);
            if (status == SlaStatus.BREACHED) {
                breached.add(toItem(row, status, Duration.between(row.slaDueAt(), now).toMinutes(), null));
            } else if (status == SlaStatus.AT_RISK) {
                atRisk.add(toItem(row, status, null, Duration.between(now, row.slaDueAt()).toMinutes()));
            }
        }
        Comparator<SlaWorkOrderItem> byDue = Comparator.comparing(SlaWorkOrderItem::getSlaDueAt)
                .thenComparing(SlaWorkOrderItem::getId);
        breached.sort(byDue);
        atRisk.sort(byDue);

        return SlaReportResponse.builder()
                .generatedAt(now)
                .summary(buildSlaSummary(openRows, now))
                .breachedWorkOrders(breached.size() > LIST_LIMIT ? new ArrayList<>(breached.subList(0, LIST_LIMIT)) : breached)
                .atRiskWorkOrders(atRisk.size() > LIST_LIMIT ? new ArrayList<>(atRisk.subList(0, LIST_LIMIT)) : atRisk)
                .listLimit(LIST_LIMIT)
                .build();
    }

    @Transactional(readOnly = true)
    public TechnicianWorkloadResponse technicianWorkload() {
        Instant now = slaService.now();
        List<WorkOrderSlaRow> openRows = workOrderRepository.findSlaRowsByStatusIn(SlaService.OPEN_STATUSES);

        Map<Long, long[]> acc = new LinkedHashMap<>();   // [assigned, inProgress, onHold, overdue, atRisk, completed]
        Map<Long, String> names = new LinkedHashMap<>();
        for (User t : userRepository.findByRole(Role.TECHNICIAN)) {
            acc.put(t.getId(), new long[6]);
            names.put(t.getId(), t.getFullName());
        }

        long unassignedOpen = 0;
        for (WorkOrderSlaRow row : openRows) {
            if (row.technicianId() == null) {
                unassignedOpen++;
                continue;
            }
            long[] a = acc.computeIfAbsent(row.technicianId(), k -> new long[6]);
            names.putIfAbsent(row.technicianId(), row.technicianName());
            switch (row.status()) {
                case ASSIGNED -> a[0]++;
                case IN_PROGRESS -> a[1]++;
                case ON_HOLD -> a[2]++;
                default -> { /* NEW with a technician cannot normally happen; not counted as workload */ }
            }
            SlaStatus status = statusOf(row, now);
            if (status == SlaStatus.BREACHED) {
                a[3]++;
            } else if (status == SlaStatus.AT_RISK) {
                a[4]++;
            }
        }
        for (Object[] r : workOrderRepository.countByTechnicianAndStatusIn(SlaService.FINISHED_STATUSES)) {
            long[] a = acc.computeIfAbsent((Long) r[0], k -> new long[6]);
            a[5] = (Long) r[1];
        }

        List<TechnicianWorkload> list = new ArrayList<>();
        acc.forEach((id, a) -> list.add(TechnicianWorkload.builder()
                .technicianId(id)
                .technician(names.get(id))
                .assigned(a[0])
                .inProgress(a[1])
                .onHold(a[2])
                .openTotal(a[0] + a[1] + a[2])
                .overdue(a[3])
                .atRisk(a[4])
                .completed(a[5])
                .build()));
        list.sort(Comparator.comparingLong(TechnicianWorkload::getOpenTotal).reversed()
                .thenComparing(w -> w.getTechnician() == null ? "" : w.getTechnician())
                .thenComparing(TechnicianWorkload::getTechnicianId));

        return TechnicianWorkloadResponse.builder()
                .generatedAt(now)
                .technicians(list)
                .unassignedOpen(unassignedOpen)
                .build();
    }

    // ---- helpers ----

    private Map<WorkOrderStatus, Long> countsByStatus() {
        Map<WorkOrderStatus, Long> map = new EnumMap<>(WorkOrderStatus.class);
        for (WorkOrderStatus s : WorkOrderStatus.values()) {
            map.put(s, 0L);
        }
        for (Object[] r : workOrderRepository.countGroupedByStatus()) {
            map.put((WorkOrderStatus) r[0], (Long) r[1]);
        }
        return map;
    }

    private static long sum(Map<WorkOrderStatus, Long> byStatus, Iterable<WorkOrderStatus> statuses) {
        long total = 0;
        for (WorkOrderStatus s : statuses) {
            total += byStatus.get(s);
        }
        return total;
    }

    private SlaStatus statusOf(WorkOrderSlaRow row, Instant now) {
        return slaService.evaluate(row.status(), row.priority(), row.createdAt(), row.slaDueAt(), null, now);
    }

    SlaSummary buildSlaSummary(List<WorkOrderSlaRow> openRows, Instant now) {
        long onTrack = 0;
        long atRisk = 0;
        long breachedOpen = 0;
        for (WorkOrderSlaRow row : openRows) {
            SlaStatus status = statusOf(row, now);
            if (status == SlaStatus.BREACHED) {
                breachedOpen++;
            } else if (status == SlaStatus.AT_RISK) {
                atRisk++;
            } else if (status == SlaStatus.ON_TRACK) {
                onTrack++;
            }
        }
        long met = workOrderRepository.countFinishedWithinSla(SlaService.FINISHED_STATUSES);
        long late = workOrderRepository.countFinishedLate(SlaService.FINISHED_STATUSES);
        long totalBreaches = breachedOpen + late;
        return SlaSummary.builder()
                .onTrackOpen(onTrack)
                .atRisk(atRisk)
                .breachedOpen(breachedOpen)
                .breachedCompleted(late)
                .totalBreaches(totalBreaches)
                .metOnTime(met)
                .compliancePercent(compliance(met, totalBreaches))
                .build();
    }

    static Double compliance(long met, long breaches) {
        long decided = met + breaches;
        if (decided == 0) {
            return null;
        }
        return BigDecimal.valueOf(met * 100.0 / decided).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private SlaWorkOrderItem toItem(WorkOrderSlaRow row, SlaStatus status, Long minutesOverdue, Long minutesRemaining) {
        return SlaWorkOrderItem.builder()
                .id(row.id())
                .code(row.code())
                .title(row.title())
                .priority(row.priority().name())
                .status(row.status().name())
                .assignedTechnicianId(row.technicianId())
                .assignedTechnician(row.technicianName())
                .slaDueAt(row.slaDueAt())
                .slaStatus(status.name())
                .minutesOverdue(minutesOverdue)
                .minutesRemaining(minutesRemaining)
                .build();
    }
}
