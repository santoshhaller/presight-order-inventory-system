package com.presight.order.controller;

import com.presight.order.dto.CreateOrderRequest;
import com.presight.order.dto.OrderResponse;
import com.presight.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order creation, lookup and cancellation")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order (reserves stock synchronously against Inventory Service)")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        HttpStatus status = "FAILED".equals(response.status()) ? HttpStatus.CONFLICT : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{orderReference}")
    @Operation(summary = "Get an order by its reference")
    public OrderResponse getOrder(@PathVariable String orderReference) {
        return orderService.getOrder(orderReference);
    }

    @GetMapping
    @Operation(summary = "List orders, optionally filtered by customer")
    public List<OrderResponse> listOrders(@RequestParam(required = false) String customerId) {
        return customerId != null
                ? orderService.listOrdersForCustomer(customerId)
                : orderService.listAllOrders();
    }

    @PostMapping("/{orderReference}/cancel")
    @Operation(summary = "Cancel an order and release any reserved stock")
    public OrderResponse cancelOrder(@PathVariable String orderReference) {
        return orderService.cancelOrder(orderReference);
    }
}
