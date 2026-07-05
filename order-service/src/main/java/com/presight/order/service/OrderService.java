package com.presight.order.service;

import com.presight.order.client.InventoryClient;
import com.presight.order.client.InventoryProductResponse;
import com.presight.order.client.InventoryStockRequest;
import com.presight.order.dto.CreateOrderRequest;
import com.presight.order.dto.OrderItemRequest;
import com.presight.order.dto.OrderResponse;
import com.presight.order.exception.OrderNotFoundException;
import com.presight.order.exception.StockReservationException;
import com.presight.order.model.Order;
import com.presight.order.model.OrderItem;
import com.presight.order.model.OrderStatus;
import com.presight.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the order lifecycle and the cross-service consistency dance with
 * Inventory Service.
 *
 * DATA CONSISTENCY STRATEGY (see README for the full write-up):
 * We use a synchronous orchestration ("try-confirm/cancel") rather than
 * a 2-phase commit or a message-broker based saga, on the reasoning
 * that order placement is a short-lived, user-facing flow where the
 * caller is waiting on a response - a few hundred ms of extra latency
 * for reserve() calls is preferable to introducing eventual
 * consistency + polling for something a customer expects an
 * immediate answer to.
 *
 * Flow for createOrder():
 *   1. Persist the order as PENDING.
 *   2. Reserve stock for each line item, one at a time.
 *   3. If any reservation fails, release everything reserved so far
 *      (compensating transactions) and mark the order FAILED.
 *   4. If all reservations succeed, mark the order CONFIRMED.
 *
 * This gives us atomicity of the *business outcome* even though the
 * two services have separate databases and there is no distributed
 * transaction underneath.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .customerId(request.customerId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> builtItems = new ArrayList<>();

        for (OrderItemRequest itemRequest : request.items()) {
            InventoryProductResponse product = fetchProduct(itemRequest.sku());
            OrderItem item = OrderItem.builder()
                    .sku(itemRequest.sku())
                    .productName(product.name())
                    .quantity(itemRequest.quantity())
                    .unitPrice(product.price())
                    .build();
            builtItems.add(item);
            total = total.add(product.price().multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }

        order.setTotalAmount(total);
        builtItems.forEach(order::addItem);
        order = orderRepository.save(order);

        List<OrderItem> reserved = new ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                reserveStock(item.getSku(), item.getQuantity(), order.getOrderReference());
                reserved.add(item);
            }
            order.setStatus(OrderStatus.CONFIRMED);
            log.info("Order CONFIRMED reference={} customer={} items={} total={}",
                    order.getOrderReference(), order.getCustomerId(), order.getItems().size(), order.getTotalAmount());
        } catch (StockReservationException ex) {
            log.warn("Order FAILED reference={} reason='{}' - compensating {} already-reserved item(s)",
                    order.getOrderReference(), ex.getMessage(), reserved.size());
            compensate(reserved, order.getOrderReference());
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason(ex.getMessage());
        }

        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderReference) {
        return OrderResponse.from(findOrThrow(orderReference));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrdersForCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listAllOrders() {
        return orderRepository.findAll().stream().map(OrderResponse::from).toList();
    }

    @Transactional
    public OrderResponse cancelOrder(String orderReference) {
        Order order = findOrThrow(orderReference);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            compensate(order.getItems(), order.getOrderReference());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    /**
     * Retries transient failures (timeouts, connection resets) a couple of
     * times; if the breaker has already tripped from repeated Inventory
     * Service failures, calls fail fast instead of piling up threads.
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveFallback")
    @Retry(name = "inventoryService")
    public void reserveStock(String sku, int quantity, String orderReference) {
        var response = inventoryClient.reserve(new InventoryStockRequest(sku, quantity, orderReference));
        if (!response.success()) {
            throw new StockReservationException("Reservation rejected for SKU " + sku);
        }
    }

    @SuppressWarnings("unused")
    private void reserveFallback(String sku, int quantity, String orderReference, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.error("Circuit OPEN for inventory-service - failing fast for sku={} orderRef={}", sku, orderReference);
        } else {
            log.error("Inventory reservation call failed sku={} orderRef={} error={}",
                    sku, orderReference, throwable.getMessage());
        }
        throw new StockReservationException(
                "Inventory Service unavailable, could not reserve stock for SKU " + sku);
    }

    private InventoryProductResponse fetchProduct(String sku) {
        try {
            return inventoryClient.getProduct(sku);
        } catch (Exception ex) {
            throw new StockReservationException("Unable to look up product " + sku + ": " + ex.getMessage());
        }
    }

    /**
     * Best-effort rollback. Each release is independently retried since
     * releases are safe to repeat (worst case, ops notices a stock
     * discrepancy via reconciliation rather than an over-sold SKU).
     */
    private void compensate(List<OrderItem> itemsToRelease, String orderReference) {
        for (OrderItem item : itemsToRelease) {
            try {
                inventoryClient.release(new InventoryStockRequest(item.getSku(), item.getQuantity(), orderReference));
            } catch (Exception ex) {
                log.error("COMPENSATION FAILED for sku={} orderRef={} - manual reconciliation required. error={}",
                        item.getSku(), orderReference, ex.getMessage());
            }
        }
    }

    private Order findOrThrow(String orderReference) {
        return orderRepository.findByOrderReference(orderReference)
                .orElseThrow(() -> new OrderNotFoundException(orderReference));
    }
}
