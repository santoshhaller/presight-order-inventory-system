package com.presight.inventory.service;

import com.presight.inventory.config.InventoryProperties;
import com.presight.inventory.dto.ProductRequest;
import com.presight.inventory.dto.StockRequest;
import com.presight.inventory.exception.InsufficientStockException;
import com.presight.inventory.exception.ProductNotFoundException;
import com.presight.inventory.model.Product;
import com.presight.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    private InventoryProperties inventoryProperties;

    @InjectMocks
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryProperties = new InventoryProperties();
        inventoryProperties.setLowStockThreshold(10);
        inventoryService = new InventoryService(productRepository, inventoryProperties);
    }

    private Product sampleProduct(int quantity) {
        return Product.builder()
                .id(1L)
                .sku("SKU-001")
                .name("Wireless Mouse")
                .price(BigDecimal.valueOf(19.99))
                .quantity(quantity)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void reserve_deductsStock_whenEnoughAvailable() {
        Product product = sampleProduct(50);
        when(productRepository.findBySkuForUpdate("SKU-001")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = inventoryService.reserve(new StockRequest("SKU-001", 5, "ORDER-1"));

        assertThat(response.success()).isTrue();
        assertThat(response.remainingQuantity()).isEqualTo(45);
    }

    @Test
    void reserve_throwsInsufficientStock_whenNotEnoughAvailable() {
        Product product = sampleProduct(3);
        when(productRepository.findBySkuForUpdate("SKU-001")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> inventoryService.reserve(new StockRequest("SKU-001", 10, "ORDER-2")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void reserve_throwsNotFound_whenSkuDoesNotExist() {
        when(productRepository.findBySkuForUpdate("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserve(new StockRequest("MISSING", 1, "ORDER-3")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void release_addsStockBack() {
        Product product = sampleProduct(10);
        when(productRepository.findBySkuForUpdate("SKU-001")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = inventoryService.release(new StockRequest("SKU-001", 4, "ORDER-1"));

        assertThat(response.remainingQuantity()).isEqualTo(14);
    }
}
