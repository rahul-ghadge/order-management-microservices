package com.orderms.common.util;

/**
 * Centralised Kafka topic name constants.
 * Shared across producer and consumer services to avoid magic strings.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String ORDER_PLACED          = "order.placed";
    public static final String ORDER_STATUS_CHANGED  = "order.status.changed";
    public static final String PAYMENT_PROCESSED     = "payment.processed";
    public static final String NOTIFICATION_SEND     = "notification.send";

    // Consumer group IDs
    public static final String GROUP_PAYMENT_SERVICE       = "payment-service-group";
    public static final String GROUP_ORDER_SERVICE         = "order-service-group";
    public static final String GROUP_NOTIFICATION_SERVICE  = "notification-service-group";
}
