package com.orderms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Payment Service – processes payments asynchronously via Kafka.
 * Consumes: order.placed  |  Publishes: payment.processed
 * Port: 8083
 */
@Slf4j
@EnableKafka
@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
        log.info("=============================================================");
        log.info("  Payment Service started on port 8083                       ");
        log.info("  Swagger: http://localhost:8083/swagger-ui.html              ");
        log.info("=============================================================");
    }
}
