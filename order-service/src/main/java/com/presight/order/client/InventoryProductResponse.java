package com.presight.order.client;

import java.math.BigDecimal;

public record InventoryProductResponse(String sku, String name, BigDecimal price, int quantity) {
}
