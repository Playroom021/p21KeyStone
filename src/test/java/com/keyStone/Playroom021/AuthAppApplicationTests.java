package com.keyStone.Playroom021;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Foundation-phase smoke test: verifies the Spring Boot context wires up
 * cleanly (all beans, security config, JPA repositories) against the
 * in-memory H2 database defined in src/test/resources/application.properties.
 * No business-logic assertions are made here by design (Step 1 scope).
 */
@SpringBootTest
class AuthAppApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: a successful context load is the assertion.
    }
}
