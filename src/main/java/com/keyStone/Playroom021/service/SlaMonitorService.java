package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.entity.SlaStatus;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * One SLA sweep: re-evaluates open work orders (via {@link SlaService}, where the rules live) and records
 * the first time each became AT_RISK or BREACHED. Idempotent - a second run with no time-based change
 * touches nothing. Run by {@code SlaScheduler}; callable directly (tests, future admin endpoint).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaMonitorService {

    private final WorkOrderRepository workOrderRepository;
    private final SlaService slaService;

    @Transactional
    public SlaCheckResult checkOpenWorkOrders() {
        Instant now = slaService.now();
        List<WorkOrder> candidates = workOrderRepository.findOpenForSlaCheck(SlaService.OPEN_STATUSES, SlaStatus.BREACHED);

        int atRisk = 0;
        int breached = 0;
        int changes = 0;
        for (WorkOrder wo : candidates) {
            SlaStatus previous = wo.getSlaStatus();
            if (!slaService.applyTo(wo, now)) {
                continue;
            }
            changes++;
            if (wo.getSlaStatus() == SlaStatus.BREACHED) {
                breached++;
                log.warn("SLA breached: {} ({} priority, status {}, was due {})",
                        wo.getCode(), wo.getPriority(), wo.getStatus(), wo.getSlaDueAt());
            } else if (wo.getSlaStatus() == SlaStatus.AT_RISK) {
                atRisk++;
                log.info("SLA at risk: {} ({} priority, status {}, due {})",
                        wo.getCode(), wo.getPriority(), wo.getStatus(), wo.getSlaDueAt());
            } else {
                log.debug("SLA status for {} recorded as {} (was {})", wo.getCode(), wo.getSlaStatus(), previous);
            }
        }
        return new SlaCheckResult(candidates.size(), atRisk, breached, changes);
    }
}
