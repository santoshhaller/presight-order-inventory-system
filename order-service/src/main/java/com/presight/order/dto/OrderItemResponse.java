package com.presight.order.dto;

import com.presight.order.model.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getSku(), item.getProductName(), item.getQuantity(), item.getUnitPrice());
    }
}
