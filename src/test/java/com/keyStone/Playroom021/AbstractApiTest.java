package com.keyStone.Playroom021;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for the Step 7 integration tests (same style as the earlier test classes, which keep
 * their own private copies and are untouched). Real security filter chain, H2 test DB. Time-dependent
 * state (SLA windows) is set up by writing the database directly, because the clock is the real one.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractApiTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected WorkOrderRepository workOrderRepository;
    @Autowired
    protected TransactionTemplate tx;
    @PersistenceContext
    protected EntityManager entityManager;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected String tag() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String json(Object... kv) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return objectMapper.writeValueAsString(map);
    }

    protected String signupToken(String role, String companyName) throws Exception {
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

    protected long userId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    protected ResultActions getAs(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearer(token)));
    }

    protected ResultActions postAs(String token, String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions putAs(String token, String url, String body) throws Exception {
        return mockMvc.perform(put(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected JsonNode bodyOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected long idOf(ResultActions result) throws Exception {
        return bodyOf(result).get("id").asLong();
    }

    // ---- domain setup ----

    protected long newSite(String manager) throws Exception {
        long customerId = idOf(postAs(manager, "/api/customers",
                json("companyName", "Acme " + tag(), "contactEmail", null)).andExpect(status().isCreated()));
        return idOf(postAs(manager, "/api/customers/" + customerId + "/sites",
                json("name", "HQ " + tag(), "addressLine", "1 Main", "city", "Metropolis")).andExpect(status().isCreated()));
    }

    protected long newWorkOrder(String manager, long siteId, String priority) throws Exception {
        return idOf(postAs(manager, "/api/work-orders",
                json("siteId", siteId, "title", "Job " + tag(), "priority", priority)).andExpect(status().isCreated()));
    }

    protected void assign(String manager, long woId, long techId) throws Exception {
        postAs(manager, "/api/work-orders/" + woId + "/status", json("status", "ASSIGNED", "technicianId", techId))
                .andExpect(status().isOk());
    }

    protected void start(String tech, long woId) throws Exception {
        postAs(tech, "/api/work-orders/" + woId + "/start", "{}").andExpect(status().isOk());
    }

    protected void complete(String tech, long woId) throws Exception {
        postAs(tech, "/api/work-orders/" + woId + "/complete", "{}").andExpect(status().isOk());
    }

    protected JsonNode workOrder(String token, long woId) throws Exception {
        return bodyOf(getAs(token, "/api/work-orders/" + woId).andExpect(status().isOk()));
    }

    // ---- direct DB manipulation (the clock is real, so tests move the SLA window instead) ----

    /** Replace only slaDueAt (an updatable column). */
    protected void setDue(long woId, Instant due) {
        tx.executeWithoutResult(s -> {
            WorkOrder wo = workOrderRepository.findById(woId).orElseThrow();
            wo.setSlaDueAt(due);
        });
    }

    /** Replace created_at and sla_due_at (created_at is not updatable through the entity, hence native SQL). */
    protected void setWindow(long woId, Instant createdAt, Instant due) {
        tx.executeWithoutResult(s -> entityManager
                .createNativeQuery("update work_orders set created_at = :c, sla_due_at = :d where id = :id")
                .setParameter("c", createdAt)
                .setParameter("d", due)
                .setParameter("id", woId)
                .executeUpdate());
    }

    protected WorkOrder reload(long woId) {
        return workOrderRepository.findById(woId).orElseThrow();
    }
}
