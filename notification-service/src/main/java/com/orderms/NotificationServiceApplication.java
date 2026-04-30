package com.orderms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Notification Service – event-driven, stateless notification dispatcher.
 * Consumes: payment.processed, order.status.changed
 * Port: 8084
 */
@Slf4j
@EnableKafka
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
        log.info("=============================================================");
        log.info("  Notification Service started on port 8084                  ");
        log.info("=============================================================");
    }
}
