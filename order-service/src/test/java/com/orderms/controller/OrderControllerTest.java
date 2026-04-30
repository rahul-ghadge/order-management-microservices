package com.orderms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderms.entity.Order;
import com.orderms.entity.OrderStatus;
import com.orderms.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@ActiveProfiles("test")
@DisplayName("OrderController – MockMvc Tests")
class OrderControllerTest {

    @Autowired private MockMvc      mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private OrderServiceImpl orderService;

    private Order sampleOrder() {
        return Order.builder()
                .id("order-uuid-1").userId("user-1").userEmail("user@test.com")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("300.00"))
                .currency("USD").items(List.of()).build();
    }

    // ── POST /api/v1/orders ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user-1", roles = "USER")
    @DisplayName("POST /orders – 201 with valid request body")
    void createOrder_shouldReturn201() throws Exception {
        when(orderService.createOrder(any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleOrder());

        Map<String, Object> request = Map.of(
                "userEmail",       "user@test.com",
                "shippingAddress", "123 Test St",
                "currency",        "USD",
                "items", List.of(Map.of(
                        "productId",   "prod-1",
                        "productName", "Widget",
                        "quantity",    2,
                        "unitPrice",   150.00))
        );

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "user-1", roles = "USER")
    @DisplayName("POST /orders – 400 when items list is empty")
    void createOrder_shouldReturn400_emptyItems() throws Exception {
        Map<String, Object> request = Map.of(
                "userEmail",       "user@test.com",
                "shippingAddress", "123 Test St",
                "items",           List.of()   // empty – fails @NotEmpty
        );

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/orders ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user-1", roles = "USER")
    @DisplayName("GET /orders – 200 returns user's orders")
    void getOrders_shouldReturn200() throws Exception {
        when(orderService.getOrdersByUser("user-1"))
                .thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("order-uuid-1"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("GET /orders – ADMIN sees all orders")
    void getOrders_admin_shouldReturnAll() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    // ── GET /api/v1/orders/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user-1", roles = "USER")
    @DisplayName("GET /orders/{id} – 200 returns cached order")
    void getOrderById_shouldReturn200() throws Exception {
        when(orderService.getOrderById("order-uuid-1")).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/v1/orders/order-uuid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("order-uuid-1"))
                .andExpect(jsonPath("$.data.totalAmount").value(300.00));
    }

    // ── PATCH /api/v1/orders/{id}/cancel ─────────────────────────────────────

    @Test
    @WithMockUser(username = "user-1", roles = "USER")
    @DisplayName("PATCH /orders/{id}/cancel – 200 cancels order")
    void cancelOrder_shouldReturn200() throws Exception {
        Order cancelled = sampleOrder();
        cancelled.setStatus(OrderStatus.CANCELLED);
        when(orderService.cancelOrder("order-uuid-1", "user-1")).thenReturn(cancelled);

        mockMvc.perform(patch("/api/v1/orders/order-uuid-1/cancel").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    // ── Unauthenticated access ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /orders – 401 when no JWT provided")
    void getOrders_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }
}
