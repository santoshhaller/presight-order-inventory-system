package com.presight.order.dto;

import com.presight.order.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderReference,
        String customerId,
        String status,
        BigDecimal totalAmount,
        String failureReason,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderReference(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getFailureReason(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
