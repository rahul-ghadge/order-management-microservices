//package com.orderms.service;
//
//import com.orderms.entity.Order;
//import com.orderms.entity.OrderStatus;
//import com.orderms.event.OrderEventProducer;
//import com.orderms.repository.OrderRepository;
//import com.orderms.service.impl.OrderServiceImpl;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.*;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("OrderServiceImpl – Unit Tests")
//class OrderServiceImplTest {
//
//    @Mock private OrderRepository    orderRepository;
//    @Mock private OrderEventProducer orderEventProducer;
//
//    @InjectMocks
//    private OrderServiceImpl orderService;
//
//    private Order sampleOrder(OrderStatus status) {
//        return Order.builder()
//                .id("order-uuid-1").userId("user-1").userEmail("user@test.com")
//                .status(status).totalAmount(new BigDecimal("300.00"))
//                .currency("USD").items(List.of()).build();
//    }
//
//    @Test
//    @DisplayName("createOrder() – persists order and publishes Kafka event")
//    void createOrder_persistsAndPublishesEvent() {
//        Order saved = sampleOrder(OrderStatus.PENDING);
//        when(orderRepository.save(any(Order.class))).thenReturn(saved);
//
//        List<OrderServiceImpl.OrderItemInput> items = List.of(
//                new OrderServiceImpl.OrderItemInput("prod-1", "Widget", 2, new BigDecimal("150.00")));
//
//        Order result = orderService.createOrder("user-1", "user@test.com",
//                "123 St", "USD", null, items);
//
//        assertThat(result.getId()).isEqualTo("order-uuid-1");
//        verify(orderRepository).save(any(Order.class));
//        verify(orderEventProducer).publishOrderPlaced(any());
//    }
//
//    @Test
//    @DisplayName("cancelOrder() – PENDING order can be cancelled")
//    void cancelOrder_pendingOrder_cancelled() {
//        Order pending = sampleOrder(OrderStatus.PENDING);
//        when(orderRepository.findByIdAndUserId("order-uuid-1", "user-1"))
//                .thenReturn(Optional.of(pending));
//        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
//
//        Order result = orderService.cancelOrder("order-uuid-1", "user-1");
//
//        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
//    }
//
//    @Test
//    @DisplayName("cancelOrder() – PAID order cannot be cancelled")
//    void cancelOrder_paidOrder_throwsIllegalState() {
//        Order paid = sampleOrder(OrderStatus.PAID);
//        when(orderRepository.findByIdAndUserId("order-uuid-1", "user-1"))
//                .thenReturn(Optional.of(paid));
//
//        assertThatThrownBy(() -> orderService.cancelOrder("order-uuid-1", "user-1"))
//                .isInstanceOf(IllegalStateException.class)
//                .hasMessageContaining("PAID");
//    }
//
//    @Test
//    @DisplayName("getOrderById() – not found throws RuntimeException")
//    void getOrderById_notFound_throws() {
//        when(orderRepository.findById("ghost")).thenReturn(Optional.empty());
//        assertThatThrownBy(() -> orderService.getOrderById("ghost"))
//                .isInstanceOf(RuntimeException.class)
//                .hasMessageContaining("ghost");
//    }
//
//    @Test
//    @DisplayName("getOrdersByUser() – delegates to repository")
//    void getOrdersByUser_delegatesToRepository() {
//        when(orderRepository.findByUserId("user-1"))
//                .thenReturn(List.of(sampleOrder(OrderStatus.PAID)));
//        List<Order> orders = orderService.getOrdersByUser("user-1");
//        assertThat(orders).hasSize(1);
//        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.PAID);
//    }
//}
