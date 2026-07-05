package com.presight.order.service;

import com.presight.order.client.InventoryClient;
import com.presight.order.client.InventoryProductResponse;
import com.presight.order.client.InventoryStockRequest;
import com.presight.order.client.InventoryStockResponse;
import com.presight.order.dto.CreateOrderRequest;
import com.presight.order.dto.OrderItemRequest;
import com.presight.order.dto.OrderResponse;
import com.presight.order.model.Order;
import com.presight.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, inventoryClient);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createOrder_confirms_whenAllReservationsSucceed() {
        when(inventoryClient.getProduct("SKU-1"))
                .thenReturn(new InventoryProductResponse("SKU-1", "Keyboard", BigDecimal.valueOf(49.99), 100));
        when(inventoryClient.reserve(any(InventoryStockRequest.class)))
                .thenReturn(new InventoryStockResponse("SKU-1", true, 98, "ok"));

        CreateOrderRequest request = new CreateOrderRequest("customer-1", List.of(new OrderItemRequest("SKU-1", 2)));
        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(99.98));
        verify(inventoryClient, never()).release(any());
    }

    @Test
    void createOrder_failsAndCompensates_whenSecondItemReservationFails() {
        when(inventoryClient.getProduct("SKU-1"))
                .thenReturn(new InventoryProductResponse("SKU-1", "Keyboard", BigDecimal.valueOf(49.99), 100));
        when(inventoryClient.getProduct("SKU-2"))
                .thenReturn(new InventoryProductResponse("SKU-2", "Mouse", BigDecimal.valueOf(19.99), 0));

        when(inventoryClient.reserve(argThat(r -> r != null && "SKU-1".equals(r.sku()))))
                .thenReturn(new InventoryStockResponse("SKU-1", true, 99, "ok"));
        when(inventoryClient.reserve(argThat(r -> r != null && "SKU-2".equals(r.sku()))))
                .thenReturn(new InventoryStockResponse("SKU-2", false, 0, "insufficient"));

        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest("SKU-1", 1), new OrderItemRequest("SKU-2", 5))
        );

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).contains("SKU-2");
        // SKU-1 was reserved before SKU-2 failed, so it must be released (compensating transaction)
        verify(inventoryClient, times(1)).release(argThat(r -> r != null && "SKU-1".equals(r.sku())));
    }
}
