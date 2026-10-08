package com.keyStone.Playroom021;

import com.fasterxml.jackson.databind.JsonNode;
import com.keyStone.Playroom021.entity.SlaStatus;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.service.SlaCheckResult;
import com.keyStone.Playroom021.service.SlaMonitorService;
import com.keyStone.Playroom021.service.SlaScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Step 7: SLA calculation, SLA status in the API, breach detection and the monitor sweep, against the real
 * stack (H2). The scheduler itself is switched off in the test profile; SlaSchedulerIntegrationTest covers it
 * firing on its own. NOT RUN in the authoring environment.
 */
class SlaIntegrationTest extends AbstractApiTest {

    @Autowired
    private SlaMonitorService slaMonitorService;

    @Autowired
    private ObjectProvider<SlaScheduler> slaScheduler;

    private String manager() throws Exception {
        return signupToken("MANAGER", null);
    }

    // ------------------------------------------------------------------ calculation

    @Test
    void create_setsSlaDueAtFromPriorityTarget() throws Exception {
        String manager = manager();
        long siteId = newSite(manager);
        Object[][] cases = {{"CRITICAL", 4L}, {"HIGH", 24L}, {"MEDIUM", 48L}, {"LOW", 72L}};
        for (Object[] c : cases) {
            long id = newWorkOrder(manager, siteId, (String) c[0]);
            JsonNode wo = workOrder(manager, id);
            Instant created = Instant.parse(wo.get("createdAt").asText());
            Instant due = Instant.parse(wo.get("slaDueAt").asText());
            Duration target = Duration.ofHours((Long) c[1]);
            // slaDueAt is computed an instant before createdAt is stamped; allow a few seconds of slack.
            long driftSeconds = Math.abs(Duration.between(created, due).minus(target).getSeconds());
            assertTrue(driftSeconds <= 5, c[0] + " due should be created + " + target + " (drift " + driftSeconds + "s)");
            assertEquals("ON_TRACK", wo.get("slaStatus").asText());
        }
    }

    @Test
    void customerPortalRequest_getsTheSameSlaRules() throws Exception {
        String customer = signupToken("CUSTOMER", "Cust " + tag());
        long siteId = idOf(postAs(customer, "/api/customer/sites",
                json("name", "Depot " + tag(), "addressLine", "2 Side St", "city", "Gotham")).andExpect(status().isCreated()));
        JsonNode created = bodyOf(postAs(customer, "/api/customer/work-orders",
                json("siteId", siteId, "title", "Leak " + tag(), "priority", "CRITICAL")).andExpect(status().isCreated()));

        Instant due = Instant.parse(created.get("slaDueAt").asText());
        long hoursAhead = Duration.between(Instant.now(), due).toHours();
        assertTrue(hoursAhead >= 3 && hoursAhead <= 4, "CRITICAL due is about 4h away, was " + hoursAhead + "h");
        assertEquals("ON_TRACK", created.get("slaStatus").asText());
    }

    // ------------------------------------------------------------------ status in the API (live)

    @Test
    void slaStatus_isEvaluatedLive_onDetailAndList() throws Exception {
        String manager = manager();
        long siteId = newSite(manager);
        long onTrack = newWorkOrder(manager, siteId, "HIGH");
        long atRisk = newWorkOrder(manager, siteId, "HIGH");
        long breached = newWorkOrder(manager, siteId, "HIGH");

        Instant now = Instant.now();
        setWindow(atRisk, now.minus(20, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));   // 4h of 24h left (<= 25%)
        setDue(breached, now.minus(1, ChronoUnit.HOURS));

        assertEquals("ON_TRACK", workOrder(manager, onTrack).get("slaStatus").asText());
        assertEquals("AT_RISK", workOrder(manager, atRisk).get("slaStatus").asText());
        assertEquals("BREACHED", workOrder(manager, breached).get("slaStatus").asText());

        // The recorded column has not been touched (no scheduler run yet), proving the API value is live.
        assertEquals(SlaStatus.ON_TRACK, reload(breached).getSlaStatus());

        JsonNode list = bodyOf(getAs(manager, "/api/work-orders?size=100&sortBy=id&direction=desc").andExpect(status().isOk()));
        boolean sawBreached = false;
        for (JsonNode item : list.get("content")) {
            if (item.get("id").asLong() == breached) {
                assertEquals("BREACHED", item.get("slaStatus").asText());
                sawBreached = true;
            }
        }
        assertTrue(sawBreached, "breached work order should appear in the list with its SLA status");
    }

