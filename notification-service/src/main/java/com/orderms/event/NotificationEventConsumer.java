package com.orderms.event;

import com.orderms.common.event.OrderStatusChangedEvent;
import com.orderms.common.event.PaymentProcessedEvent;
import com.orderms.common.util.KafkaTopics;
import com.orderms.entity.NotificationLog;
import com.orderms.repository.NotificationLogRepository;
import com.orderms.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Listens to Kafka topics and dispatches email notifications accordingly.
 *
 * <p>Topics consumed:
 * <ul>
 *   <li>{@code payment.processed}     → payment success / failure emails</li>
 *   <li>{@code order.status.changed}  → order status update emails</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final EmailNotificationService   emailService;
    private final NotificationLogRepository  logRepository;

    // ── Payment result ────────────────────────────────────────────────────────

    @KafkaListener(
            topics           = KafkaTopics.PAYMENT_PROCESSED,
            groupId          = KafkaTopics.GROUP_NOTIFICATION_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentProcessed(
            @Payload PaymentProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        log.info("Notification trigger – PaymentProcessed: orderId={}, status={}, partition={}",
                event.getOrderId(), event.getPaymentStatus(), partition);

        String type    = "SUCCESS".equalsIgnoreCase(event.getPaymentStatus())
                         ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
        String subject = "SUCCESS".equalsIgnoreCase(event.getPaymentStatus())
                         ? "✅ Payment confirmed for your order " + event.getOrderId()
                         : "❌ Payment failed for your order "   + event.getOrderId();

        String body = buildPaymentEmailBody(event);
        dispatchEmail(event.getUserId(), event.getUserEmail(), event.getOrderId(), type, subject, body);
    }

    // ── Order status change ───────────────────────────────────────────────────

    @KafkaListener(
            topics           = KafkaTopics.ORDER_STATUS_CHANGED,
            groupId          = KafkaTopics.GROUP_NOTIFICATION_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(
            @Payload OrderStatusChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        log.info("Notification trigger – OrderStatusChanged: orderId={}, status={}, partition={}",
                event.getOrderId(), event.getNewStatus(), partition);

        String subject = "📦 Order " + event.getOrderId() + " status updated to " + event.getNewStatus();
        String body    = buildOrderStatusEmailBody(event);
        dispatchEmail(event.getUserId(), event.getUserEmail(), event.getOrderId(),
                "ORDER_STATUS_CHANGED", subject, body);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void dispatchEmail(String userId, String email, String orderId,
                                String type, String subject, String body) {
        String status = "SENT";
        String error  = null;
        try {
            emailService.sendEmail(email, subject, body);
            log.info("Email dispatched – type={}, to={}", type, email);
        } catch (Exception ex) {
            status = "FAILED";
            error  = ex.getMessage();
            log.error("Email dispatch failed – type={}, to={}: {}", type, email, ex.getMessage());
        }

        logRepository.save(NotificationLog.builder()
                .userId(userId).userEmail(email).orderId(orderId)
                .notificationType(type).channel("EMAIL")
                .subject(subject).body(body)
                .status(status).errorMessage(error)
                .build());
    }

    private String buildPaymentEmailBody(PaymentProcessedEvent e) {
        if ("SUCCESS".equalsIgnoreCase(e.getPaymentStatus())) {
            return String.format(
                    "Dear Customer,\n\nYour payment of %s %s for order %s has been successfully processed.\n" +
                    "Transaction ID: %s\n\nThank you for your order!\n\nOrder Management System",
                    e.getCurrency(), e.getAmount(), e.getOrderId(), e.getTransactionId());
        } else {
            return String.format(
                    "Dear Customer,\n\nUnfortunately, your payment for order %s could not be processed.\n" +
                    "Reason: %s\n\nPlease try again or contact support.\n\nOrder Management System",
                    e.getOrderId(), e.getFailureReason());
        }
    }

    private String buildOrderStatusEmailBody(OrderStatusChangedEvent e) {
        return String.format(
                "Dear Customer,\n\nYour order %s status has been updated.\n" +
                "Previous status: %s\nNew status: %s\n%s\n\nOrder Management System",
                e.getOrderId(), e.getPreviousStatus(), e.getNewStatus(),
                e.getReason() != null ? "Reason: " + e.getReason() : "");
    }
}
