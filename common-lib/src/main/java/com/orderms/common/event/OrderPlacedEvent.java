package com.orderms.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// ORDER PLACED  →  published by order-service, consumed by payment-service
// ─────────────────────────────────────────────────────────────────────────────
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderPlacedEvent extends BaseEvent {

    private String  orderId;
    private String  userId;
    private String  userEmail;
    private BigDecimal totalAmount;
    private String  currency;
    private List<OrderItemDto> items;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OrderItemDto {
        private String productId;
        private String productName;
        private int    quantity;
        private BigDecimal unitPrice;
    }
}