    @Test
    void technicianSeesSlaStatusOfAssignedWork() throws Exception {
        String manager = manager();
        String tech = signupToken("TECHNICIAN", null);
        long id = newWorkOrder(manager, newSite(manager), "MEDIUM");
        assign(manager, id, userId(tech));
        getAs(tech, "/api/work-orders/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.slaStatus").value("ON_TRACK"));
    }

    // ------------------------------------------------------------------ finishing work

    @Test
    void completingOnTime_recordsCompletionAndKeepsSlaMet() throws Exception {
        String manager = manager();
        String tech = signupToken("TECHNICIAN", null);
        long id = newWorkOrder(manager, newSite(manager), "HIGH");
        assign(manager, id, userId(tech));
        start(tech, id);
        complete(tech, id);

        WorkOrder wo = reload(id);
        assertNotNull(wo.getCompletedAt());
        assertEquals(SlaStatus.ON_TRACK, wo.getSlaStatus());
        assertNull(wo.getSlaBreachedAt());
        assertEquals("ON_TRACK", workOrder(manager, id).get("slaStatus").asText());
    }

    @Test
    void completingLate_recordsABreach_thatStaysBreached() throws Exception {
        String manager = manager();
        String tech = signupToken("TECHNICIAN", null);
        long id = newWorkOrder(manager, newSite(manager), "HIGH");
        assign(manager, id, userId(tech));
        start(tech, id);
        setDue(id, Instant.now().minus(2, ChronoUnit.HOURS));
        complete(tech, id);

        WorkOrder wo = reload(id);
        assertEquals(SlaStatus.BREACHED, wo.getSlaStatus());
        assertNotNull(wo.getSlaBreachedAt());
        assertEquals("BREACHED", workOrder(manager, id).get("slaStatus").asText());

        // Closing it later does not change the outcome.
        postAs(manager, "/api/work-orders/" + id + "/status", json("status", "CLOSED")).andExpect(status().isOk());
        assertEquals("BREACHED", workOrder(manager, id).get("slaStatus").asText());
    }

    @Test
    void completedWork_isNotAffectedByTimePassing() throws Exception {
        String manager = manager();
        String tech = signupToken("TECHNICIAN", null);
        long id = newWorkOrder(manager, newSite(manager), "CRITICAL");
        assign(manager, id, userId(tech));
        start(tech, id);
        complete(tech, id);
        // Due time is just after the completion time but already in the past by now: finishing before it
        // means the SLA was met, and time passing afterwards must not turn it into a breach.
        setDue(id, reload(id).getCompletedAt().plusMillis(1));
        assertEquals("ON_TRACK", workOrder(manager, id).get("slaStatus").asText());
    }

    @Test
    void cancelledWork_hasNoSlaStatus() throws Exception {
        String manager = manager();
        long id = newWorkOrder(manager, newSite(manager), "LOW");
        postAs(manager, "/api/work-orders/" + id + "/status", json("status", "CANCELLED")).andExpect(status().isOk());
        assertNull(reload(id).getSlaStatus());
        getAs(manager, "/api/work-orders/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.slaStatus").doesNotExist());
    }

    // ------------------------------------------------------------------ priority change

    @Test
    void priorityChange_retimesOpenWorkFromCreation() throws Exception {
        String manager = manager();
        long siteId = newSite(manager);
        long id = newWorkOrder(manager, siteId, "LOW");

        putAs(manager, "/api/work-orders/" + id, json("siteId", siteId, "title", "Escalated", "priority", "CRITICAL"))
                .andExpect(status().isOk());

        WorkOrder wo = reload(id);
        assertEquals(wo.getCreatedAt().plus(Duration.ofHours(4)), wo.getSlaDueAt());
        assertEquals("ON_TRACK", workOrder(manager, id).get("slaStatus").asText());
    }

    @Test
    void priorityChange_cannotHideABreach() throws Exception {
        String manager = manager();
        long siteId = newSite(manager);
        long id = newWorkOrder(manager, siteId, "CRITICAL");
        Instant pastDue = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        setDue(id, pastDue);

        putAs(manager, "/api/work-orders/" + id, json("siteId", siteId, "title", "Downgraded", "priority", "LOW"))
                .andExpect(status().isOk());

        assertEquals(pastDue, reload(id).getSlaDueAt());
        assertEquals("BREACHED", workOrder(manager, id).get("slaStatus").asText());
    }

    @Test
    void priorityChange_raisingOldWork_breachesImmediately() throws Exception {
        String manager = manager();
        long siteId = newSite(manager);
        long id = newWorkOrder(manager, siteId, "LOW");
        Instant now = Instant.now();
        setWindow(id, now.minus(10, ChronoUnit.HOURS), now.plus(62, ChronoUnit.HOURS));   // LOW: created + 72h

        putAs(manager, "/api/work-orders/" + id, json("siteId", siteId, "title", "Now critical", "priority", "CRITICAL"))
                .andExpect(status().isOk());

        assertEquals("BREACHED", workOrder(manager, id).get("slaStatus").asText());      // due = created + 4h = 6h ago
        assertEquals(SlaStatus.BREACHED, reload(id).getSlaStatus());
    }

    @Test
    void priorityChange_afterCompletion_doesNotRetime() throws Exception {
        String manager = manager();
        String tech = signupToken("TECHNICIAN", null);
        long siteId = newSite(manager);
        long id = newWorkOrder(manager, siteId, "CRITICAL");
        assign(manager, id, userId(tech));
        start(tech, id);
        complete(tech, id);
        Instant dueBefore = reload(id).getSlaDueAt();

        putAs(manager, "/api/work-orders/" + id, json("siteId", siteId, "title", "Late edit", "priority", "LOW"))
                .andExpect(status().isOk());
        assertEquals(dueBefore, reload(id).getSlaDueAt());
    }

    // ------------------------------------------------------------------ breach detection (monitor sweep)

    @Test
    void monitorSweep_detectsAtRiskAndBreached_andIsIdempotent() throws Exception {
        String manager = manager();
        long siteId = newSite(manager);
        long fine = newWorkOrder(manager, siteId, "HIGH");
        long risky = newWorkOrder(manager, siteId, "HIGH");
        long late = newWorkOrder(manager, siteId, "HIGH");
        long cancelled = newWorkOrder(manager, siteId, "HIGH");
        Instant now = Instant.now();
        setWindow(risky, now.minus(20, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));
        setDue(late, now.minus(1, ChronoUnit.HOURS));
        postAs(manager, "/api/work-orders/" + cancelled + "/status", json("status", "CANCELLED")).andExpect(status().isOk());
        setDue(cancelled, now.minus(1, ChronoUnit.HOURS));

        SlaCheckResult first = slaMonitorService.checkOpenWorkOrders();
        assertTrue(first.newlyBreached() >= 1, "late work order should be reported as newly breached");
        assertTrue(first.newlyAtRisk() >= 1, "risky work order should be reported as newly at risk");

        assertEquals(SlaStatus.ON_TRACK, reload(fine).getSlaStatus());
        assertNull(reload(fine).getSlaAtRiskAt());

        WorkOrder riskyAfter = reload(risky);
        assertEquals(SlaStatus.AT_RISK, riskyAfter.getSlaStatus());
        assertNotNull(riskyAfter.getSlaAtRiskAt());
        assertNull(riskyAfter.getSlaBreachedAt());

        WorkOrder lateAfter = reload(late);
        assertEquals(SlaStatus.BREACHED, lateAfter.getSlaStatus());
        assertNotNull(lateAfter.getSlaBreachedAt());

        assertNull(reload(cancelled).getSlaStatus(), "cancelled work is never checked");

        // Second run: nothing time-based changed for these work orders, so their records are untouched.
        slaMonitorService.checkOpenWorkOrders();
        assertEquals(lateAfter.getSlaBreachedAt(), reload(late).getSlaBreachedAt());
        assertEquals(riskyAfter.getSlaAtRiskAt(), reload(risky).getSlaAtRiskAt());
    }

    @Test
    void monitorSweep_escalatesAtRiskToBreached_keepingFirstSeenTimes() throws Exception {
        String manager = manager();
        long id = newWorkOrder(manager, newSite(manager), "HIGH");
        Instant now = Instant.now();
        setWindow(id, now.minus(20, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));

        slaMonitorService.checkOpenWorkOrders();
        WorkOrder atRisk = reload(id);
        assertEquals(SlaStatus.AT_RISK, atRisk.getSlaStatus());

        setDue(id, Instant.now().minus(5, ChronoUnit.MINUTES));
        slaMonitorService.checkOpenWorkOrders();

        WorkOrder breached = reload(id);
        assertEquals(SlaStatus.BREACHED, breached.getSlaStatus());
        assertNotNull(breached.getSlaBreachedAt());
        assertEquals(atRisk.getSlaAtRiskAt(), breached.getSlaAtRiskAt());
    }

    @Test
    void monitorSweep_doesNotTouchFinishedWork() throws Exception {
        String manager = manager();
        String tech = signupToken("TECHNICIAN", null);
        long id = newWorkOrder(manager, newSite(manager), "HIGH");
        assign(manager, id, userId(tech));
        start(tech, id);
        complete(tech, id);
        Instant recorded = reload(id).getUpdatedAt();
        setDue(id, Instant.now().minus(1, ChronoUnit.HOURS));      // would be "late" if it were still open

        slaMonitorService.checkOpenWorkOrders();

        assertEquals(SlaStatus.ON_TRACK, reload(id).getSlaStatus());
        assertNull(reload(id).getSlaBreachedAt());
        assertNotNull(recorded);
    }

    // ------------------------------------------------------------------ scheduler wiring

    @Test
    void scheduler_isDisabledInTheTestProfile() {
        assertNull(slaScheduler.getIfAvailable(), "app.sla.scheduler.enabled=false must not create the scheduler bean");
    }
}
