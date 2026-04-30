package com.orderms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway – single ingress point for all microservices.
 * Performs JWT validation at the edge before forwarding to downstream services.
 * Port: 8080
 */
@Slf4j
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
        log.info("=============================================================");
        log.info("  API Gateway started on port 8080                          ");
        log.info("  Routes: /api/v1/auth, /api/v1/users, /api/v1/orders,     ");
        log.info("          /api/v1/payments, /api/v1/notifications           ");
        log.info("=============================================================");
    }
}
