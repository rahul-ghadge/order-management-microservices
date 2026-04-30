package com.orderms.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.time.Instant;

// ─────────────────────────────────────────────────────────────────────────────
// PAYMENT PROCESSED  →  published by payment-service
//                        consumed by order-service + notification-service
// ─────────────────────────────────────────────────────────────────────────────
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentProcessedEvent extends BaseEvent {

    private String     orderId;
    private String     userId;
    private String     userEmail;
    private String     transactionId;
    private BigDecimal amount;
    private String     currency;
    /** SUCCESS | FAILED | REFUNDED */
    private String     paymentStatus;
    private String     failureReason;
    private Instant    processedAt;
}
