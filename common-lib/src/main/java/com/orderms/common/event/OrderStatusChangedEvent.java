package com.orderms.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

// ─────────────────────────────────────────────────────────────────────────────
// ORDER STATUS CHANGED  →  published by order-service
//                           consumed by notification-service
// ─────────────────────────────────────────────────────────────────────────────
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderStatusChangedEvent extends BaseEvent {
    private String orderId;
    private String userId;
    private String userEmail;
    private String previousStatus;
    private String newStatus;
    private String reason;
}
