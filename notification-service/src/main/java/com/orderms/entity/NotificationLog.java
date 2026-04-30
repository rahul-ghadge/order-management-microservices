package com.orderms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Audit log for every notification dispatched.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notif_user_id",   columnList = "user_id"),
        @Index(name = "idx_notif_order_id",  columnList = "order_id"),
        @Index(name = "idx_notif_type",      columnList = "notification_type")
})
public class NotificationLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id",           nullable = false) private String userId;
    @Column(name = "user_email",        nullable = false, length = 200) private String userEmail;
    @Column(name = "order_id")                            private String orderId;
    @Column(name = "notification_type", nullable = false, length = 50) private String notificationType;
    @Column(name = "channel",           nullable = false, length = 20) private String channel;
    @Column(name = "subject",           length = 500)     private String subject;
    @Column(name = "body",              columnDefinition = "TEXT") private String body;
    @Column(name = "status",            nullable = false, length = 20) private String status;
    @Column(name = "error_message",     columnDefinition = "TEXT") private String errorMessage;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;
}
