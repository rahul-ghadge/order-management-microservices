package com.orderms.event;

import com.orderms.common.event.OrderStatusChangedEvent;
import com.orderms.common.event.PaymentProcessedEvent;
import com.orderms.common.util.KafkaTopics;
import com.orderms.entity.Order;
import com.orderms.entity.OrderStatus;
import com.orderms.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Listens to payment results and transitions order status accordingly.
 *
 * <pre>
 *   payment.processed → order transitions to PAID or PAYMENT_FAILED
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderRepository     orderRepository;
    private final OrderEventProducer  orderEventProducer;

    @KafkaListener(
            topics          = KafkaTopics.PAYMENT_PROCESSED,
            groupId         = KafkaTopics.GROUP_ORDER_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentProcessed(
            @Payload PaymentProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received PaymentProcessedEvent – orderId={}, status={}, partition={}, offset={}",
                event.getOrderId(), event.getPaymentStatus(), partition, offset);

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Order not found for paymentEvent: orderId={}", event.getOrderId());
            return;
        }

        String previousStatus = order.getStatus().name();
        OrderStatus newStatus;

        if ("SUCCESS".equalsIgnoreCase(event.getPaymentStatus())) {
            newStatus = OrderStatus.PAID;
            order.setPaymentTransactionId(event.getTransactionId());
        } else {
            newStatus = OrderStatus.PAYMENT_FAILED;
            order.setFailureReason(event.getFailureReason());
        }

        order.setStatus(newStatus);
        orderRepository.save(order);

        log.info("Order {} status updated: {} → {}", order.getId(), previousStatus, newStatus);

        // Propagate status change for notification-service
        OrderStatusChangedEvent statusEvent = OrderStatusChangedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .previousStatus(previousStatus)
                .newStatus(newStatus.name())
                .source("order-service")
                .build();
        orderEventProducer.publishOrderStatusChanged(statusEvent);
    }
}
