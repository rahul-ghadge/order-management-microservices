package com.orderms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Order Service – manages the full order lifecycle.
 * Publishes OrderPlacedEvent → Kafka; consumes PaymentProcessedEvent ← Kafka.
 * Port: 8082
 */
@Slf4j
@EnableKafka
@EnableCaching
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
        log.info("=============================================================");
        log.info("  Order Service started on port 8082                        ");
        log.info("  Swagger: http://localhost:8082/swagger-ui.html             ");
        log.info("=============================================================");
    }
}
