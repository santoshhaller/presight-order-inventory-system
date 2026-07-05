package com.presight.order.client;

public record InventoryStockResponse(String sku, boolean success, int remainingQuantity, String message) {
}
