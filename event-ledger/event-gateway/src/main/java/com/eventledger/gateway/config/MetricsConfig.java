package com.eventledger.gateway.config;

import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter eventReceivedCounter(MeterRegistry registry) {
        return Counter.builder("events.received.total")
                .description("Total number of events received by the gateway")
                .register(registry);
    }

    @Bean
    public Counter eventDuplicateCounter(MeterRegistry registry) {
        return Counter.builder("events.duplicate.total")
                .description("Total number of duplicate events rejected")
                .register(registry);
    }

    @Bean
    public Counter eventFailedCounter(MeterRegistry registry) {
        return Counter.builder("events.failed.total")
                .description("Total number of events that failed processing")
                .register(registry);
    }

    @Bean
    public Counter accountServiceErrorCounter(MeterRegistry registry) {
        return Counter.builder("account.service.errors.total")
                .description("Total number of Account Service call failures")
                .register(registry);
    }
}