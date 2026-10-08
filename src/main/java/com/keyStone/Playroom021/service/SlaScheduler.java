package com.keyStone.Playroom021.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic trigger for {@link SlaMonitorService}. No business logic here. Disable with
 * {@code app.sla.scheduler.enabled=false}; tune with {@code app.sla.check-interval-ms}
 * (default 60000) and {@code app.sla.check-initial-delay-ms} (default 30000).
 */
@Component
@ConditionalOnProperty(name = "app.sla.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SlaScheduler {

    private final SlaMonitorService slaMonitorService;

    @Scheduled(fixedDelayString = "${app.sla.check-interval-ms:60000}",
            initialDelayString = "${app.sla.check-initial-delay-ms:30000}")
    public void run() {
        try {
            SlaCheckResult result = slaMonitorService.checkOpenWorkOrders();
            if (result.statusChanges() > 0) {
                log.info("SLA check: {} checked, {} newly at risk, {} newly breached",
                        result.checked(), result.newlyAtRisk(), result.newlyBreached());
            }
        } catch (RuntimeException e) {
            // Never let one failed sweep stop future runs.
            log.error("SLA check failed", e);
        }
    }
}
