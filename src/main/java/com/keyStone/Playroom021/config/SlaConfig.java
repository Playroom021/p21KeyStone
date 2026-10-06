package com.keyStone.Playroom021.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * SLA infrastructure: a {@link Clock} (so time-dependent logic is testable) and scheduling support.
 * The scheduled job itself is {@code SlaScheduler}, which can be switched off with
 * {@code app.sla.scheduler.enabled=false}.
 */
@Configuration
@EnableScheduling
public class SlaConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
