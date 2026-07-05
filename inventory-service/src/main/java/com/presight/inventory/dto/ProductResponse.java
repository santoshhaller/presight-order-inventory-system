package com.presight.inventory.dto;

import com.presight.inventory.model.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        BigDecimal price,
        Integer quantity,
        boolean lowStock,
        Instant updatedAt
) {
    public static ProductResponse from(Product p, int effectiveThreshold) {
        return new ProductResponse(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getPrice(),
                p.getQuantity(),
                p.getQuantity() <= effectiveThreshold,
                p.getUpdatedAt()
        );
    }
}
