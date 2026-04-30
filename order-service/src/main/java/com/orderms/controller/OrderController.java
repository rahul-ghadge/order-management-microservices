package com.orderms.controller;

import com.orderms.common.dto.ApiResponse;
import com.orderms.entity.Order;
import com.orderms.service.impl.OrderServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order lifecycle REST endpoints.
 *
 * <ul>
 *   <li>POST   /api/v1/orders          – place a new order</li>
 *   <li>GET    /api/v1/orders          – list orders (admin: all; user: own)</li>
 *   <li>GET    /api/v1/orders/{id}     – get order by ID</li>
 *   <li>PATCH  /api/v1/orders/{id}/cancel – cancel an order</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Orders", description = "Order lifecycle management – place, view, cancel orders")
public class OrderController {

    private final OrderServiceImpl orderService;

    // ── POST /api/v1/orders ───────────────────────────────────────────────────

    @PostMapping
    @Operation(
            summary = "Place a new order",
            description = "Creates an order, persists to PostgreSQL, and publishes an " +
                    "OrderPlacedEvent to Kafka → consumed by payment-service.")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("POST /orders – user={}, items={}", principal.getUsername(), request.getItems().size());

        List<OrderServiceImpl.OrderItemInput> items = request.getItems().stream()
                .map(i -> new OrderServiceImpl.OrderItemInput(
                        i.getProductId(), i.getProductName(),
                        i.getQuantity(), i.getUnitPrice()))
                .toList();

        Order order = orderService.createOrder(
                principal.getUsername(),
                request.getUserEmail(),
                request.getShippingAddress(),
                request.getCurrency(),
                request.getNotes(),
                items);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", order));
    }

    // ── GET /api/v1/orders ────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List orders – ADMIN sees all; authenticated user sees own orders")
    public ResponseEntity<ApiResponse<List<Order>>> getOrders(
            @AuthenticationPrincipal UserDetails principal) {

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<Order> orders = isAdmin
                ? orderService.getAllOrders()
                : orderService.getOrdersByUser(principal.getUsername());

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    // ── GET /api/v1/orders/{id} ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific order by ID (cached in Redis)")
    public ResponseEntity<ApiResponse<Order>> getOrderById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    // ── PATCH /api/v1/orders/{id}/cancel ─────────────────────────────────────

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a PENDING or CONFIRMED order")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("PATCH /orders/{}/cancel – user={}", id, principal.getUsername());
        Order cancelled = orderService.cancelOrder(id, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", cancelled));
    }

    // ── Admin – GET all orders ────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: list all orders regardless of owner [ADMIN only]")
    public ResponseEntity<ApiResponse<List<Order>>> adminGetAll() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders()));
    }

    // ── Request DTOs (inner classes keep controller self-contained) ───────────

    @Data
    public static class CreateOrderRequest {
        @NotBlank
        private String userEmail;
        @NotBlank
        private String shippingAddress;
        @NotEmpty @Valid
        private List<LineItemRequest> items;
        private String currency = "USD";
        private String notes;

        @Data
        public static class LineItemRequest {
            @NotBlank private String    productId;
            @NotBlank private String    productName;
            @Min(1)   private int       quantity;
            @DecimalMin("0.01") private BigDecimal unitPrice;
        }
    }
}
