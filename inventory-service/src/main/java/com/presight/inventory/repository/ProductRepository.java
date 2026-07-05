package com.presight.inventory.repository;

import com.presight.inventory.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    /**
     * Row-level lock used exclusively during stock deduction/release.
     * Under concurrent orders hitting the same SKU, this serializes
     * writers at the database instead of letting them race on a
     * read-modify-write in application memory. Held only for the
     * duration of the transaction, which is deliberately kept short.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.sku = :sku")
    Optional<Product> findBySkuForUpdate(@Param("sku") String sku);

    @Query("select p from Product p where p.quantity <= coalesce(p.lowStockThresholdOverride, :globalThreshold)")
    List<Product> findAllBelowThreshold(@Param("globalThreshold") int globalThreshold);
}
