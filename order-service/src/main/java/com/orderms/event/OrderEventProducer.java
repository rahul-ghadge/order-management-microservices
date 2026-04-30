package com.orderms.event;

import com.orderms.common.event.OrderPlacedEvent;
import com.orderms.common.event.OrderStatusChangedEvent;
import com.orderms.common.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for order domain events.
 * Uses async send with CompletableFuture callbacks for observability.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes an {@link OrderPlacedEvent} to {@code order.placed} topic.
     * Key = orderId, ensuring all events for the same order go to the same partition.
     */
    public void publishOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent: orderId={}", event.getOrderId());
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.ORDER_PLACED, event.getOrderId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("OrderPlacedEvent sent – topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send OrderPlacedEvent for orderId={}: {}",
                        event.getOrderId(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * Publishes an {@link OrderStatusChangedEvent} to {@code order.status.changed} topic.
     */
    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Publishing OrderStatusChangedEvent: orderId={}, newStatus={}",
                event.getOrderId(), event.getNewStatus());
        kafkaTemplate.send(KafkaTopics.ORDER_STATUS_CHANGED, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish OrderStatusChangedEvent: {}", ex.getMessage());
                });
    }
}
