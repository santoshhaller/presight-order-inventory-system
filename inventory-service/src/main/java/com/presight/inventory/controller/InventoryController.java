package com.presight.inventory.controller;

import com.presight.inventory.dto.ProductRequest;
import com.presight.inventory.dto.ProductResponse;
import com.presight.inventory.dto.StockRequest;
import com.presight.inventory.dto.StockResponse;
import com.presight.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Product catalogue and stock management")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/products")
    @Operation(summary = "Add a new product to the catalogue")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createProduct(request));
    }

    @GetMapping("/products")
    @Operation(summary = "List all products")
    public List<ProductResponse> listProducts() {
        return inventoryService.listProducts();
    }

    @GetMapping("/products/{sku}")
    @Operation(summary = "Get a product by SKU")
    public ProductResponse getProduct(@PathVariable String sku) {
        return inventoryService.getProduct(sku);
    }

    @GetMapping("/low-stock")
    @Operation(summary = "List products at or below the configured low-stock threshold")
    public List<ProductResponse> lowStock() {
        return inventoryService.listLowStock();
    }

    @PostMapping("/reserve")
    @Operation(summary = "Deduct stock for an order line item (called by Order Service)")
    public ResponseEntity<StockResponse> reserve(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(inventoryService.reserve(request));
    }

    @PostMapping("/release")
    @Operation(summary = "Compensating action: restore previously reserved stock")
    public ResponseEntity<StockResponse> release(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(inventoryService.release(request));
    }

    @PutMapping("/products/{sku}/replenish")
    @Operation(summary = "Add stock back into the warehouse (restock)")
    public ProductResponse replenish(@PathVariable String sku, @RequestParam int quantity) {
        return inventoryService.replenish(sku, quantity);
    }
}
