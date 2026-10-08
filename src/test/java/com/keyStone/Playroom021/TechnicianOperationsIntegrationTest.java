package com.keyStone.Playroom021;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * KEYSTONE Step 6 - technician job actions, Parts CRUD, part usage (transactional stock),
 * time logs and authorization. Same style as WorkOrderIntegrationTest: real security filter
 * chain, H2 test DB, every test builds its own users/data with a random tag.
 *
 * NOTE: not executed in the authoring environment (no Maven) - see HANDOFF.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TechnicianOperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ helpers

    private String tag() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object... kv) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return objectMapper.writeValueAsString(map);
    }

    private String signupToken(String role, String companyName) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@keystone.test";
        String body = companyName == null
                ? json("fullName", role + " User", "email", email, "password", "password123", "role", role)
                : json("fullName", role + " User", "email", email, "password", "password123", "role", role,
                "companyName", companyName);
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long userId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions getAs(String token, String url, String... nv) throws Exception {
        var req = get(url).header("Authorization", bearer(token));
        for (int i = 0; i < nv.length; i += 2) {
            req = req.param(nv[i], nv[i + 1]);
        }
        return mockMvc.perform(req);
    }

    private ResultActions postAs(String token, String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putAs(String token, String url, String body) throws Exception {
        return mockMvc.perform(put(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions deleteAs(String token, String url) throws Exception {
        return mockMvc.perform(delete(url).header("Authorization", bearer(token)));
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** Manager, dispatcher, an assigned technician, and a customer-site work order in ASSIGNED status. */
    private record Ctx(String manager, String dispatcher, String tech, long techId, long woId, long siteId) {
    }

    private Ctx assignedJob() throws Exception {
        String manager = signupToken("MANAGER", null);
        String dispatcher = signupToken("DISPATCHER", null);
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long customerId = idOf(postAs(manager, "/api/customers", json("companyName", "Acme " + tag(), "contactEmail", null))
                .andExpect(status().isCreated()));
        long siteId = idOf(postAs(manager, "/api/customers/" + customerId + "/sites",
                json("name", "HQ " + tag(), "addressLine", "1 Main", "city", "Metropolis")).andExpect(status().isCreated()));
        long woId = idOf(postAs(manager, "/api/work-orders",
                json("siteId", siteId, "title", "Job " + tag(), "priority", "HIGH")).andExpect(status().isCreated()));
        postAs(manager, "/api/work-orders/" + woId + "/status", json("status", "ASSIGNED", "technicianId", techId))
                .andExpect(status().isOk());
        return new Ctx(manager, dispatcher, tech, techId, woId, siteId);
    }

    private Ctx activeJob() throws Exception {
        Ctx c = assignedJob();
        postAs(c.tech(), "/api/work-orders/" + c.woId() + "/start", "{}").andExpect(status().isOk());
        return c;
    }

    private String wo(Ctx c, String suffix) {
        return "/api/work-orders/" + c.woId() + suffix;
    }

    private long createPart(String token, int qty, double cost) throws Exception {
        return idOf(postAs(token, "/api/parts", json("sku", "sku-" + tag(), "name", "Filter " + tag(), "unit", "pcs",
                "quantityOnHand", qty, "reorderLevel", 2, "unitCost", cost)).andExpect(status().isCreated()));
    }

    private int stock(String token, long partId) throws Exception {
        String r = getAs(token, "/api/parts/" + partId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("quantityOnHand").asInt();
    }

    private String usageBody(Object... partIdQtyPairs) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < partIdQtyPairs.length; i += 2) {
            items.add(Map.of("partId", partIdQtyPairs[i], "quantity", partIdQtyPairs[i + 1]));
        }
        return json("items", items);
    }

    private String timeBody(Instant start, Instant end) throws Exception {
        return json("startedAt", start.toString(), "endedAt", end.toString(), "note", "work");
    }

    // ------------------------------------------------------------------ technician job actions

    @Test
    void jobActions_fullFlow_startHoldResumeComplete_writesHistory() throws Exception {
        Ctx c = assignedJob();
        postAs(c.tech(), wo(c, "/start"), json("note", "on site")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAs(c.tech(), wo(c, "/hold"), json("note", "waiting for part")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"));
        postAs(c.tech(), wo(c, "/resume"), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAs(c.tech(), wo(c, "/start"), "{}").andExpect(status().isConflict());
        postAs(c.tech(), wo(c, "/complete"), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                // NEW, ASSIGNED, IN_PROGRESS, ON_HOLD, IN_PROGRESS, COMPLETED
                .andExpect(jsonPath("$.history.length()").value(6))
                .andExpect(jsonPath("$.history[5].toStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.history[5].changedByRole").value("TECHNICIAN"));
    }

    @Test
    void jobActions_noBodyAtAll_isAccepted() throws Exception {
        Ctx c = assignedJob();
        mockMvc.perform(post(wo(c, "/start")).header("Authorization", bearer(c.tech())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void jobActions_wrongState_returns409() throws Exception {
        Ctx c = assignedJob();
        postAs(c.tech(), wo(c, "/hold"), "{}").andExpect(status().isConflict());      // ASSIGNED -> hold
        postAs(c.tech(), wo(c, "/resume"), "{}").andExpect(status().isConflict());    // ASSIGNED -> resume
        postAs(c.tech(), wo(c, "/complete"), "{}").andExpect(status().isConflict());  // ASSIGNED -> complete
        postAs(c.tech(), wo(c, "/start"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/hold"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/complete"), "{}").andExpect(status().isConflict());  // ON_HOLD -> complete
    }

    @Test
    void jobActions_onWorkOrderAssignedToSomeoneElse_returns404() throws Exception {
        Ctx c = assignedJob();
        String other = signupToken("TECHNICIAN", null);
        for (String action : List.of("/start", "/hold", "/resume", "/complete")) {
            postAs(other, wo(c, action), "{}").andExpect(status().isNotFound());
        }
        getAs(c.manager(), wo(c, "")).andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void jobActions_nonTechnicianRoles_returns403_andUnauthenticated401() throws Exception {
        Ctx c = assignedJob();
        postAs(c.manager(), wo(c, "/start"), "{}").andExpect(status().isForbidden());
        postAs(c.dispatcher(), wo(c, "/start"), "{}").andExpect(status().isForbidden());
        postAs(signupToken("CUSTOMER", "Cust " + tag()), wo(c, "/start"), "{}").andExpect(status().isForbidden());
        mockMvc.perform(post(wo(c, "/start")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ parts CRUD

    @Test
    void parts_create_normalizesSku_andReturns201() throws Exception {
        String manager = signupToken("MANAGER", null);
        String sku = "ab-" + tag();
        postAs(manager, "/api/parts", json("sku", " " + sku + " ", "name", "Air filter", "unit", "pcs",
                "quantityOnHand", 10, "reorderLevel", 3, "unitCost", 12.5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
                .andExpect(jsonPath("$.quantityOnHand").value(10))
                .andExpect(jsonPath("$.lowStock").value(false));
    }

    @Test
    void parts_duplicateSku_returns409() throws Exception {
        String manager = signupToken("MANAGER", null);
        String sku = "dup-" + tag();
        String body = json("sku", sku, "name", "A", "unit", "pcs", "quantityOnHand", 1, "reorderLevel", 0, "unitCost", 1);
        postAs(manager, "/api/parts", body).andExpect(status().isCreated());
        postAs(manager, "/api/parts", body.replace(sku, sku.toUpperCase())).andExpect(status().isConflict());
    }

    @Test
    void parts_validation_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);
        postAs(manager, "/api/parts", json("sku", "", "name", "A", "unit", "pcs", "quantityOnHand", 1,
                "reorderLevel", 0, "unitCost", 1)).andExpect(status().isBadRequest());
        postAs(manager, "/api/parts", json("sku", "s" + tag(), "name", "A", "unit", "pcs", "quantityOnHand", -1,
                "reorderLevel", 0, "unitCost", 1)).andExpect(status().isBadRequest());
        postAs(manager, "/api/parts", json("sku", "s" + tag(), "name", "A", "unit", "pcs", "quantityOnHand", 1,
                "reorderLevel", 0, "unitCost", -0.5)).andExpect(status().isBadRequest());
    }

    @Test
    void parts_getListSearchAndLowStock() throws Exception {
        String manager = signupToken("MANAGER", null);
        String marker = "zq" + tag();
        long a = idOf(postAs(manager, "/api/parts", json("sku", marker + "-A", "name", "Valve " + marker, "unit", "pcs",
                "quantityOnHand", 1, "reorderLevel", 5, "unitCost", 3)).andExpect(status().isCreated()));
        postAs(manager, "/api/parts", json("sku", marker + "-B", "name", "Belt " + marker, "unit", "pcs",
                "quantityOnHand", 50, "reorderLevel", 5, "unitCost", 3)).andExpect(status().isCreated());

        getAs(manager, "/api/parts/" + a).andExpect(status().isOk()).andExpect(jsonPath("$.lowStock").value(true));
        getAs(manager, "/api/parts/999999999").andExpect(status().isNotFound());
        getAs(manager, "/api/parts", "search", marker).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs(manager, "/api/parts", "search", marker, "lowStock", "true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a));
        getAs(manager, "/api/parts", "size", "1", "search", marker).andExpect(jsonPath("$.totalPages").value(2));
        getAs(manager, "/api/parts", "sortBy", "bogus").andExpect(status().isBadRequest());
    }

    @Test
    void parts_update_changesFields_andDuplicateSkuIs409() throws Exception {
        String manager = signupToken("MANAGER", null);
        long a = createPart(manager, 5, 2.0);
        String otherSku = "other-" + tag();
        postAs(manager, "/api/parts", json("sku", otherSku, "name", "Other", "unit", "pcs", "quantityOnHand", 1,
                "reorderLevel", 0, "unitCost", 1)).andExpect(status().isCreated());

        putAs(manager, "/api/parts/" + a, json("sku", "new-" + tag(), "name", "Renamed", "unit", "box",
                "quantityOnHand", 20, "reorderLevel", 4, "unitCost", 9.99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.quantityOnHand").value(20))
                .andExpect(jsonPath("$.unit").value("box"));
        putAs(manager, "/api/parts/" + a, json("sku", otherSku, "name", "X", "unit", "pcs", "quantityOnHand", 1,
                "reorderLevel", 0, "unitCost", 1)).andExpect(status().isConflict());
        putAs(manager, "/api/parts/999999999", json("sku", "z" + tag(), "name", "X", "unit", "pcs",
                "quantityOnHand", 1, "reorderLevel", 0, "unitCost", 1)).andExpect(status().isNotFound());
        putAs(manager, "/api/parts/" + a, json("sku", "k" + tag(), "name", "X", "unit", "pcs",
                "quantityOnHand", -3, "reorderLevel", 0, "unitCost", 1)).andExpect(status().isBadRequest());
    }

    @Test
    void parts_delete_managerOnly_andBlockedOnceUsed() throws Exception {
        String manager = signupToken("MANAGER", null);
        String dispatcher = signupToken("DISPATCHER", null);
        long free = createPart(manager, 5, 1);
        deleteAs(dispatcher, "/api/parts/" + free).andExpect(status().isForbidden());
        deleteAs(manager, "/api/parts/" + free).andExpect(status().isNoContent());
        getAs(manager, "/api/parts/" + free).andExpect(status().isNotFound());

        Ctx c = activeJob();
        long used = createPart(c.manager(), 5, 1);
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(used, 1)).andExpect(status().isCreated());
        deleteAs(c.manager(), "/api/parts/" + used).andExpect(status().isConflict());
    }

    @Test
    void parts_authorization_matrix() throws Exception {
        String manager = signupToken("MANAGER", null);
        String dispatcher = signupToken("DISPATCHER", null);
        String tech = signupToken("TECHNICIAN", null);
        String customer = signupToken("CUSTOMER", "Cust " + tag());
        long id = createPart(manager, 5, 1);
        String body = json("sku", "n" + tag(), "name", "N", "unit", "pcs", "quantityOnHand", 1, "reorderLevel", 0, "unitCost", 1);

        postAs(dispatcher, "/api/parts", body).andExpect(status().isCreated());
        postAs(tech, "/api/parts", body).andExpect(status().isForbidden());
        postAs(customer, "/api/parts", body).andExpect(status().isForbidden());
        getAs(tech, "/api/parts/" + id).andExpect(status().isOk());
        getAs(customer, "/api/parts/" + id).andExpect(status().isForbidden());
        getAs(customer, "/api/parts").andExpect(status().isForbidden());
        putAs(tech, "/api/parts/" + id, body).andExpect(status().isForbidden());
        deleteAs(tech, "/api/parts/" + id).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parts")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ part usage + stock

    @Test
    void partUsage_decreasesStock_snapshotsCost_andIsListed() throws Exception {
        Ctx c = activeJob();
        long part = createPart(c.manager(), 10, 12.5);

        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].quantity").value(3))
                .andExpect(jsonPath("$[0].remainingStock").value(7))
                .andExpect(jsonPath("$[0].unitCostAtUse").value(12.5))
                .andExpect(jsonPath("$[0].lineCost").value(37.5))
                .andExpect(jsonPath("$[0].usedByRole").value("TECHNICIAN"));
        assertEquals(7, stock(c.manager(), part));

        // Changing the catalogue price later does not alter the recorded usage.
        String sku = "re-" + tag();
        putAs(c.manager(), "/api/parts/" + part, json("sku", sku, "name", "Filter", "unit", "pcs",
                "quantityOnHand", 7, "reorderLevel", 2, "unitCost", 99)).andExpect(status().isOk());
        getAs(c.manager(), wo(c, "/part-usage")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unitCostAtUse").value(12.5));
    }

    @Test
    void partUsage_insufficientStock_returns409_andStockUnchanged() throws Exception {
        Ctx c = activeJob();
        long part = createPart(c.manager(), 2, 1);
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 3)).andExpect(status().isConflict());
        assertEquals(2, stock(c.manager(), part));
        getAs(c.manager(), wo(c, "/part-usage")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void partUsage_stockNeverNegative_exactlyZeroThenRejected() throws Exception {
        Ctx c = activeJob();
        long part = createPart(c.manager(), 3, 1);
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 3)).andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].remainingStock").value(0));
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isConflict());
        assertEquals(0, stock(c.manager(), part));
    }

    @Test
    void partUsage_batchFailure_rollsBackEarlierDecrementsAndUsageRows() throws Exception {
        Ctx c = activeJob();
        long plenty = createPart(c.manager(), 10, 1);
        long scarce = createPart(c.manager(), 1, 1);

        // Item 1 would succeed on its own; item 2 fails -> the whole request must roll back.
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(plenty, 5, scarce, 2)).andExpect(status().isConflict());

        assertEquals(10, stock(c.manager(), plenty));
        assertEquals(1, stock(c.manager(), scarce));
        getAs(c.manager(), wo(c, "/part-usage")).andExpect(jsonPath("$.length()").value(0));

        // Unknown part in a batch also rolls back the earlier item.
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(plenty, 4, 999_999_999L, 1)).andExpect(status().isNotFound());
        assertEquals(10, stock(c.manager(), plenty));

        // A fully valid batch commits everything.
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(plenty, 5, scarce, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
        assertEquals(5, stock(c.manager(), plenty));
        assertEquals(0, stock(c.manager(), scarce));
    }

    @Test
    void partUsage_concurrentRequests_neverOversell() throws Exception {
        Ctx c = activeJob();
        long part = createPart(c.manager(), 5, 1);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        String body = usageBody(part, 1);
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return postAs(c.tech(), wo(c, "/part-usage"), body).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> f : results) {
            int s = f.get(30, TimeUnit.SECONDS);
            if (s == 201) created++;
            else if (s == 409) conflicts++;
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        assertEquals(5, created);
        assertEquals(3, conflicts);
        assertEquals(0, stock(c.manager(), part));
    }

    @Test
    void partUsage_invalidRequests_return400or404() throws Exception {
        Ctx c = activeJob();
        long part = createPart(c.manager(), 5, 1);
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 0)).andExpect(status().isBadRequest());
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, -2)).andExpect(status().isBadRequest());
        postAs(c.tech(), wo(c, "/part-usage"), json("items", List.of())).andExpect(status().isBadRequest());
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(999_999_999L, 1)).andExpect(status().isNotFound());
        assertEquals(5, stock(c.manager(), part));
    }

    @Test
    void partUsage_requiresActiveWorkOrder() throws Exception {
        Ctx c = assignedJob();  // ASSIGNED, not started
        long part = createPart(c.manager(), 5, 1);
        postAs(c.manager(), wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isConflict());
        postAs(c.tech(), wo(c, "/start"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isCreated());
        postAs(c.tech(), wo(c, "/hold"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isCreated());   // ON_HOLD ok
        postAs(c.tech(), wo(c, "/resume"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/complete"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isConflict());  // COMPLETED
        assertEquals(3, stock(c.manager(), part));
    }

    @Test
    void partUsage_technicianOnlyOnAssignedWorkOrders() throws Exception {
        Ctx c = activeJob();
        String other = signupToken("TECHNICIAN", null);
        long part = createPart(c.manager(), 5, 1);
        postAs(other, wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isNotFound());
        getAs(other, wo(c, "/part-usage")).andExpect(status().isNotFound());
        assertEquals(5, stock(c.manager(), part));
    }

    @Test
    void partUsage_managerAndDispatcherCanRecord_customerCannotAccess() throws Exception {
        Ctx c = activeJob();
        long part = createPart(c.manager(), 10, 1);
        postAs(c.manager(), wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isCreated());
        postAs(c.dispatcher(), wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isCreated());
        assertEquals(8, stock(c.manager(), part));

        String customer = signupToken("CUSTOMER", "Cust " + tag());
        getAs(customer, wo(c, "/part-usage")).andExpect(status().isForbidden());
        postAs(customer, wo(c, "/part-usage"), usageBody(part, 1)).andExpect(status().isForbidden());
        mockMvc.perform(get(wo(c, "/part-usage"))).andExpect(status().isUnauthorized());
    }

    @Test
    void partUsage_remove_returnsStock() throws Exception {
        Ctx c = activeJob();
        long part = createPart(c.manager(), 10, 1);
        String r = postAs(c.tech(), wo(c, "/part-usage"), usageBody(part, 4)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long usageId = objectMapper.readTree(r).get(0).get("id").asLong();
        assertEquals(6, stock(c.manager(), part));

        deleteAs(c.tech(), wo(c, "/part-usage/" + usageId)).andExpect(status().isNoContent());
        assertEquals(10, stock(c.manager(), part));
        deleteAs(c.tech(), wo(c, "/part-usage/" + usageId)).andExpect(status().isNotFound());
        assertEquals(10, stock(c.manager(), part));
    }

    // ------------------------------------------------------------------ time logs

    @Test
    void timeLogs_create_computesMinutes_andListTotals() throws Exception {
        Ctx c = activeJob();
        Instant end = Instant.now().minus(10, ChronoUnit.MINUTES);
        postAs(c.tech(), wo(c, "/time-logs"), timeBody(end.minus(90, ChronoUnit.MINUTES), end))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minutes").value(90))
                .andExpect(jsonPath("$.technicianId").value(c.techId()));
        postAs(c.tech(), wo(c, "/time-logs"), timeBody(end.minus(30, ChronoUnit.MINUTES), end.minus(10, ChronoUnit.MINUTES)))
                .andExpect(status().isCreated());

        getAs(c.manager(), wo(c, "/time-logs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.totalMinutes").value(110));
    }

    @Test
    void timeLogs_invalidRanges_return400() throws Exception {
        Ctx c = activeJob();
        Instant now = Instant.now();
        postAs(c.tech(), wo(c, "/time-logs"), timeBody(now.minus(1, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());                                   // end before start
        postAs(c.tech(), wo(c, "/time-logs"), timeBody(now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());                                   // future
        postAs(c.tech(), wo(c, "/time-logs"), timeBody(now.minus(30, ChronoUnit.SECONDS), now.minus(5, ChronoUnit.SECONDS)))
                .andExpect(status().isBadRequest());                                   // < 1 minute
        postAs(c.tech(), wo(c, "/time-logs"), timeBody(now.minus(30, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());                                   // > 24h
        postAs(c.tech(), wo(c, "/time-logs"), json("endedAt", now.toString())).andExpect(status().isBadRequest());
        getAs(c.manager(), wo(c, "/time-logs")).andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void timeLogs_statusRules() throws Exception {
        Ctx c = assignedJob();
        Instant end = Instant.now().minus(5, ChronoUnit.MINUTES);
        String body = timeBody(end.minus(20, ChronoUnit.MINUTES), end);
        postAs(c.tech(), wo(c, "/time-logs"), body).andExpect(status().isConflict());   // ASSIGNED
        postAs(c.tech(), wo(c, "/start"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/time-logs"), body).andExpect(status().isCreated());    // IN_PROGRESS
        postAs(c.tech(), wo(c, "/complete"), "{}").andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/time-logs"), body).andExpect(status().isCreated());    // COMPLETED late entry
        postAs(c.manager(), wo(c, "/status"), json("status", "CLOSED")).andExpect(status().isOk());
        postAs(c.tech(), wo(c, "/time-logs"), body).andExpect(status().isConflict());   // CLOSED
    }

    @Test
    void timeLogs_authorization() throws Exception {
        Ctx c = activeJob();
        String other = signupToken("TECHNICIAN", null);
        String customer = signupToken("CUSTOMER", "Cust " + tag());
        Instant end = Instant.now().minus(5, ChronoUnit.MINUTES);
        String body = timeBody(end.minus(20, ChronoUnit.MINUTES), end);

        postAs(other, wo(c, "/time-logs"), body).andExpect(status().isNotFound());       // not assigned to them
        getAs(other, wo(c, "/time-logs")).andExpect(status().isNotFound());
        postAs(c.manager(), wo(c, "/time-logs"), body).andExpect(status().isForbidden()); // only technicians log time
        postAs(c.dispatcher(), wo(c, "/time-logs"), body).andExpect(status().isForbidden());
        postAs(customer, wo(c, "/time-logs"), body).andExpect(status().isForbidden());
        getAs(customer, wo(c, "/time-logs")).andExpect(status().isForbidden());
        mockMvc.perform(get(wo(c, "/time-logs"))).andExpect(status().isUnauthorized());
        getAs(c.tech(), wo(c, "/time-logs")).andExpect(status().isOk());
    }

    @Test
    void timeLogs_delete_ownerOrStaffOnly() throws Exception {
        Ctx c = activeJob();
        String other = signupToken("TECHNICIAN", null);
        Instant end = Instant.now().minus(5, ChronoUnit.MINUTES);
        long log1 = idOf(postAs(c.tech(), wo(c, "/time-logs"), timeBody(end.minus(20, ChronoUnit.MINUTES), end))
                .andExpect(status().isCreated()));
        long log2 = idOf(postAs(c.tech(), wo(c, "/time-logs"), timeBody(end.minus(60, ChronoUnit.MINUTES), end.minus(30, ChronoUnit.MINUTES)))
                .andExpect(status().isCreated()));

        deleteAs(other, wo(c, "/time-logs/" + log1)).andExpect(status().isNotFound());
        deleteAs(c.tech(), wo(c, "/time-logs/" + log1)).andExpect(status().isNoContent());
        deleteAs(c.tech(), wo(c, "/time-logs/" + log1)).andExpect(status().isNotFound());
        deleteAs(c.manager(), wo(c, "/time-logs/" + log2)).andExpect(status().isNoContent());
        getAs(c.manager(), wo(c, "/time-logs")).andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.totalMinutes").value(0));
    }
}
