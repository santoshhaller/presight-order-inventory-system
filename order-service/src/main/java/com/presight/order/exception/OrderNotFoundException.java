package com.presight.order.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String reference) {
        super("Order not found: " + reference);
    }
}
