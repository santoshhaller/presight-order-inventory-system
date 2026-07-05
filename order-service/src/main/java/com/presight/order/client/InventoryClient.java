package com.presight.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Talks to inventory-service by its Eureka logical name ("inventory-service")
 * rather than a fixed host:port - the client-side load balancer picks
 * whichever instance is healthy, which is what lets us scale that
 * service to N replicas transparently.
 *
 * Resilience is layered on top of this client in OrderOrchestrationService
 * via Resilience4j's CircuitBreaker + Retry, applied at the call site
 * rather than here so retry/fallback behaviour differs between
 * "reserve" (must NOT be blindly retried - could double-deduct) and
 * "release" (safe, idempotent-ish, retried aggressively).
 */
@FeignClient(name = "inventory-service", path = "/api/v1/inventory")
public interface InventoryClient {

    @GetMapping("/products/{sku}")
    InventoryProductResponse getProduct(@PathVariable("sku") String sku);

    @PostMapping("/reserve")
    InventoryStockResponse reserve(@RequestBody InventoryStockRequest request);

    @PostMapping("/release")
    InventoryStockResponse release(@RequestBody InventoryStockRequest request);
}
