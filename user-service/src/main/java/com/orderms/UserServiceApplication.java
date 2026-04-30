package com.orderms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * User Service – handles registration, authentication (JWT), token refresh,
 * logout (token blacklisting via Redis), and user profile management.
 *
 * <p>Port: 8081
 */
@Slf4j
@EnableCaching
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.orderms.repository")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
        log.info("================================================================");
        log.info("  User Service started on port 8081                            ");
        log.info("  Swagger: http://localhost:8081/swagger-ui.html                ");
        log.info("================================================================");
    }
}
