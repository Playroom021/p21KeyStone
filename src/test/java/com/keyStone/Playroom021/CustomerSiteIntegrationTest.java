package com.keyStone.Playroom021;

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
 * KEYSTONE Step 4 - Customer + Site management integration tests.
 *
 * Runs through the real Spring Security filter chain against the H2 in-memory test
 * database (see src/test/resources/application.properties). Every test creates its own
 * users and uses a random tag in company/site names, so tests are order-independent and
 * search assertions only ever match rows the test itself created.
 *
 * Covers: Customer CRUD, Site CRUD, validation, the customer<->site relationship,
 * pagination/search/sort, role authorization, and CUSTOMER own-data-only isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerSiteIntegrationTest {

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

    /** Signs up a fresh user through the real API and returns their JWT. */
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

    private long createCustomer(String token, String companyName, String contactEmail) throws Exception {
        String response = mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("companyName", companyName, "contactEmail", contactEmail)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createSite(String token, long customerId, String name, String city) throws Exception {
        String response = mockMvc.perform(post("/api/customers/" + customerId + "/sites")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", name, "addressLine", "1 Main Road", "city", city)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    /** The customer id a CUSTOMER-role user is linked to, via the API itself. */
    private long ownCustomerId(String customerToken) throws Exception {
        String response = mockMvc.perform(get("/api/customers/me")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    /** GET with optional query parameters given as name/value pairs (avoids URL-template re-encoding of % and spaces). */
    private ResultActions getAs(String token, String url, String... nameValuePairs) throws Exception {
        var request = get(url).header("Authorization", bearer(token));
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request = request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions putAs(String token, String url, String body) throws Exception {
        return mockMvc.perform(put(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postAs(String token, String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions deleteAs(String token, String url) throws Exception {
        return mockMvc.perform(delete(url).header("Authorization", bearer(token)));
    }

    // ------------------------------------------------------------- Customer CRUD

    @Test
    void customer_create_returns201WithFields() throws Exception {
        String manager = signupToken("MANAGER", null);
        String name = "Acme " + tag();

        postAs(manager, "/api/customers", json("companyName", name, "contactEmail", "ops@acme.test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.companyName").value(name))
                .andExpect(jsonPath("$.contactEmail").value("ops@acme.test"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void customer_create_trimsCompanyName() throws Exception {
        String manager = signupToken("MANAGER", null);
        String name = "Padded " + tag();

        postAs(manager, "/api/customers", json("companyName", "   " + name + "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value(name));
    }

    @Test
    void customer_read_returnsCustomerById() throws Exception {
        String manager = signupToken("MANAGER", null);
        String name = "Readable " + tag();
        long id = createCustomer(manager, name, "read@acme.test");

        getAs(manager, "/api/customers/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.companyName").value(name));
    }

    @Test
    void customer_update_changesFields() throws Exception {
        String manager = signupToken("MANAGER", null);
        long id = createCustomer(manager, "Before " + tag(), "before@acme.test");
        String newName = "After " + tag();

        putAs(manager, "/api/customers/" + id, json("companyName", newName, "contactEmail", "after@acme.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.companyName").value(newName))
                .andExpect(jsonPath("$.contactEmail").value("after@acme.test"));

        getAs(manager, "/api/customers/" + id)
                .andExpect(jsonPath("$.companyName").value(newName));
    }

    @Test
    void customer_delete_returns204ThenGet404() throws Exception {
        String manager = signupToken("MANAGER", null);
        long id = createCustomer(manager, "Doomed " + tag(), null);

        deleteAs(manager, "/api/customers/" + id).andExpect(status().isNoContent());
        getAs(manager, "/api/customers/" + id).andExpect(status().isNotFound());
    }

    @Test
    void customer_getUnknownId_returns404() throws Exception {
        String manager = signupToken("MANAGER", null);

        getAs(manager, "/api/customers/999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    void customer_updateAndDeleteUnknownId_return404() throws Exception {
        String manager = signupToken("MANAGER", null);

        putAs(manager, "/api/customers/999999999", json("companyName", "Ghost"))
                .andExpect(status().isNotFound());
        deleteAs(manager, "/api/customers/999999999").andExpect(status().isNotFound());
    }

    // ------------------------------------------------- Customer validation

    @Test
    void customer_create_blankCompanyName_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);

        postAs(manager, "/api/customers", json("companyName", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("companyName: Company name is required"));
    }

    @Test
    void customer_create_missingCompanyName_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);

        postAs(manager, "/api/customers", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void customer_create_companyNameTooLong_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);

        postAs(manager, "/api/customers", json("companyName", "a".repeat(151)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customer_create_invalidContactEmail_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);

        postAs(manager, "/api/customers", json("companyName", "Bad Email " + tag(), "contactEmail", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customer_update_blankCompanyName_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);
        long id = createCustomer(manager, "Valid " + tag(), null);

        putAs(manager, "/api/customers/" + id, json("companyName", ""))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------- Customer pagination / search / sort

    @Test
    void customer_list_paginatesAndSearchesCaseInsensitively() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tag = tag();
        for (int i = 1; i <= 3; i++) {
            createCustomer(manager, "Alpha " + tag + " " + i, null);
        }

        getAs(manager, "/api/customers?search=" + tag.toUpperCase() + "&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].companyName").value("Alpha " + tag + " 1"));

        getAs(manager, "/api/customers?search=" + tag + "&size=2&page=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("Alpha " + tag + " 3"));
    }

    @Test
    void customer_list_searchMatchesContactEmail() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tag = tag();
        long id = createCustomer(manager, "Emailed " + tag, "billing-" + tag + "@acme.test");
        createCustomer(manager, "Other " + tag, null);

        getAs(manager, "/api/customers?search=billing-" + tag)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void customer_list_sortDescending() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tag = tag();
        for (int i = 1; i <= 3; i++) {
            createCustomer(manager, "Sorted " + tag + " " + i, null);
        }

        getAs(manager, "/api/customers?search=" + tag + "&sortBy=companyName&direction=desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyName").value("Sorted " + tag + " 3"))
                .andExpect(jsonPath("$.content[2].companyName").value("Sorted " + tag + " 1"));
    }

    @Test
    void customer_list_searchTreatsPercentAsLiteral() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tag = tag();
        long literal = createCustomer(manager, "Wild " + tag + "%X", null);
        createCustomer(manager, "Wild " + tag + "ZZ", null);

        // "<tag>%" must match only the name that literally contains a percent sign.
        getAs(manager, "/api/customers", "search", tag + "%")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(literal));
    }

    @Test
    void customer_list_invalidPagingParams_return400() throws Exception {
        String manager = signupToken("MANAGER", null);

        getAs(manager, "/api/customers?page=-1").andExpect(status().isBadRequest());
        getAs(manager, "/api/customers?size=0").andExpect(status().isBadRequest());
        getAs(manager, "/api/customers?size=101").andExpect(status().isBadRequest());
        getAs(manager, "/api/customers?sortBy=password").andExpect(status().isBadRequest());
        getAs(manager, "/api/customers?direction=sideways").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------- Site CRUD

    @Test
    void site_create_returns201WithCustomerLink() throws Exception {
        String manager = signupToken("MANAGER", null);
        String customerName = "Owner " + tag();
        long customerId = createCustomer(manager, customerName, null);

        postAs(manager, "/api/customers/" + customerId + "/sites",
                json("name", "  HQ Tower  ", "addressLine", "12 Park Street", "city", "Noida"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.customerName").value(customerName))
                .andExpect(jsonPath("$.name").value("HQ Tower"))
                .andExpect(jsonPath("$.addressLine").value("12 Park Street"))
                .andExpect(jsonPath("$.city").value("Noida"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void site_read_returnsSiteById() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Owner " + tag(), null);
        long siteId = createSite(manager, customerId, "Warehouse", "Delhi");

        getAs(manager, "/api/sites/" + siteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(siteId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.name").value("Warehouse"));
    }

    @Test
    void site_update_changesFieldsButNotCustomer() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerA = createCustomer(manager, "Owner A " + tag(), null);
        long customerB = createCustomer(manager, "Owner B " + tag(), null);
        long siteId = createSite(manager, customerA, "Old Name", "Delhi");

        // A customerId in the body is not part of the update contract and must be ignored.
        putAs(manager, "/api/sites/" + siteId,
                json("name", "New Name", "addressLine", "99 New Road", "city", "Pune", "customerId", customerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.city").value("Pune"))
                .andExpect(jsonPath("$.customerId").value(customerA));

        getAs(manager, "/api/sites/" + siteId)
                .andExpect(jsonPath("$.customerId").value(customerA));
    }

    @Test
    void site_delete_returns204ThenGet404() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Owner " + tag(), null);
        long siteId = createSite(manager, customerId, "Temporary", null);

        deleteAs(manager, "/api/sites/" + siteId).andExpect(status().isNoContent());
        getAs(manager, "/api/sites/" + siteId).andExpect(status().isNotFound());
    }

    @Test
    void site_unknownId_returns404() throws Exception {
        String manager = signupToken("MANAGER", null);

        getAs(manager, "/api/sites/999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Site not found"));
        putAs(manager, "/api/sites/999999999", json("name", "Ghost")).andExpect(status().isNotFound());
        deleteAs(manager, "/api/sites/999999999").andExpect(status().isNotFound());
    }

    @Test
    void site_create_forUnknownCustomer_returns404() throws Exception {
        String manager = signupToken("MANAGER", null);

        postAs(manager, "/api/customers/999999999/sites", json("name", "Orphan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    // ---------------------------------------------------- Site validation

    @Test
    void site_create_blankName_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Owner " + tag(), null);

        postAs(manager, "/api/customers/" + customerId + "/sites", json("name", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: Site name is required"));
    }

    @Test
    void site_create_fieldsTooLong_return400() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Owner " + tag(), null);
        String url = "/api/customers/" + customerId + "/sites";

        postAs(manager, url, json("name", "n".repeat(151))).andExpect(status().isBadRequest());
        postAs(manager, url, json("name", "ok", "addressLine", "a".repeat(256))).andExpect(status().isBadRequest());
        postAs(manager, url, json("name", "ok", "city", "c".repeat(101))).andExpect(status().isBadRequest());
    }

    @Test
    void site_update_blankName_returns400() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Owner " + tag(), null);
        long siteId = createSite(manager, customerId, "Valid", null);

        putAs(manager, "/api/sites/" + siteId, json("name", "")).andExpect(status().isBadRequest());
    }

    // ------------------------------------- Customer <-> Site relationship

    @Test
    void customer_canHaveMultipleSites_andEachSiteBelongsToOneCustomer() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerA = createCustomer(manager, "Multi A " + tag(), null);
        long customerB = createCustomer(manager, "Multi B " + tag(), null);

        long a1 = createSite(manager, customerA, "A Site 1", "Delhi");
        long a2 = createSite(manager, customerA, "A Site 2", "Noida");
        long b1 = createSite(manager, customerB, "B Site 1", "Pune");

        getAs(manager, "/api/customers/" + customerA + "/sites")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(a1))
                .andExpect(jsonPath("$.content[0].customerId").value(customerA))
                .andExpect(jsonPath("$.content[1].id").value(a2))
                .andExpect(jsonPath("$.content[1].customerId").value(customerA));

        getAs(manager, "/api/customers/" + customerB + "/sites")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b1))
                .andExpect(jsonPath("$.content[0].customerId").value(customerB));
    }

    @Test
    void sites_list_canFilterByCustomerAndSearch() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tag = tag();
        long customerA = createCustomer(manager, "Filter A " + tag, null);
        long customerB = createCustomer(manager, "Filter B " + tag, null);
        createSite(manager, customerA, "Depot " + tag, "Delhi");
        createSite(manager, customerA, "Office " + tag, "Delhi");
        createSite(manager, customerB, "Depot " + tag, "Pune");

        // Search across all customers by site name.
        getAs(manager, "/api/sites", "search", "depot " + tag)
                .andExpect(jsonPath("$.totalElements").value(2));

        // Restrict to one customer.
        getAs(manager, "/api/sites", "customerId", String.valueOf(customerA), "search", "depot " + tag)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerId").value(customerA));

        // Search also matches city.
        getAs(manager, "/api/sites?customerId=" + customerB + "&search=pune")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sites_list_paginates() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Pager " + tag(), null);
        for (int i = 1; i <= 5; i++) {
            createSite(manager, customerId, "Site " + i, "Delhi");
        }

        getAs(manager, "/api/customers/" + customerId + "/sites?size=2&page=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Site 5"));

        getAs(manager, "/api/customers/" + customerId + "/sites?sortBy=name&direction=desc&size=1")
                .andExpect(jsonPath("$.content[0].name").value("Site 5"));

        getAs(manager, "/api/sites?customerId=" + customerId + "&sortBy=bogus")
                .andExpect(status().isBadRequest());
    }

    @Test
    void sites_list_withUnknownCustomerFilter_returns404() throws Exception {
        String manager = signupToken("MANAGER", null);

        getAs(manager, "/api/sites?customerId=999999999").andExpect(status().isNotFound());
        getAs(manager, "/api/customers/999999999/sites").andExpect(status().isNotFound());
    }

    @Test
    void delete_customerWithSites_returns409UntilSitesRemoved() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Has Sites " + tag(), null);
        long siteId = createSite(manager, customerId, "Only Site", null);

        deleteAs(manager, "/api/customers/" + customerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        deleteAs(manager, "/api/sites/" + siteId).andExpect(status().isNoContent());
        deleteAs(manager, "/api/customers/" + customerId).andExpect(status().isNoContent());
    }

    @Test
    void delete_customerWithPortalAccount_returns409() throws Exception {
        String manager = signupToken("MANAGER", null);
        String customerToken = signupToken("CUSTOMER", "Has Login " + tag());
        long customerId = ownCustomerId(customerToken);

        deleteAs(manager, "/api/customers/" + customerId).andExpect(status().isConflict());
    }

    @Test
    void delete_siteWithWorkOrders_returns409() throws Exception {
        String manager = signupToken("MANAGER", null);
        String customerToken = signupToken("CUSTOMER", "Has Orders " + tag());
        long customerId = ownCustomerId(customerToken);
        long siteId = createSite(customerToken, customerId, "Ordered Site", "Delhi");

        // Raise a work order against the site through the existing customer-portal endpoint.
        postAs(customerToken, "/api/customer/work-orders",
                json("siteId", siteId, "title", "Broken AC", "priority", "HIGH"))
                .andExpect(status().isCreated());

        deleteAs(manager, "/api/sites/" + siteId).andExpect(status().isConflict());
    }

    // ------------------------------------------------ Authorization (staff roles)

    @Test
    void unauthenticated_requests_return401() throws Exception {
        mockMvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/customers/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                .content(json("companyName", "X"))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sites")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/sites/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dispatcher_canCreateAndUpdate_butNotDelete() throws Exception {
        String dispatcher = signupToken("DISPATCHER", null);
        long customerId = createCustomer(dispatcher, "Dispatched " + tag(), null);
        long siteId = createSite(dispatcher, customerId, "Dispatch Site", "Delhi");

        putAs(dispatcher, "/api/customers/" + customerId, json("companyName", "Renamed " + tag()))
                .andExpect(status().isOk());
        putAs(dispatcher, "/api/sites/" + siteId, json("name", "Renamed Site"))
                .andExpect(status().isOk());

        deleteAs(dispatcher, "/api/sites/" + siteId).andExpect(status().isForbidden());
        deleteAs(dispatcher, "/api/customers/" + customerId).andExpect(status().isForbidden());
    }

    @Test
    void technician_isReadOnly() throws Exception {
        String manager = signupToken("MANAGER", null);
        String technician = signupToken("TECHNICIAN", null);
        long customerId = createCustomer(manager, "Tech View " + tag(), null);
        long siteId = createSite(manager, customerId, "Tech Site", "Delhi");

        // Reads are allowed.
        getAs(technician, "/api/customers").andExpect(status().isOk());
        getAs(technician, "/api/customers/" + customerId).andExpect(status().isOk());
        getAs(technician, "/api/customers/" + customerId + "/sites").andExpect(status().isOk());
        getAs(technician, "/api/sites").andExpect(status().isOk());
        getAs(technician, "/api/sites/" + siteId).andExpect(status().isOk());

        // Every write is forbidden.
        postAs(technician, "/api/customers", json("companyName", "Nope")).andExpect(status().isForbidden());
        putAs(technician, "/api/customers/" + customerId, json("companyName", "Nope")).andExpect(status().isForbidden());
        deleteAs(technician, "/api/customers/" + customerId).andExpect(status().isForbidden());
        postAs(technician, "/api/customers/" + customerId + "/sites", json("name", "Nope")).andExpect(status().isForbidden());
        putAs(technician, "/api/sites/" + siteId, json("name", "Nope")).andExpect(status().isForbidden());
        deleteAs(technician, "/api/sites/" + siteId).andExpect(status().isForbidden());
    }

    @Test
    void manager_hasFullAccess() throws Exception {
        String manager = signupToken("MANAGER", null);
        long customerId = createCustomer(manager, "Full " + tag(), null);
        long siteId = createSite(manager, customerId, "Full Site", "Delhi");

        getAs(manager, "/api/customers").andExpect(status().isOk());
        deleteAs(manager, "/api/sites/" + siteId).andExpect(status().isNoContent());
        deleteAs(manager, "/api/customers/" + customerId).andExpect(status().isNoContent());
    }

    // ------------------------------- Authorization (CUSTOMER: own data only)

    @Test
    void customer_me_returnsOwnCustomerRecord() throws Exception {
        String company = "My Company " + tag();
        String customerToken = signupToken("CUSTOMER", company);

        getAs(customerToken, "/api/customers/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value(company));
    }

    @Test
    void customer_me_isForbiddenForStaff() throws Exception {
        String manager = signupToken("MANAGER", null);

        getAs(manager, "/api/customers/me").andExpect(status().isForbidden());
    }

    @Test
    void customer_canReadOwnCustomerButNotOthers() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tokenA = signupToken("CUSTOMER", "Isolated A " + tag());
        long idA = ownCustomerId(tokenA);
        long idB = createCustomer(manager, "Isolated B " + tag(), null);

        getAs(tokenA, "/api/customers/" + idA).andExpect(status().isOk());

        // Someone else's customer looks exactly like a nonexistent one.
        getAs(tokenA, "/api/customers/" + idB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    void customer_cannotListOrModifyCustomers() throws Exception {
        String tokenA = signupToken("CUSTOMER", "No Admin " + tag());
        long idA = ownCustomerId(tokenA);

        getAs(tokenA, "/api/customers").andExpect(status().isForbidden());
        postAs(tokenA, "/api/customers", json("companyName", "Sneaky")).andExpect(status().isForbidden());
        putAs(tokenA, "/api/customers/" + idA, json("companyName", "Renamed")).andExpect(status().isForbidden());
        deleteAs(tokenA, "/api/customers/" + idA).andExpect(status().isForbidden());
    }

    @Test
    void customer_canManageSitesOfOwnCustomer() throws Exception {
        String tokenA = signupToken("CUSTOMER", "Own Sites " + tag());
        long idA = ownCustomerId(tokenA);

        long siteId = createSite(tokenA, idA, "My Site", "Delhi");

        getAs(tokenA, "/api/sites/" + siteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(idA));
        getAs(tokenA, "/api/customers/" + idA + "/sites")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        putAs(tokenA, "/api/sites/" + siteId, json("name", "My Renamed Site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Renamed Site"));

        // Deleting sites is a MANAGER-only action.
        deleteAs(tokenA, "/api/sites/" + siteId).andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotTouchAnotherCustomersSites() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tokenA = signupToken("CUSTOMER", "Attacker " + tag());
        long idB = createCustomer(manager, "Victim " + tag(), null);
        long victimSite = createSite(manager, idB, "Victim Site", "Delhi");

        // Create a site under someone else's customer.
        postAs(tokenA, "/api/customers/" + idB + "/sites", json("name", "Planted"))
                .andExpect(status().isNotFound());

        // List someone else's sites.
        getAs(tokenA, "/api/customers/" + idB + "/sites").andExpect(status().isNotFound());
        getAs(tokenA, "/api/sites?customerId=" + idB).andExpect(status().isNotFound());

        // Read / update / delete someone else's site by guessing its id.
        getAs(tokenA, "/api/sites/" + victimSite).andExpect(status().isNotFound());
        putAs(tokenA, "/api/sites/" + victimSite, json("name", "Hijacked")).andExpect(status().isNotFound());
        deleteAs(tokenA, "/api/sites/" + victimSite).andExpect(status().isForbidden());

        // The victim's site is untouched.
        getAs(manager, "/api/sites/" + victimSite)
                .andExpect(jsonPath("$.name").value("Victim Site"))
                .andExpect(jsonPath("$.customerId").value(idB));
    }

    @Test
    void customer_siteListing_isAlwaysScopedToOwnCustomer() throws Exception {
        String manager = signupToken("MANAGER", null);
        String tag = tag();
        String tokenA = signupToken("CUSTOMER", "Scoped A " + tag);
        long idA = ownCustomerId(tokenA);
        long idB = createCustomer(manager, "Scoped B " + tag, null);
        long mine = createSite(manager, idA, "Shared Name " + tag, "Delhi");
        createSite(manager, idB, "Shared Name " + tag, "Delhi");

        // GET /api/sites with no filter returns only the caller's own sites.
        getAs(tokenA, "/api/sites", "search", "shared name " + tag)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine))
                .andExpect(jsonPath("$.content[0].customerId").value(idA));

        // Naming their own customer explicitly is fine.
        getAs(tokenA, "/api/sites?customerId=" + idA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
