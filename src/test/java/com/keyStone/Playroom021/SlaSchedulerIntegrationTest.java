package com.keyStone.Playroom021;

import com.keyStone.Playroom021.entity.SlaStatus;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.service.SlaScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the scheduled job really fires on its own: the scheduler is enabled with a fast interval and the
 * test only waits for the recorded SLA state to change. The context is discarded afterwards so its
 * background thread does not keep running during other tests. NOT RUN in the authoring environment.
 */
@SpringBootTest(properties = {
        "app.sla.scheduler.enabled=true",
        "app.sla.check-interval-ms=200",
        "app.sla.check-initial-delay-ms=0"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SlaSchedulerIntegrationTest extends AbstractApiTest {

    @Autowired
    private SlaScheduler slaScheduler;

    @Test
    void schedulerBeanExists_andIsAnnotatedWithScheduled() throws Exception {
        Method run = SlaScheduler.class.getMethod("run");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "SlaScheduler.run must be @Scheduled");
        assertFalse(scheduled.fixedDelayString().isBlank());
        assertNotNull(slaScheduler);
    }

    @Test
    void scheduler_marksOverdueWorkOrderBreached_withoutAnyApiCall() throws Exception {
        String manager = signupToken("MANAGER", null);
        long id = newWorkOrder(manager, newSite(manager), "HIGH");
        assertEquals(SlaStatus.ON_TRACK, reload(id).getSlaStatus());

        setDue(id, Instant.now().minus(30, ChronoUnit.MINUTES));

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            WorkOrder wo = reload(id);
            assertEquals(SlaStatus.BREACHED, wo.getSlaStatus());
            assertNotNull(wo.getSlaBreachedAt());
        });
    }

    @Test
    void scheduler_marksNearlyDueWorkOrderAtRisk() throws Exception {
        String manager = signupToken("MANAGER", null);
        long id = newWorkOrder(manager, newSite(manager), "HIGH");
        Instant now = Instant.now();
        setWindow(id, now.minus(20, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            WorkOrder wo = reload(id);
            assertEquals(SlaStatus.AT_RISK, wo.getSlaStatus());
            assertNotNull(wo.getSlaAtRiskAt());
        });
    }
}
