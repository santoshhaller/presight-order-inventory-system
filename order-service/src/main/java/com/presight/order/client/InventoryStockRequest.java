package com.presight.order.client;

public record InventoryStockRequest(String sku, int quantity, String referenceId) {
}
