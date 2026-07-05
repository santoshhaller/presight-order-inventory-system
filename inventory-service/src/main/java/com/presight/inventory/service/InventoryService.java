package com.presight.inventory.service;

import com.presight.inventory.config.InventoryProperties;
import com.presight.inventory.dto.ProductRequest;
import com.presight.inventory.dto.ProductResponse;
import com.presight.inventory.dto.StockRequest;
import com.presight.inventory.dto.StockResponse;
import com.presight.inventory.exception.DuplicateSkuException;
import com.presight.inventory.exception.InsufficientStockException;
import com.presight.inventory.exception.ProductNotFoundException;
import com.presight.inventory.model.Product;
import com.presight.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final InventoryProperties inventoryProperties;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }
        Product product = Product.builder()
                .sku(request.sku())
                .name(request.name())
                .price(request.price())
                .quantity(request.quantity())
                .lowStockThresholdOverride(request.lowStockThresholdOverride())
                .build();
        Product saved = productRepository.save(product);
        log.info("Created product sku={} initialQuantity={}", saved.getSku(), saved.getQuantity());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(String sku) {
        return toResponse(findOrThrow(sku));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listLowStock() {
        return productRepository.findAllBelowThreshold(inventoryProperties.getLowStockThreshold())
                .stream().map(this::toResponse).toList();
    }

    /**
     * Deducts stock for a single SKU. Uses a pessimistic write lock so that
     * concurrent reservations for the same SKU are serialized at the
     * database rather than racing in application memory - this is the
     * piece that keeps the numbers correct under high concurrency
     * (e.g. a flash-sale scenario where dozens of orders hit one SKU
     * at once).
     *
     * Runs in its own transaction (REQUIRES_NEW is not needed here since
     * the Order Service calls this over REST - each call is already an
     * independent transaction boundary).
     */
    @Transactional
    public StockResponse reserve(StockRequest request) {
        Product product = productRepository.findBySkuForUpdate(request.sku())
                .orElseThrow(() -> new ProductNotFoundException(request.sku()));

        if (product.getQuantity() < request.quantity()) {
            log.warn("Reservation REJECTED sku={} requested={} available={} orderRef={}",
                    request.sku(), request.quantity(), product.getQuantity(), request.referenceId());
            throw new InsufficientStockException(request.sku(), request.quantity(), product.getQuantity());
        }

        product.setQuantity(product.getQuantity() - request.quantity());
        productRepository.save(product);

        log.info("Reserved sku={} qty={} remaining={} orderRef={}",
                request.sku(), request.quantity(), product.getQuantity(), request.referenceId());

        checkAndWarnLowStock(product);

        return new StockResponse(product.getSku(), true, product.getQuantity(), "Reservation successful");
    }

    /**
     * Compensating action: restores stock that was previously reserved
     * but whose order ultimately failed downstream (e.g. a different
     * item in the same order was out of stock). This is what gives us
     * data consistency across the two services without a distributed
     * transaction / 2PC - see README "Data Consistency Strategy".
     */
    @Transactional
    public StockResponse release(StockRequest request) {
        Product product = productRepository.findBySkuForUpdate(request.sku())
                .orElseThrow(() -> new ProductNotFoundException(request.sku()));

        product.setQuantity(product.getQuantity() + request.quantity());
        productRepository.save(product);

        log.info("Released sku={} qty={} remaining={} orderRef={} (compensating transaction)",
                request.sku(), request.quantity(), product.getQuantity(), request.referenceId());

        return new StockResponse(product.getSku(), true, product.getQuantity(), "Stock released");
    }

    @Transactional
    public ProductResponse replenish(String sku, int quantity) {
        Product product = productRepository.findBySkuForUpdate(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        product.setQuantity(product.getQuantity() + quantity);
        productRepository.save(product);
        log.info("Replenished sku={} by={} newQuantity={}", sku, quantity, product.getQuantity());
        return toResponse(product);
    }

    private void checkAndWarnLowStock(Product product) {
        int threshold = product.getLowStockThresholdOverride() != null
                ? product.getLowStockThresholdOverride()
                : inventoryProperties.getLowStockThreshold();

        if (product.getQuantity() <= threshold) {
            // Deliberately WARN level: this is what ops dashboards / log-based
            // alerting (e.g. an ELK or CloudWatch filter) hook into.
            log.warn("LOW STOCK WARNING sku={} name='{}' remaining={} threshold={}",
                    product.getSku(), product.getName(), product.getQuantity(), threshold);
        }
    }

    private Product findOrThrow(String sku) {
        return productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    private ProductResponse toResponse(Product product) {
        int threshold = product.getLowStockThresholdOverride() != null
                ? product.getLowStockThresholdOverride()
                : inventoryProperties.getLowStockThreshold();
        return ProductResponse.from(product, threshold);
    }
}
