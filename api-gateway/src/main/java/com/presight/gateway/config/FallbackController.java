package com.presight.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Hit when a downstream service's circuit breaker is open, so callers
 * get a fast, clean 503 instead of hanging until a socket timeout.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/orders")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        return response("order-service");
    }

    @RequestMapping("/inventory")
    public ResponseEntity<Map<String, Object>> inventoryServiceFallback() {
        return response("inventory-service");
    }

    private ResponseEntity<Map<String, Object>> response(String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 503,
                "error", "Service Unavailable",
                "message", service + " is currently unavailable, please retry shortly"
        ));
    }
}
