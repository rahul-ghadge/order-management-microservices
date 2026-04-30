//package com.orderms.service;
//
//import com.orderms.common.event.OrderPlacedEvent;
//import com.orderms.entity.Payment;
//import com.orderms.entity.PaymentStatus;
//import com.orderms.event.PaymentEventProcessor;
//import com.orderms.repository.PaymentRepository;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.*;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//import java.util.concurrent.CompletableFuture;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("PaymentEventProcessor – Unit Tests")
//class PaymentEventProcessorTest {
//
//    @Mock private PaymentRepository             paymentRepository;
//    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
//
//    @InjectMocks
//    private PaymentEventProcessor processor;
//
//    @BeforeEach
//    void setup() {
//        ReflectionTestUtils.setField(processor, "successRate",        1.0); // always succeed in tests
//        ReflectionTestUtils.setField(processor, "processingDelayMs",  0L);  // no delay in tests
//    }
//
//    private OrderPlacedEvent sampleEvent() {
//        return OrderPlacedEvent.builder()
//                .orderId("order-uuid-1")
//                .userId("user-1")
//                .userEmail("user@test.com")
//                .totalAmount(new BigDecimal("300.00"))
//                .currency("USD")
//                .source("order-service")
//                .items(List.of())
//                .build();
//    }
//
//    @Test
//    @DisplayName("onOrderPlaced() – processes payment and saves Payment record")
//    void onOrderPlaced_savesPaymentRecord() {
//        when(paymentRepository.findByOrderId("order-uuid-1")).thenReturn(Optional.empty());
//        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
//        when(kafkaTemplate.send(anyString(), anyString(), any()))
//                .thenReturn(CompletableFuture.completedFuture(null));
//
//        processor.onOrderPlaced(sampleEvent(), 0, 0L);
//
//        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
//        verify(paymentRepository).save(captor.capture());
//        Payment saved = captor.getValue();
//
//        assertThat(saved.getOrderId()).isEqualTo("order-uuid-1");
//        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
//        assertThat(saved.getAmount()).isEqualByComparingTo("300.00");
//    }
//
//    @Test
//    @DisplayName("onOrderPlaced() – duplicate event is ignored (idempotency guard)")
//    void onOrderPlaced_duplicateEvent_skipped() {
//        Payment existing = Payment.builder().orderId("order-uuid-1").build();
//        when(paymentRepository.findByOrderId("order-uuid-1")).thenReturn(Optional.of(existing));
//
//        processor.onOrderPlaced(sampleEvent(), 0, 1L);
//
//        verify(paymentRepository, never()).save(any());
//        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
//    }
//
//    @Test
//    @DisplayName("onOrderPlaced() – publishes PaymentProcessedEvent after saving")
//    void onOrderPlaced_publishesPaymentProcessedEvent() {
//        when(paymentRepository.findByOrderId("order-uuid-1")).thenReturn(Optional.empty());
//        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
//        when(kafkaTemplate.send(anyString(), anyString(), any()))
//                .thenReturn(CompletableFuture.completedFuture(null));
//
//        processor.onOrderPlaced(sampleEvent(), 0, 0L);
//
//        verify(kafkaTemplate).send(eq("payment.processed"), eq("order-uuid-1"), any());
//    }
//
//    @Test
//    @DisplayName("onOrderPlaced() – failed payment sets FAILED status")
//    void onOrderPlaced_failedPayment() {
//        ReflectionTestUtils.setField(processor, "successRate", 0.0); // always fail
//        when(paymentRepository.findByOrderId("order-uuid-1")).thenReturn(Optional.empty());
//        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
//        when(kafkaTemplate.send(anyString(), anyString(), any()))
//                .thenReturn(CompletableFuture.completedFuture(null));
//
//        processor.onOrderPlaced(sampleEvent(), 0, 0L);
//
//        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
//        verify(paymentRepository).save(captor.capture());
//        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
//        assertThat(captor.getValue().getFailureReason()).isNotBlank();
//    }
//}
