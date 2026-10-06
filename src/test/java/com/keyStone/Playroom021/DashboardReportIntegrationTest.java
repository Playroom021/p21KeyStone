package com.keyStone.Playroom021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 7 dashboard/report APIs against the real stack. The H2 database is shared with other tests, so every
 * assertion on totals is a before/after delta around data this test creates. NOT RUN in the authoring environment.
 */
class DashboardReportIntegrationTest extends AbstractApiTest {

    private static final String[] ENDPOINTS = {"/api/dashboard/summary", "/api/dashboard/sla", "/api/dashboard/technician-workload"};

    private JsonNode summary(String token) throws Exception {
        return bodyOf(getAs(token, "/api/dashboard/summary").andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ authorization

    @Test
    void authorization_managerAndDispatcherOnly() throws Exception {
        String manager = signupToken("MANAGER", null);
        String dispatcher = signupToken("DISPATCHER", null);
        String tech = signupToken("TECHNICIAN", null);
        String customer = signupToken("CUSTOMER", "Cust " + tag());
        for (String url : ENDPOINTS) {
            getAs(manager, url).andExpect(status().isOk());
            getAs(dispatcher, url).andExpect(status().isOk());
            getAs(tech, url).andExpect(status().isForbidden());
            getAs(customer, url).andExpect(status().isForbidden());
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void existingRoleHomeEndpoints_stillWork() throws Exception {
        getAs(signupToken("MANAGER", null), "/api/dashboard/manager").andExpect(status().isOk());
        getAs(signupToken("DISPATCHER", null), "/api/dashboard/dispatcher").andExpect(status().isOk());
        getAs(signupToken("TECHNICIAN", null), "/api/dashboard/worker").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ summary

    @Test
    void summary_countsOpenCompletedCancelledOverdueAndBreaches() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long siteId = newSite(manager);

        JsonNode before = summary(manager);

        long openOnTime = newWorkOrder(manager, siteId, "HIGH");                       // NEW, fine
        long openOverdue = newWorkOrder(manager, siteId, "HIGH");                      // NEW, overdue
        setDue(openOverdue, Instant.now().minus(2, ChronoUnit.HOURS));

        long doneOnTime = newWorkOrder(manager, siteId, "HIGH");                       // completed, SLA met
        assign(manager, doneOnTime, techId);
        start(tech, doneOnTime);
        complete(tech, doneOnTime);

        long doneLate = newWorkOrder(manager, siteId, "HIGH");                         // completed after due
        assign(manager, doneLate, techId);
        start(tech, doneLate);
        setDue(doneLate, Instant.now().minus(1, ChronoUnit.HOURS));
        complete(tech, doneLate);

        long cancelled = newWorkOrder(manager, siteId, "LOW");
        postAs(manager, "/api/work-orders/" + cancelled + "/status", json("status", "CANCELLED")).andExpect(status().isOk());

        JsonNode after = summary(manager);

        assertEquals(5, after.get("totalWorkOrders").asLong() - before.get("totalWorkOrders").asLong());
        assertEquals(2, after.get("openWorkOrders").asLong() - before.get("openWorkOrders").asLong());
        assertEquals(2, after.get("completedWorkOrders").asLong() - before.get("completedWorkOrders").asLong());
        assertEquals(1, after.get("cancelledWorkOrders").asLong() - before.get("cancelledWorkOrders").asLong());
        assertEquals(1, after.get("overdueWorkOrders").asLong() - before.get("overdueWorkOrders").asLong());

        JsonNode b = before.get("sla");
        JsonNode a = after.get("sla");
        assertEquals(1, a.get("breachedOpen").asLong() - b.get("breachedOpen").asLong());
        assertEquals(1, a.get("breachedCompleted").asLong() - b.get("breachedCompleted").asLong());
        assertEquals(2, a.get("totalBreaches").asLong() - b.get("totalBreaches").asLong());
        assertEquals(1, a.get("metOnTime").asLong() - b.get("metOnTime").asLong());

        assertEquals(after.get("overdueWorkOrders").asLong(), a.get("breachedOpen").asLong(), "overdue == open breached");
        assertEquals(2, after.get("countsByStatus").get("NEW").asLong() - before.get("countsByStatus").get("NEW").asLong());
        assertEquals(2, after.get("countsByStatus").get("COMPLETED").asLong() - before.get("countsByStatus").get("COMPLETED").asLong());
        assertEquals(1, after.get("countsByStatus").get("CANCELLED").asLong() - before.get("countsByStatus").get("CANCELLED").asLong());
        assertNotNull(openOnTime);
    }

    @Test
    void summary_complianceIsMetOverDecided() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tech = signupToken("TECHNICIAN", null);
        long siteId = newSite(manager);
        long id = newWorkOrder(manager, siteId, "HIGH");
        assign(manager, id, userId(tech));
        start(tech, id);
        complete(tech, id);                                                            // guarantees at least one decided SLA

        JsonNode sla = summary(manager).get("sla");
        long met = sla.get("metOnTime").asLong();
        long breaches = sla.get("totalBreaches").asLong();
        assertTrue(met >= 1);
        double expected = Math.round(met * 10000.0 / (met + breaches)) / 100.0;
        assertEquals(expected, sla.get("compliancePercent").asDouble(), 0.011);
        assertTrue(sla.get("compliancePercent").asDouble() <= 100.0);
    }

    // ------------------------------------------------------------------ SLA report

    @Test
    void slaReport_listsBreachedAndAtRiskOpenWork() throws Exception {
        String manager = signupToken("MANAGER", null);
        long siteId = newSite(manager);
        long breached = newWorkOrder(manager, siteId, "HIGH");
        long risky = newWorkOrder(manager, siteId, "HIGH");
        Instant now = Instant.now();
        setDue(breached, now.minus(2, ChronoUnit.HOURS));
        setWindow(risky, now.minus(20, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));

        JsonNode report = bodyOf(getAs(manager, "/api/dashboard/sla").andExpect(status().isOk()));
        assertEquals(50, report.get("listLimit").asInt());
        assertNotNull(report.get("summary").get("totalBreaches"));

        JsonNode breachedItem = find(report.get("breachedWorkOrders"), breached);
        assertNotNull(breachedItem, "breached work order should be listed");
        assertEquals("BREACHED", breachedItem.get("slaStatus").asText());
        assertTrue(Math.abs(breachedItem.get("minutesOverdue").asLong() - 120) <= 2);

        JsonNode riskyItem = find(report.get("atRiskWorkOrders"), risky);
        assertNotNull(riskyItem, "at-risk work order should be listed");
        assertEquals("AT_RISK", riskyItem.get("slaStatus").asText());
        assertTrue(Math.abs(riskyItem.get("minutesRemaining").asLong() - 240) <= 2);
        assertNull(find(report.get("breachedWorkOrders"), risky));

        Instant previous = null;
        for (JsonNode item : report.get("breachedWorkOrders")) {
            Instant due = Instant.parse(item.get("slaDueAt").asText());
            if (previous != null) {
                assertFalse(due.isBefore(previous), "breached list is ordered by due time, earliest first");
            }
            previous = due;
        }
    }

    @Test
    void slaReport_excludesFinishedAndCancelledWork() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tech = signupToken("TECHNICIAN", null);
        long siteId = newSite(manager);
        long done = newWorkOrder(manager, siteId, "HIGH");
        assign(manager, done, userId(tech));
        start(tech, done);
        complete(tech, done);
        setDue(done, Instant.now().minus(1, ChronoUnit.HOURS));
        long cancelled = newWorkOrder(manager, siteId, "HIGH");
        postAs(manager, "/api/work-orders/" + cancelled + "/status", json("status", "CANCELLED")).andExpect(status().isOk());
        setDue(cancelled, Instant.now().minus(1, ChronoUnit.HOURS));

        JsonNode report = bodyOf(getAs(manager, "/api/dashboard/sla").andExpect(status().isOk()));
        assertNull(find(report.get("breachedWorkOrders"), done));
        assertNull(find(report.get("breachedWorkOrders"), cancelled));
    }

    // ------------------------------------------------------------------ technician workload

    @Test
    void technicianWorkload_countsPerTechnician_includingIdleOnes() throws Exception {
        String manager = signupToken("MANAGER", null);
        String busy = signupToken("TECHNICIAN", null);
        String idle = signupToken("TECHNICIAN", null);
        long busyId = userId(busy);
        long idleId = userId(idle);
        long siteId = newSite(manager);

        JsonNode before = bodyOf(getAs(manager, "/api/dashboard/technician-workload").andExpect(status().isOk()));

        long assigned = newWorkOrder(manager, siteId, "HIGH");                  // ASSIGNED
        assign(manager, assigned, busyId);

        long inProgressRisky = newWorkOrder(manager, siteId, "HIGH");           // IN_PROGRESS, at risk
        assign(manager, inProgressRisky, busyId);
        start(busy, inProgressRisky);
        Instant now = Instant.now();
        setWindow(inProgressRisky, now.minus(20, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));

        long inProgressLate = newWorkOrder(manager, siteId, "HIGH");            // IN_PROGRESS, overdue
        assign(manager, inProgressLate, busyId);
        start(busy, inProgressLate);
        setDue(inProgressLate, now.minus(1, ChronoUnit.HOURS));

        long onHold = newWorkOrder(manager, siteId, "HIGH");                    // ON_HOLD
        assign(manager, onHold, busyId);
        start(busy, onHold);
        postAs(busy, "/api/work-orders/" + onHold + "/hold", "{}").andExpect(status().isOk());

        long finished = newWorkOrder(manager, siteId, "HIGH");                  // COMPLETED
        assign(manager, finished, busyId);
        start(busy, finished);
        complete(busy, finished);

        newWorkOrder(manager, siteId, "HIGH");                                  // NEW, unassigned

        JsonNode after = bodyOf(getAs(manager, "/api/dashboard/technician-workload").andExpect(status().isOk()));

        JsonNode busyRow = find(after.get("technicians"), "technicianId", busyId);
        assertNotNull(busyRow);
        assertEquals(1, busyRow.get("assigned").asLong());
        assertEquals(2, busyRow.get("inProgress").asLong());
        assertEquals(1, busyRow.get("onHold").asLong());
        assertEquals(4, busyRow.get("openTotal").asLong());
        assertEquals(1, busyRow.get("overdue").asLong());
        assertEquals(1, busyRow.get("atRisk").asLong());
        assertEquals(1, busyRow.get("completed").asLong());

        JsonNode idleRow = find(after.get("technicians"), "technicianId", idleId);
        assertNotNull(idleRow, "technicians with no work are still listed");
        assertEquals(0, idleRow.get("openTotal").asLong());
        assertEquals(0, idleRow.get("completed").asLong());

        assertEquals(1, after.get("unassignedOpen").asLong() - before.get("unassignedOpen").asLong());

        int busyIndex = indexOf(after.get("technicians"), busyId);
        int idleIndex = indexOf(after.get("technicians"), idleId);
        assertTrue(busyIndex < idleIndex, "busier technicians come first");
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode find(JsonNode array, long id) {
        return find(array, "id", id);
    }

    private JsonNode find(JsonNode array, String field, long id) {
        for (JsonNode n : array) {
            if (n.get(field).asLong() == id) {
                return n;
            }
        }
        return null;
    }

    private int indexOf(JsonNode array, long technicianId) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).get("technicianId").asLong() == technicianId) {
                return i;
            }
        }
        return -1;
    }
}
