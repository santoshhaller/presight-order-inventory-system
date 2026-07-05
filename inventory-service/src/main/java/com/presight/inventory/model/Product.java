package com.presight.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single product line in the catalogue.
 *
 * `quantity` is the number of units physically sitting on the shelf.
 * We use @Version for optimistic locking as a first line of defense
 * against lost updates; the repository additionally exposes a
 * pessimistic-lock lookup for the hot path (stock deduction) where we
 * expect real contention under load and would rather block briefly
 * than retry a stale-object exception in a loop.
 */
@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = "sku"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * Per-product override for the low-stock threshold. If null, the
     * service falls back to the global threshold coming from the
     * ConfigMap (see InventoryProperties).
     */
    private Integer lowStockThresholdOverride;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
