package com.orderms.service.impl;

import com.orderms.common.event.OrderPlacedEvent;
import com.orderms.entity.Order;
import com.orderms.entity.OrderItem;
import com.orderms.entity.OrderStatus;
import com.orderms.event.OrderEventProducer;
import com.orderms.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Order business logic:
 * <ol>
 *   <li>Validate and persist the order</li>
 *   <li>Publish {@link OrderPlacedEvent} to Kafka → payment-service processes it</li>
 *   <li>Cache order lookups in Redis</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl {

    private final OrderRepository    orderRepository;
    private final OrderEventProducer orderEventProducer;

    @Transactional
    public Order createOrder(String userId, String userEmail, String shippingAddress,
                              String currency, String notes,
                              List<OrderItemInput> items) {

        log.debug("createOrder() – userId={}", userId);

        List<OrderItem> lineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        Order order = Order.builder()
                .userId(userId)
                .userEmail(userEmail)
                .shippingAddress(shippingAddress)
                .currency(currency != null ? currency : "USD")
                .notes(notes)
                .status(OrderStatus.PENDING)
                .items(lineItems)
                .build();

        for (OrderItemInput i : items) {
            BigDecimal lineTotal = i.unitPrice().multiply(BigDecimal.valueOf(i.quantity()));
            total = total.add(lineTotal);
            lineItems.add(OrderItem.builder()
                    .order(order)
                    .productId(i.productId())
                    .productName(i.productName())
                    .quantity(i.quantity())
                    .unitPrice(i.unitPrice())
                    .lineTotal(lineTotal)
                    .build());
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        log.info("Order created: id={}, total={}", saved.getId(), total);

        // Publish Kafka event → payment-service will process payment
        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(saved.getId())
                .userId(userId)
                .userEmail(userEmail)
                .totalAmount(total)
                .currency(saved.getCurrency())
                .source("order-service")
                .items(lineItems.stream().map(li -> new OrderPlacedEvent.OrderItemDto(
                        li.getProductId(), li.getProductName(),
                        li.getQuantity(), li.getUnitPrice())).toList())
                .build();

        orderEventProducer.publishOrderPlaced(event);
        return saved;
    }

    @Cacheable(value = "orders", key = "#orderId")
    public Order getOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
    }

    public List<Order> getOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllOrderByCreatedAtDesc();
    }

    @CacheEvict(value = "orders", key = "#orderId")
    @Transactional
    public Order cancelOrder(String orderId, String userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new RuntimeException("Order not found or unauthorized: " + orderId));
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    // ── Input record ──────────────────────────────────────────────────────────
    public record OrderItemInput(String productId, String productName,
                                  int quantity, BigDecimal unitPrice) {}
}
