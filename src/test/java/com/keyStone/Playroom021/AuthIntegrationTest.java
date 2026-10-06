package com.keyStone.Playroom021;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyStone.Playroom021.entity.User;
import com.keyStone.Playroom021.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KEYSTONE Step 3 - Authentication & Authorization integration tests.
 *
 * Covers, end to end through the real Spring Security filter chain and the
 * H2 in-memory test database (see src/test/resources/application.properties):
 *   1. Registration (signup)
 *   2. Successful login (JWT issued)
 *   3. Login with wrong password (401)
 *   4. Missing JWT on a protected endpoint (401)
 *   5. Invalid/malformed JWT on a protected endpoint (401)
 *   6. Role-protected endpoint: allowed for the correct role, forbidden (403)
 *      for a different authenticated role
 *   7. No password field ever appears in an auth API response body
 *
 * Each test uses a unique, randomly generated email so tests can run in any
 * order without colliding on the app_users.email unique constraint, without
 * needing per-test database cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@keystone.test";
    }

    private String signupJson(String fullName, String email, String password, String role, String companyName) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "password": "%s",
                  "role": "%s"%s
                }
                """.formatted(fullName, email, password, role,
                companyName != null ? ",\n \"companyName\": \"" + companyName + "\"" : "");
    }

    // ---- 1. Registration ----

    @Test
    void signup_withValidManagerPayload_returns201WithTokenAndNoPassword() throws Exception {
        String email = uniqueEmail("manager");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Ada Manager", email, "password123", "MANAGER", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signup_withDuplicateEmail_returns409() throws Exception {
        String email = uniqueEmail("dupe");
        String body = signupJson("First User", email, "password123", "MANAGER", null);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Same email again -> 409, not a 500 or duplicate row.
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void signup_customerRole_requiresCompanyName() throws Exception {
        String email = uniqueEmail("nocompany");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("No Company", email, "password123", "CUSTOMER", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Company name is required for a customer account"));
    }

    @Test
    void signup_allFourRoles_areAccepted() throws Exception {
        for (String role : new String[]{"MANAGER", "DISPATCHER", "TECHNICIAN"}) {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupJson(role + " User", uniqueEmail(role.toLowerCase()),
                                    "password123", role, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value(role));
        }

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Customer User", uniqueEmail("customer"),
                                "password123", "CUSTOMER", "Acme Co")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.companyName").value("Acme Co"));
    }

    // ---- 2 & 3. Login: success and wrong password ----

    @Test
    void login_withCorrectPassword_returns200WithToken() throws Exception {
        String email = uniqueEmail("loginok");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Login OK", email, "correct-password", "MANAGER", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "correct-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = uniqueEmail("loginbad");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Login Bad", email, "correct-password", "MANAGER", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_withUnknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "whatever123"}
                                """.formatted(uniqueEmail("nosuchuser"))))
                .andExpect(status().isUnauthorized());
    }

    // ---- 4 & 5. Missing / invalid JWT on a protected endpoint ----

    @Test
    void protectedEndpoint_withNoAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withMalformedJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer this.is.not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withGarbageBearerToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidJwt_returns200() throws Exception {
        String email = uniqueEmail("validjwt");
        String token = signupAndGetToken("Valid JWT", email, "password123", "MANAGER", null);

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ---- 6. Role-protected endpoint ----

    @Test
    void managerOnlyEndpoint_withManagerToken_returns200() throws Exception {
        String token = signupAndGetToken("Manager Access", uniqueEmail("mgr-ok"),
                "password123", "MANAGER", null);

        mockMvc.perform(get("/api/dashboard/manager")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void managerOnlyEndpoint_withCustomerToken_returns403() throws Exception {
        String token = signupAndGetToken("Customer Denied", uniqueEmail("cust-denied"),
                "password123", "CUSTOMER", "Beta LLC");

        mockMvc.perform(get("/api/dashboard/manager")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerOnlyPortalEndpoint_withTechnicianToken_returns403() throws Exception {
        String token = signupAndGetToken("Technician Denied", uniqueEmail("tech-denied"),
                "password123", "TECHNICIAN", null);

        mockMvc.perform(get("/api/customer/sites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerOnlyPortalEndpoint_withCustomerToken_returns200() throws Exception {
        String token = signupAndGetToken("Customer Allowed", uniqueEmail("cust-ok"),
                "password123", "CUSTOMER", "Gamma Inc");

        mockMvc.perform(get("/api/customer/sites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ---- 7. Passwords never present in BCrypt-hashed form or plaintext in any auth response ----

    @Test
    void signupResponse_neverContainsPasswordField_evenAsRawJson() throws Exception {
        String email = uniqueEmail("nopwfield");
        String responseBody = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("No Password Leak", email, "super-secret-pw", "MANAGER", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.has("password")).isFalse();
        assertThat(responseBody).doesNotContain("super-secret-pw");
    }

    // ---- 8. Password is stored BCrypt-hashed, never in plaintext ----

    @Test
    void password_isPersistedAsBcryptHash_notPlaintext() throws Exception {
        String email = uniqueEmail("bcrypt");
        String rawPassword = "super-secret-pw-123";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("BCrypt Check", email, rawPassword, "MANAGER", null)))
                .andExpect(status().isCreated());

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo(rawPassword);
        // BCrypt hashes start with $2a$, $2b$ or $2y$ followed by the cost factor.
        assertThat(stored.getPassword()).matches("^\\$2[aby]\\$\\d{2}\\$.+");
    }

    // ---- helper ----

    private String signupAndGetToken(String fullName, String email, String password,
                                      String role, String companyName) throws Exception {
        String responseBody = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(fullName, email, password, role, companyName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }
}
