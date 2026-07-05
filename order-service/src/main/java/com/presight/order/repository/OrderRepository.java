package com.presight.order.repository;

import com.presight.order.model.Order;
import com.presight.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderReference(String orderReference);

    List<Order> findByCustomerId(String customerId);

    List<Order> findByStatus(OrderStatus status);
}
