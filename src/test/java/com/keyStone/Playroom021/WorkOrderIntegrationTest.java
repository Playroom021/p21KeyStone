package com.keyStone.Playroom021;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KEYSTONE Step 5 - Work Order management + lifecycle integration tests.
 *
 * Runs through the real Spring Security filter chain against the H2 in-memory test
 * database (see src/test/resources/application.properties). Every test signs up its own
 * users and tags names with a random suffix, so tests are order-independent.
 *
 * Covers: Work Order CRUD, valid/invalid status transitions, StatusHistory creation,
 * technician authorization (assigned-only), and customer isolation (own-only).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkOrderIntegrationTest {

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

    private String json(Object... keyValues) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    /** Also returns the new user's id, read back via a fresh JWT (needed to assign a technician by id). */
    private long userId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createCustomer(String managerToken, String companyName) throws Exception {
        String response = mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("companyName", companyName, "contactEmail", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createSite(String managerToken, long customerId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/customers/" + customerId + "/sites")
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", name, "addressLine", "1 Main Road", "city", "Metropolis")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long ownCustomerId(String customerToken) throws Exception {
        String response = mockMvc.perform(get("/api/customers/me").header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions getAs(String token, String url, String... nameValuePairs) throws Exception {
        var request = get(url).header("Authorization", bearer(token));
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request = request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return mockMvc.perform(request);
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

    /** Fixture: a MANAGER, a customer with one site, and the tokens needed to create work orders against it. */
    private record Fixture(String managerToken, String dispatcherToken, long customerId, long siteId) {
    }

    private Fixture fixture() throws Exception {
        String manager = signupToken("MANAGER", null);
        String dispatcher = signupToken("DISPATCHER", null);
        long customerId = createCustomer(manager, "Acme " + tag());
        long siteId = createSite(manager, customerId, "HQ " + tag());
        return new Fixture(manager, dispatcher, customerId, siteId);
    }

    private long createWorkOrder(String token, long siteId, String title, String priority) throws Exception {
        String response = postAs(token, "/api/work-orders",
                json("siteId", siteId, "title", title, "description", "Something is broken", "priority", priority))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    // ------------------------------------------------------------------ CRUD

    @Test
    void create_byManager_returns201WithCustomerAndSiteLinked() throws Exception {
        Fixture f = fixture();
        postAs(f.managerToken(), "/api/work-orders",
                json("siteId", f.siteId(), "title", "Broken AC " + tag(), "description", "It's hot",
                        "priority", "HIGH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("WO-")))
                .andExpect(jsonPath("$.customerId").value(f.customerId()))
                .andExpect(jsonPath("$.siteId").value(f.siteId()))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.assignedTechnicianId").doesNotExist());
    }

    @Test
    void create_byDispatcher_returns201() throws Exception {
        Fixture f = fixture();
        postAs(f.dispatcherToken(), "/api/work-orders",
                json("siteId", f.siteId(), "title", "Leaky pipe " + tag(), "description", "Drip drip",
                        "priority", "MEDIUM"))
                .andExpect(status().isCreated());
    }

    @Test
    void create_withUnknownSite_returns404() throws Exception {
        Fixture f = fixture();
        postAs(f.managerToken(), "/api/work-orders",
                json("siteId", 999_999_999L, "title", "Ghost site", "priority", "LOW"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_withBlankTitle_returns400() throws Exception {
        Fixture f = fixture();
        postAs(f.managerToken(), "/api/work-orders",
                json("siteId", f.siteId(), "title", "", "priority", "LOW"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_byId_includesEmptyHistoryIsNotEmpty_createdEntry() throws Exception {
        Fixture f = fixture();
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Broken lock " + tag(), "LOW");

        getAs(f.managerToken(), "/api/work-orders/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.history[0].toStatus").value("NEW"));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        Fixture f = fixture();
        getAs(f.managerToken(), "/api/work-orders/999999999")
                .andExpect(status().isNotFound());
    }

    @Test
    void update_byManager_changesFieldsButNotSiteOrCustomer() throws Exception {
        Fixture f = fixture();
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Old title " + tag(), "LOW");

        putAs(f.managerToken(), "/api/work-orders/" + id,
                json("siteId", 999999L, "title", "New title", "description", "Updated", "priority", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.siteId").value(f.siteId())); // siteId in body is ignored
    }

    @Test
    void delete_newWorkOrder_byManager_returns204() throws Exception {
        Fixture f = fixture();
        long id = createWorkOrder(f.managerToken(), f.siteId(), "To delete " + tag(), "LOW");

        deleteAs(f.managerToken(), "/api/work-orders/" + id).andExpect(status().isNoContent());
        getAs(f.managerToken(), "/api/work-orders/" + id).andExpect(status().isNotFound());
    }

    @Test
    void delete_byDispatcher_returns403() throws Exception {
        Fixture f = fixture();
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Not yours " + tag(), "LOW");
        deleteAs(f.dispatcherToken(), "/api/work-orders/" + id).andExpect(status().isForbidden());
    }

    @Test
    void delete_afterAssignment_returns409() throws Exception {
        Fixture f = fixture();
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "In progress WO " + tag(), "LOW");

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", techId))
                .andExpect(status().isOk());

        deleteAs(f.managerToken(), "/api/work-orders/" + id).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ valid transitions + history

    @Test
    void fullLifecycle_managerAndTechnician_recordsHistoryForEachTransition() throws Exception {
        Fixture f = fixture();
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Full lifecycle " + tag(), "HIGH");

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", techId, "note", "Assigning to tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTechnicianId").value(techId));

        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "ON_HOLD", "note", "Waiting on part"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"));

        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "IN_PROGRESS"))
                .andExpect(status().isOk());

        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status", json("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                // created, ASSIGNED, IN_PROGRESS, ON_HOLD, IN_PROGRESS, COMPLETED, CLOSED = 7 rows
                .andExpect(jsonPath("$.history.length()").value(7))
                .andExpect(jsonPath("$.history[1].fromStatus").value("NEW"))
                .andExpect(jsonPath("$.history[1].toStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.history[1].changedByRole").value("MANAGER"))
                .andExpect(jsonPath("$.history[6].fromStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.history[6].toStatus").value("CLOSED"));
    }

    @Test
    void cancel_fromNew_byDispatcher_isValidAndTerminal() throws Exception {
        Fixture f = fixture();
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Cancel me " + tag(), "LOW");

        postAs(f.dispatcherToken(), "/api/work-orders/" + id + "/status", json("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // CANCELLED is terminal: no further transition is valid.
        postAs(f.dispatcherToken(), "/api/work-orders/" + id + "/status", json("status", "NEW"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ invalid transitions

    @Test
    void transition_newDirectlyToInProgress_isInvalid_returns409() throws Exception {
        Fixture f = fixture();
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Skip step " + tag(), "LOW");

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status", json("status", "IN_PROGRESS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void transition_closedToAnything_isInvalid_returns409() throws Exception {
        Fixture f = fixture();
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Terminal check " + tag(), "LOW");

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", techId)).andExpect(status().isOk());
        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "IN_PROGRESS")).andExpect(status().isOk());
        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "COMPLETED")).andExpect(status().isOk());
        postAs(f.managerToken(), "/api/work-orders/" + id + "/status", json("status", "CLOSED")).andExpect(status().isOk());

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status", json("status", "IN_PROGRESS"))
                .andExpect(status().isConflict());
    }

    @Test
    void transition_assignedWithoutTechnicianId_returns400() throws Exception {
        Fixture f = fixture();
        long id = createWorkOrder(f.managerToken(), f.siteId(), "No tech given " + tag(), "LOW");

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status", json("status", "ASSIGNED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transition_assignedWithNonTechnicianUser_returns400() throws Exception {
        Fixture f = fixture();
        long dispatcherId = userId(f.dispatcherToken());
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Wrong role assign " + tag(), "LOW");

        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", dispatcherId))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ technician authorization

    @Test
    void technician_cannotSelfAssign_returns403() throws Exception {
        Fixture f = fixture();
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Self assign attempt " + tag(), "LOW");

        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "ASSIGNED", "technicianId", techId))
                .andExpect(status().isForbidden());
    }

    @Test
    void technician_cannotCancelOrClose_returns403() throws Exception {
        Fixture f = fixture();
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "No cancel rights " + tag(), "LOW");
        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", techId)).andExpect(status().isOk());

        postAs(tech, "/api/work-orders/" + id + "/status", json("status", "CANCELLED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technician_onUnassignedWorkOrder_get_returns404() throws Exception {
        Fixture f = fixture();
        String techA = signupToken("TECHNICIAN", null);
        String techB = signupToken("TECHNICIAN", null);
        long techAId = userId(techA);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Assigned to A " + tag(), "LOW");
        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", techAId)).andExpect(status().isOk());

        // techB is not assigned to this work order -> hidden, same 404 as a nonexistent id.
        getAs(techB, "/api/work-orders/" + id).andExpect(status().isNotFound());
        // techA (assigned) can see it fine.
        getAs(techA, "/api/work-orders/" + id).andExpect(status().isOk());
    }

    @Test
    void technician_onUnassignedWorkOrder_statusTransition_returns404() throws Exception {
        Fixture f = fixture();
        String techA = signupToken("TECHNICIAN", null);
        String techB = signupToken("TECHNICIAN", null);
        long techAId = userId(techA);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "Assigned to A only " + tag(), "LOW");
        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", techAId)).andExpect(status().isOk());

        postAs(techB, "/api/work-orders/" + id + "/status", json("status", "IN_PROGRESS"))
                .andExpect(status().isNotFound());
    }

    @Test
    void technician_list_onlyShowsAssignedWorkOrders() throws Exception {
        Fixture f = fixture();
        String techA = signupToken("TECHNICIAN", null);
        String techB = signupToken("TECHNICIAN", null);
        long techAId = userId(techA);
        String uniqueTitle = "TechScoped " + tag();
        long assignedId = createWorkOrder(f.managerToken(), f.siteId(), uniqueTitle, "LOW");
        createWorkOrder(f.managerToken(), f.siteId(), "Unassigned " + tag(), "LOW");
        postAs(f.managerToken(), "/api/work-orders/" + assignedId + "/status",
                json("status", "ASSIGNED", "technicianId", techAId)).andExpect(status().isOk());

        getAs(techA, "/api/work-orders", "search", uniqueTitle)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(assignedId));

        // techB has nothing assigned at all under this site's customer.
        getAs(techB, "/api/work-orders", "search", uniqueTitle)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void technician_update_returns403() throws Exception {
        Fixture f = fixture();
        String tech = signupToken("TECHNICIAN", null);
        long techId = userId(tech);
        long id = createWorkOrder(f.managerToken(), f.siteId(), "No edit rights " + tag(), "LOW");
        postAs(f.managerToken(), "/api/work-orders/" + id + "/status",
                json("status", "ASSIGNED", "technicianId", techId)).andExpect(status().isOk());

        putAs(tech, "/api/work-orders/" + id, json("siteId", f.siteId(), "title", "Hijacked", "priority", "LOW"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ customer isolation

    @Test
    void customer_list_onlyShowsOwnWorkOrders() throws Exception {
        Fixture f = fixture();
        String customerA = signupToken("CUSTOMER", "Customer A " + tag());
        String customerB = signupToken("CUSTOMER", "Customer B " + tag());
        long customerAId = ownCustomerId(customerA);
        long siteA = createSite(f.managerToken(), customerAId, "Site A " + tag());
        String uniqueTitle = "IsolationCheck " + tag();
        createWorkOrder(f.managerToken(), siteA, uniqueTitle, "LOW");

        getAs(customerA, "/api/work-orders", "search", uniqueTitle)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        getAs(customerB, "/api/work-orders", "search", uniqueTitle)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void customer_get_anotherCustomersWorkOrder_returns404() throws Exception {
        Fixture f = fixture();
        String customerA = signupToken("CUSTOMER", "Owner " + tag());
        String customerB = signupToken("CUSTOMER", "Stranger " + tag());
        long customerAId = ownCustomerId(customerA);
        long siteA = createSite(f.managerToken(), customerAId, "Owner Site " + tag());
        long id = createWorkOrder(f.managerToken(), siteA, "Private WO " + tag(), "LOW");

        getAs(customerA, "/api/work-orders/" + id).andExpect(status().isOk());
        getAs(customerB, "/api/work-orders/" + id).andExpect(status().isNotFound());
    }

    @Test
    void customer_cannotCreateViaStaffEndpoint_returns403() throws Exception {
        Fixture f = fixture();
        String customer = signupToken("CUSTOMER", "No staff access " + tag());

        postAs(customer, "/api/work-orders",
                json("siteId", f.siteId(), "title", "Trying staff endpoint", "priority", "LOW"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotTransitionStatus_returns403() throws Exception {
        Fixture f = fixture();
        String customerA = signupToken("CUSTOMER", "Status blocked " + tag());
        long customerAId = ownCustomerId(customerA);
        long siteA = createSite(f.managerToken(), customerAId, "Site " + tag());
        long id = createWorkOrder(f.managerToken(), siteA, "No customer transitions " + tag(), "LOW");

        postAs(customerA, "/api/work-orders/" + id + "/status", json("status", "CANCELLED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotDelete_returns403() throws Exception {
        Fixture f = fixture();
        String customerA = signupToken("CUSTOMER", "Delete blocked " + tag());
        long customerAId = ownCustomerId(customerA);
        long siteA = createSite(f.managerToken(), customerAId, "Site " + tag());
        long id = createWorkOrder(f.managerToken(), siteA, "No customer delete " + tag(), "LOW");

        deleteAs(customerA, "/api/work-orders/" + id).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ unauthenticated / cross-cutting

    @Test
    void unauthenticated_listWorkOrders_returns401() throws Exception {
        mockMvc.perform(get("/api/work-orders")).andExpect(status().isUnauthorized());
    }
}
