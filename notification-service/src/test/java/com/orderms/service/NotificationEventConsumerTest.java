//package com.orderms.service;
//
//import com.orderms.common.event.OrderStatusChangedEvent;
//import com.orderms.common.event.PaymentProcessedEvent;
//import com.orderms.entity.NotificationLog;
//import com.orderms.event.NotificationEventConsumer;
//import com.orderms.repository.NotificationLogRepository;
//import com.orderms.service.EmailNotificationService;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.*;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.math.BigDecimal;
//import java.time.Instant;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("NotificationEventConsumer – Unit Tests")
//class NotificationEventConsumerTest {
//
//    @Mock private EmailNotificationService   emailService;
//    @Mock private NotificationLogRepository  logRepository;
//
//    @InjectMocks
//    private NotificationEventConsumer consumer;
//
//    // ── PaymentProcessed – SUCCESS ────────────────────────────────────────────
//
//    @Test
//    @DisplayName("onPaymentProcessed() – SUCCESS → sends email and logs SENT")
//    void onPaymentProcessed_success_sendsEmailAndLogs() {
//        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
//                .orderId("order-1").userId("user-1").userEmail("user@test.com")
//                .transactionId("txn-123").amount(new BigDecimal("250.00"))
//                .currency("USD").paymentStatus("SUCCESS").processedAt(Instant.now())
//                .source("payment-service").build();
//
//        when(logRepository.save(any(NotificationLog.class)))
//                .thenAnswer(i -> i.getArgument(0));
//
//        consumer.onPaymentProcessed(event, 0);
//
//        verify(emailService).sendEmail(
//                eq("user@test.com"),
//                contains("Payment confirmed"),
//                contains("order-1"));
//
//        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
//        verify(logRepository).save(captor.capture());
//        NotificationLog log = captor.getValue();
//        assertThat(log.getStatus()).isEqualTo("SENT");
//        assertThat(log.getNotificationType()).isEqualTo("PAYMENT_SUCCESS");
//        assertThat(log.getOrderId()).isEqualTo("order-1");
//        assertThat(log.getChannel()).isEqualTo("EMAIL");
//    }
//
//    // ── PaymentProcessed – FAILED ─────────────────────────────────────────────
//
//    @Test
//    @DisplayName("onPaymentProcessed() – FAILED → sends failure email and logs SENT")
//    void onPaymentProcessed_failed_sendsFailureEmail() {
//        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
//                .orderId("order-2").userId("user-1").userEmail("user@test.com")
//                .transactionId("txn-456").amount(new BigDecimal("100.00"))
//                .currency("USD").paymentStatus("FAILED")
//                .failureReason("Insufficient funds").processedAt(Instant.now())
//                .source("payment-service").build();
//
//        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));
//
//        consumer.onPaymentProcessed(event, 0);
//
//        verify(emailService).sendEmail(
//                eq("user@test.com"),
//                contains("Payment failed"),
//                contains("Insufficient funds"));
//
//        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
//        verify(logRepository).save(captor.capture());
//        assertThat(captor.getValue().getNotificationType()).isEqualTo("PAYMENT_FAILED");
//    }
//
//    // ── PaymentProcessed – email dispatch failure ─────────────────────────────
//
//    @Test
//    @DisplayName("onPaymentProcessed() – email failure → logs FAILED status, no exception thrown")
//    void onPaymentProcessed_emailFails_logsFailedStatus() {
//        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
//                .orderId("order-3").userId("user-1").userEmail("bad@email")
//                .paymentStatus("SUCCESS").amount(BigDecimal.TEN).currency("USD")
//                .transactionId("txn-789").processedAt(Instant.now())
//                .source("payment-service").build();
//
//        doThrow(new RuntimeException("SMTP connection refused"))
//                .when(emailService).sendEmail(anyString(), anyString(), anyString());
//        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));
//
//        // Must NOT throw – notification failures should be swallowed and logged
//        consumer.onPaymentProcessed(event, 0);
//
//        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
//        verify(logRepository).save(captor.capture());
//        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
//        assertThat(captor.getValue().getErrorMessage()).contains("SMTP");
//    }
//
//    // ── OrderStatusChanged ────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("onOrderStatusChanged() – sends status email and logs SENT")
//    void onOrderStatusChanged_sendsEmailAndLogs() {
//        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
//                .orderId("order-4").userId("user-1").userEmail("user@test.com")
//                .previousStatus("CONFIRMED").newStatus("SHIPPED")
//                .reason("Dispatched from warehouse").source("order-service").build();
//
//        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));
//
//        consumer.onOrderStatusChanged(event, 0);
//
//        verify(emailService).sendEmail(
//                eq("user@test.com"),
//                contains("SHIPPED"),
//                contains("order-4"));
//
//        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
//        verify(logRepository).save(captor.capture());
//        assertThat(captor.getValue().getNotificationType()).isEqualTo("ORDER_STATUS_CHANGED");
//        assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
//    }
//
//    // ── Edge case: null failureReason ─────────────────────────────────────────
//
//    @Test
//    @DisplayName("onPaymentProcessed() – null failureReason does not cause NPE")
//    void onPaymentProcessed_nullFailureReason_noNpe() {
//        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
//                .orderId("order-5").userId("user-1").userEmail("user@test.com")
//                .paymentStatus("SUCCESS").amount(BigDecimal.TEN).currency("USD")
//                .transactionId("txn-000").failureReason(null).processedAt(Instant.now())
//                .source("payment-service").build();
//
//        when(logRepository.save(any())).thenAnswer(i -> i.getArgument(0));
//
//        consumer.onPaymentProcessed(event, 0);
//
//        verify(emailService).sendEmail(anyString(), anyString(), anyString());
//    }
//}
