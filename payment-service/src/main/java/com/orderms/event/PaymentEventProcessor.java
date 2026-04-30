package com.orderms.event;

import com.orderms.common.event.OrderPlacedEvent;
import com.orderms.common.event.PaymentProcessedEvent;
import com.orderms.common.util.KafkaTopics;
import com.orderms.entity.Payment;
import com.orderms.entity.PaymentStatus;
import com.orderms.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

/**
 * Payment processing engine:
 * <ol>
 *   <li>Consumes {@code order.placed} → processes simulated payment</li>
 *   <li>Persists {@link Payment} record in PostgreSQL</li>
 *   <li>Publishes {@code payment.processed} → consumed by order-service + notification-service</li>
 * </ol>
 *
 * <p>In production, replace the simulated gateway call with a real provider SDK
 * (Stripe, Braintree, Adyen, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProcessor {

    private final PaymentRepository             paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.gateway.success-rate:0.85}")
    private double successRate;

    @Value("${payment.gateway.processing-delay-ms:500}")
    private long processingDelayMs;

    private final Random random = new Random();

    // ── Consumer ──────────────────────────────────────────────────────────────

    @KafkaListener(
            topics           = KafkaTopics.ORDER_PLACED,
            groupId          = KafkaTopics.GROUP_PAYMENT_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onOrderPlaced(
            @Payload OrderPlacedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received OrderPlacedEvent – orderId={}, amount={}, partition={}, offset={}",
                event.getOrderId(), event.getTotalAmount(), partition, offset);

        // Idempotency guard – skip if already processed
        if (paymentRepository.findByOrderId(event.getOrderId()).isPresent()) {
            log.warn("Duplicate OrderPlacedEvent for orderId={} – skipping", event.getOrderId());
            return;
        }

        // Simulate gateway latency
        simulateProcessingDelay();

        String    transactionId = UUID.randomUUID().toString();
        boolean   success       = random.nextDouble() < successRate;
        PaymentStatus status    = success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        String    failureReason = success ? null : "Insufficient funds (simulated)";

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .userEmail(event.getUserEmail())
                .transactionId(transactionId)
                .amount(event.getTotalAmount())
                .currency(event.getCurrency())
                .status(status)
                .paymentMethod("CREDIT_CARD")
                .failureReason(failureReason)
                .gatewayResponse("{\"gateway\":\"simulated\",\"status\":\"" + status + "\"}")
                .build();

        paymentRepository.save(payment);
        log.info("Payment {} for orderId={}: status={}", transactionId, event.getOrderId(), status);

        // Publish result
        PaymentProcessedEvent result = PaymentProcessedEvent.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .userEmail(event.getUserEmail())
                .transactionId(transactionId)
                .amount(event.getTotalAmount())
                .currency(event.getCurrency())
                .paymentStatus(status.name())
                .failureReason(failureReason)
                .processedAt(Instant.now())
                .source("payment-service")
                .build();

        kafkaTemplate.send(KafkaTopics.PAYMENT_PROCESSED, event.getOrderId(), result)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish PaymentProcessedEvent: {}", ex.getMessage());
                    else log.debug("PaymentProcessedEvent published for orderId={}", event.getOrderId());
                });
    }

    private void simulateProcessingDelay() {
        try { Thread.sleep(processingDelayMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
