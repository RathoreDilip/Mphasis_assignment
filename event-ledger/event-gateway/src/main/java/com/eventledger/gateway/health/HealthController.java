package com.eventledger.gateway.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.application.name}")
    private String serviceName;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("service", serviceName);
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());

        // Check DB connectivity
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            db.put("status", "UP");
            db.put("type", "H2 In-Memory");
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
            health.put("status", "DEGRADED");
        }
        health.put("database", db);

        log.debug("Health check requested — status={}", health.get("status"));
        return ResponseEntity.ok(health);
    }
}