package com.orderms.entity;

/**
 * Order lifecycle state machine:
 *
 * <pre>
 * PENDING → CONFIRMED → PAYMENT_PROCESSING → PAID → SHIPPED → DELIVERED
 *                                          ↘ PAYMENT_FAILED → CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PAYMENT_PROCESSING,
    PAID,
    PAYMENT_FAILED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
