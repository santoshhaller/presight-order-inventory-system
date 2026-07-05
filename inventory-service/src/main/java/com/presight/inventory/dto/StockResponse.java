package com.presight.inventory.dto;

public record StockResponse(
        String sku,
        boolean success,
        int remainingQuantity,
        String message
) {
}
