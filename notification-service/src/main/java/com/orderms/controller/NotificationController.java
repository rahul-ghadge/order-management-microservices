package com.orderms.controller;

import com.orderms.common.dto.ApiResponse;
import com.orderms.entity.NotificationLog;
import com.orderms.repository.NotificationLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Notification audit log API – read-only.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Query notification delivery logs")
public class NotificationController {

    private final NotificationLogRepository logRepository;

    @GetMapping
    @Operation(summary = "List all notification logs")
    public ResponseEntity<ApiResponse<List<NotificationLog>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(logRepository.findAll()));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get notifications for a specific user")
    public ResponseEntity<ApiResponse<List<NotificationLog>>> getByUser(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(logRepository.findByUserId(userId)));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get notifications for a specific order")
    public ResponseEntity<ApiResponse<List<NotificationLog>>> getByOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(logRepository.findByOrderId(orderId)));
    }
}
