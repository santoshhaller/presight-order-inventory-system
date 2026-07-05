package com.presight.order.exception;

/**
 * Raised when the Inventory Service rejects a reservation (out of stock)
 * or is unreachable and the circuit breaker / fallback gives up.
 */
public class StockReservationException extends RuntimeException {
    public StockReservationException(String message) {
        super(message);
    }
}
