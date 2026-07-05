package com.presight.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * Backed by the "inventory-threshold-config" ConfigMap (see
 * k8s/configmap.yaml). spring-cloud-starter-kubernetes-client-config
 * watches that ConfigMap and, on change, publishes a RefreshEvent -
 * @RefreshScope makes sure this bean is re-created with the new
 * value instead of us needing to restart the pod.
 *
 * Locally (outside K8s) the value simply comes from application.yml
 * or the LOW_STOCK_THRESHOLD env var.
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "inventory")
@Getter
@Setter
public class InventoryProperties {

    /**
     * Global default low-stock threshold. When a product's remaining
     * quantity drops to or below this number, a warning is logged
     * and the product shows up in GET /api/v1/inventory/low-stock.
     */
    private int lowStockThreshold = 10;
}
