package com.orderms.repository;

import com.orderms.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {
    List<NotificationLog> findByUserId(String userId);
    List<NotificationLog> findByOrderId(String orderId);
    List<NotificationLog> findByStatus(String status);
}
