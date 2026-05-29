# Order Management Microservices — Detailed Technical Guide

This guide documents the repository structure, per-module responsibilities, configuration properties, commands, and an index of classes + method signatures with short descriptions and key code snippets.

---

## Table of Contents

- Repository overview
- How to run (scripts & commands)
- Configuration / properties per service
- Per-module guide (common-lib, api-gateway, user-service, order-service, payment-service, notification-service)
- Index: classes and method signatures
- Key code snippets

---

## Repository overview

- Parent POM: `pom.xml` — multi-module Maven project. Modules: `common-lib`, `api-gateway`, `user-service`, `order-service`, `payment-service`, `notification-service`.
- Stack: Spring Boot 3.4, Java 21, PostgreSQL per service, Redis for caching/blacklist/rate-limiting, Apache Kafka for events, Docker Compose for local dev, Prometheus/Grafana for monitoring.
- Main assets:
  - `scripts/start-dev.sh` — one-command startup using Docker Compose
  - `scripts/init-databases.sql` — create DBs
  - `docker-compose.yml` — full stack composition (DBs, Kafka, Redis, services, monitoring)

---

## How to run

Quick start (from README):

```bash
chmod +x scripts/start-dev.sh
./scripts/start-dev.sh
```

Manual (build & run):

```bash
mvn clean install -DskipTests
# start infra
docker compose up -d postgres-user postgres-order postgres-payment postgres-notification redis zookeeper kafka kafka-ui
# wait then start services
docker compose up -d api-gateway user-service order-service payment-service notification-service
# monitoring
docker compose up -d prometheus grafana
```

Useful commands:
- `docker compose logs -f` — tail logs
- `docker compose down` — stop
- `docker compose down -v` — stop + wipe DB volumes
- `mvn test` — run tests

---

## Configuration / properties (high-level)

Environment variables used across services:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`
- `JWT_SECRET` — signing secret for JWTs
- `NOTIFICATION_EMAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`

Service-specific important properties are present in each service `application.yml`. Highlights:
- `user-service`:
  - `server.port: 8081`
  - `jwt.access-token-expiry-ms: 900000` (15 min)
  - `jwt.refresh-token-expiry-ms: 604800000` (7 days)
  - Redis prefixes: `redis.prefix.token-blacklist` = `blacklist:`
- `order-service`:
  - `server.port: 8082`
  - Redis: `redis.prefix.order-cache` and TTL
  - Kafka producer/consumer config and type mapping for events
- `payment-service`:
  - `server.port: 8083`
  - Simulated gateway: `payment.gateway.success-rate: 0.85`, `processing-delay-ms: 500`
- `notification-service`:
  - `server.port: 8084`
  - `notification.email.enabled` toggles real email delivery
- `api-gateway`:
  - `server.port: 8080` and route definitions
  - Uses `JwtAuthenticationFilter` to validate JWT at edge

---

## Per-module guide

### common-lib (Non-Spring Shared Library)
Purpose: Shared DTOs, domain events, exceptions, and constants used across all services.

**Responsibilities**:
- Define DTOs (Data Transfer Objects) for inter-service communication
- Define Kafka events (OrderPlacedEvent, PaymentProcessedEvent, OrderStatusChangedEvent)
- Define enums (OrderStatus, PaymentStatus, UserRole)
- Provide utility classes and constants

**Dependencies**: Only core Java libraries (no Spring)  

**Key Artifacts**:
- `UserDTO`, `OrderDTO`, `PaymentDTO`, `NotificationDTO`
- `OrderPlacedEvent`, `PaymentProcessedEvent`, `OrderStatusChangedEvent`
- `OrderStatus` enum, `PaymentStatus` enum

Key files:
- `ApiResponse<T>` — generic response envelope with builder helpers: `success(data)`, `success(message, data)`, `error(message, errorCode)`.
- `BaseEvent` — base class for Kafka events, fields: `eventId`, `source`, `occurredAt`.
- `OrderPlacedEvent`, `PaymentProcessedEvent`, `OrderStatusChangedEvent` — typed domain events used as Kafka payloads.
- `KafkaTopics` — constants for topic names and consumer group ids.
- `BaseException` — custom runtime exception base (message, errorCode, httpStatus).


### api-gateway (Spring Cloud Gateway)
Purpose: Single ingress using Spring Cloud Gateway. Validates JWTs at edge, rate-limits, forwards identity headers to downstream.  
**Port**: 8080  

**Responsibilities**:
- Route incoming requests to appropriate microservices
- Validate JWT tokens before forwarding requests
- Add authenticated user context (userId, userRole) to downstream requests
- Centralize cross-cutting concerns (security, logging)

**Key Components**:
- `JwtAuthenticationFilter`: Custom reactive filter for JWT validation
- Gateway routes configured in `application.yml`

**Dependencies**: Spring Cloud Gateway, Spring Security, jjwt

Key files:
- `ApiGatewayApplication.java` — main.
- `application.yml` — route definitions, RequestRateLimiter args, JWT secret, Redis config.
- `JwtAuthenticationFilter` (Gateway filter)
  - Validates `Authorization: Bearer <token>`
  - On success: extracts claims and forwards `X-User-Id` and `X-User-Role` headers.
  - On failure: returns HTTP 401.


### user-service (User Authentication & Management)
Purpose: Authentication (register/login/refresh/logout), user CRUD, token blacklisting.  
Port: 8081

**Responsibilities**:
- User registration with password encoding (BCrypt)
- User login with JWT token generation (access + refresh tokens)
- Token refresh for expired access tokens
- Token logout (blacklisting via Redis)
- User profile retrieval with caching

**Key Components**:
- `UserServiceImpl`: Implements register, login, refreshToken, logout, getUserById
- `JwtService`: JWT token generation, validation, claims extraction, Redis blacklist management
- `User` entity, `UserRepository`, `UserController`
- Redis cache with prefix `user:` for user profiles
- Caching strategy: `@Cacheable`, `@CacheEvict`

**Database**: PostgreSQL `user_service_db`  
**Cache Backend**: Redis with key prefix `user:`  
**Kafka**: Publishes user events (optional)

**Key Configurations** (application.yml):
```yaml
server:
  port: 8081
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/user_service_db
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:password}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-key}
    access-token-expiry-ms: 900000  # 15 minutes
    refresh-token-expiry-ms: 604800000  # 7 days
```

Important classes:
- `User` (entity implementing `UserDetails`) — fields: id, username, email, passwordHash, role (Role enum), enabled, timestamps. `getAuthorities()` returns the role as `SimpleGrantedAuthority`.
- `UserRepository` — JPA repository with `findByEmail`, `findByUsername`, `existsBy*`, `findByEmailOrUsername()` used at login.
- `JwtService` — core JWT operations (generate tokens, validate, blacklist in Redis). See code snippet below.
- `UserService` (interface) and `UserServiceImpl` — register, login, refreshToken, logout, get/update/delete users. Important behaviors:
  - `register()` checks uniqueness, encodes password, persists user, issues access & refresh tokens.
  - `login()` authenticates via AuthenticationManager, issues tokens.
  - `refreshToken()` validates refresh token and issues a new access token.
  - `logout()` blacklists the JTI of access token in Redis.
- Controllers: `AuthController` (`/api/v1/auth`) and `UserController` (`/api/v1/users`).
- `GlobalExceptionHandler` maps `BaseException` and validation exceptions to `ApiResponse`.


### order-service (Order Lifecycle Management)
Purpose: Order lifecycle; persists orders; publishes `OrderPlacedEvent` to Kafka; consumes `PaymentProcessedEvent` to update order status and publish `OrderStatusChangedEvent`.   
Port: 8082

**Responsibilities**:
- Create orders with line items (`OrderItems`)
- Calculate order totals
- Retrieve order details by `orderId` or `userId`
- Cancel orders with status validation
- Publish `OrderPlacedEvent` to Kafka when orders are created
- Consume `PaymentProcessedEvent` and `OrderStatusChangedEvent` from Kafka

**Key Components**:
- `OrderServiceImpl`: Implements CRUD operations and event publishing
- `Order` and `OrderItem` entities
- `OrderRepository`, `OrderItemRepository`
- `OrderController` (REST endpoints)
- Kafka producer for `OrderPlacedEvent`
- Kafka consumer listeners for payment & status updates

**Database**:
PostgreSQL `order_service_db`

**Message Queue**:
Apache Kafka

**Key Kafka Topics**:
- Publishes: `order.placed` (when order created)
- Subscribes: `payment.processed`, `order.status.changed`

**Key Configurations** (application.yml):
```yaml
server:
  port: 8082
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/order_service_db
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: order-service-group
```

Important classes:
- `Order` (entity) — aggregate root: status (OrderStatus), items (OrderItem list), totals, paymentTransactionId, failureReason.
- `OrderItem` — product details and line total.
- `OrderRepository` — findByUserId, findByIdAndUserId, findAllOrderByCreatedAtDesc, countByStatus.
- `OrderServiceImpl` — business logic:
  - `createOrder(userId, userEmail, shippingAddress, currency, notes, items)` — compute totals, persist order (status PENDING), publish `OrderPlacedEvent`.
  - `getOrderById(orderId)` — `@Cacheable("orders")`.
  - `getOrdersByUser(userId)`, `getAllOrders()`.
  - `cancelOrder(orderId, userId)` — allowed only when status is PENDING or CONFIRMED; evicts cache.
- `OrderEventProducer` — Kafka producer using `KafkaTemplate` to send `OrderPlacedEvent` and `OrderStatusChangedEvent` with orderId as key.
- `PaymentEventConsumer` — Kafka listener for `payment.processed` that updates order status to `PAID` or `PAYMENT_FAILED` and publishes `OrderStatusChangedEvent`.


### payment-service (Payment Processing & Events)
Purpose: Consumes `order.placed`, simulates payment gateway, persists `Payment` records, publishes `payment.processed`.   
Port: 8083

**Responsibilities**:
- Consume `OrderPlacedEvent` from Kafka
- Implement idempotency check (prevent duplicate payment processing for same `orderId`)
- Simulate payment gateway processing (85% success rate)
- Persist Payment records (with `orderId`, amount, status, timestamp)
- Publish `PaymentProcessedEvent` on successful processing
- Handle payment failures gracefully

**Key Components**:
- **PaymentEventProcessor**: Kafka consumer listener for `OrderPlacedEvent`
- **PaymentService**: Business logic for payment processing
- **Payment entity and PaymentRepository**
- **PaymentController**: REST endpoints for payment queries
- Kafka producer for `PaymentProcessedEvent`

**Database**:
PostgreSQL `payment_service_db`

**Message Queue**:
Apache Kafka

**Idempotency**:
Checked via Payment table unique constraint on `orderId`

**Key Kafka Flow**:
1. Receives: `OrderPlacedEvent` (`orderId`, `userId`, `amount`, `items`)
2. Checks: Does Payment record exist for this `orderId`?
3. If new: Simulates gateway, creates Payment record, publishes `PaymentProcessedEvent`
4. If duplicate: Logs and skips (idempotent)

```yaml
server:
  port: 8083
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payment_service_db
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    consumer:
      group-id: payment-service-group
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

Important classes:
- `Payment` (entity) — transaction record: transactionId, amount, currency, status, gatewayResponse, processedAt.
- `PaymentRepository` — JPA repository with `findByOrderId`, `findByTransactionId`.
- `PaymentEventProcessor` — Kafka listener for `order.placed`:
  - Idempotency guard: skip processing if a Payment already exists for the orderId.
  - Simulate gateway latency and success/failure using configured `successRate`.
  - Persist `Payment` entity and publish `PaymentProcessedEvent`.
- `PaymentController` — read-only endpoints to query payments by order, transaction or user.


### notification-service
Purpose: Consumes `payment.processed` and `order.status.changed` to dispatch emails (simulated or real), and persists `NotificationLog` records.
Port: 8084

**Responsibilities**
- Consume `PaymentProcessedEvent` and send payment confirmation emails
- Consume `OrderStatusChangedEvent` and send status update emails
- Persist notification records in database
- Log successful/failed email dispatch attempts
- Integration with email provider (SMTP)

**Key Components**
- `NotificationEventConsumer`: Kafka consumer listeners
- `NotificationService`: Email dispatch business logic
- Notification entity and `NotificationRepository`
- `NotificationController` (REST endpoints)
- Email template engine (e.g., Thymeleaf)

**Database**:
**PostgreSQL**- `notification_service_db`

**Message Queue**:
Apache Kafka

**Email Configuration**:
SMTP configuration for outbound emails

**Key Kafka Subscriptions**:
**Subscribes to**-
- `payment.processed`
- `order.status.changed`

**Publishes**:
- None (only consumes)

```yml
server:
  port: 8084
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notification_service_db
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    consumer:
      group-id: notification-service-group
  mail:
    host: ${MAIL_HOST:smtp.mailtrap.io}
    port: ${MAIL_PORT:2525}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
```

Important classes:
- `EmailNotificationService` — sends `SimpleMailMessage` via `JavaMailSender` when enabled; otherwise logs simulated email.
- `NotificationEventConsumer` — Kafka listeners:
  - `onPaymentProcessed(PaymentProcessedEvent)` — builds subject/body (success vs failure) and calls `dispatchEmail`.
  - `onOrderStatusChanged(OrderStatusChangedEvent)` — builds status update email and calls `dispatchEmail`.
  - `dispatchEmail(...)` records `NotificationLog` with status `SENT` or `FAILED`.
- `NotificationLog` + `NotificationLogRepository` — audit logs for notifications.

---

## Index: classes + method signatures (selected, non-test files)

Note: For brevity, this index includes all key classes and their public method signatures (controllers, services, repositories, event processors). It is derived from the codebase.

### common-lib
- com.orderms.common.dto.ApiResponse<T>
  - static <T> ApiResponse<T> success(T data)
  - static <T> ApiResponse<T> success(String message, T data)
  - static <T> ApiResponse<T> error(String message, String errorCode)

- com.orderms.common.event.BaseEvent
  - fields: String eventId, String source, Instant occurredAt

- com.orderms.common.event.OrderPlacedEvent
  - fields: orderId, userId, userEmail, totalAmount, currency, List<OrderItemDto> items

- com.orderms.common.util.KafkaTopics
  - public static final String ORDER_PLACED, ORDER_STATUS_CHANGED, PAYMENT_PROCESSED, NOTIFICATION_SEND

- com.orderms.common.exception.BaseException
  - BaseException(String message, String errorCode, int httpStatus)


### api-gateway
- com.orderms.ApiGatewayApplication
  - public static void main(String[] args)
- com.orderms.config.JwtAuthenticationFilter extends AbstractGatewayFilterFactory
  - public GatewayFilter apply(Config config)
  - private Mono<Void> unauthorized(ServerWebExchange exchange)
  - private SecretKey getSigningKey()


### user-service
- com.orderms.UserServiceApplication
  - public static void main(String[] args)

- com.orderms.entity.User implements UserDetails
  - public Collection<? extends GrantedAuthority> getAuthorities()
  - public String getPassword()
  - public boolean isAccountNonExpired(), isCredentialsNonExpired()

- com.orderms.repository.UserRepository extends JpaRepository<User, String>
  - Optional<User> findByEmail(String email)
  - Optional<User> findByUsername(String username)
  - boolean existsByEmail(String email)
  - boolean existsByUsername(String username)
  - Optional<User> findByEmailOrUsername(String emailOrUsername)

- com.orderms.security.JwtService
  - public String generateAccessToken(UserDetails userDetails)
  - public String generateAccessToken(Map<String,Object> extraClaims, UserDetails userDetails)
  - public String generateRefreshToken(UserDetails userDetails)
  - public String extractUsername(String token)
  - public String extractTokenId(String token)
  - public <T> T extractClaim(String token, Function<Claims, T> resolver)
  - public boolean isTokenValid(String token, UserDetails userDetails)
  - public void blacklistToken(String token)
  - public boolean isBlacklisted(String token)

- com.orderms.service.UserService (interface)
  - AuthResponse register(RegisterRequest request)
  - AuthResponse login(LoginRequest request)
  - AuthResponse refreshToken(String refreshToken)
  - void logout(String accessToken)
  - UserResponse getUserById(String id)
  - UserResponse getUserByEmail(String email)
  - List<UserResponse> getAllUsers()
  - UserResponse updateUser(String id, RegisterRequest request)
  - void deleteUser(String id)

- com.orderms.service.impl.UserServiceImpl (implements UserService)
  - public AuthResponse register(RegisterRequest req)
  - public AuthResponse login(LoginRequest req)
  - public AuthResponse refreshToken(String refreshToken)
  - public void logout(String accessToken)
  - public UserResponse getUserById(String id)
  - public UserResponse getUserByEmail(String email)
  - public List<UserResponse> getAllUsers()
  - public UserResponse updateUser(String id, RegisterRequest req)
  - public void deleteUser(String id)

- com.orderms.controller.AuthController
  - POST /api/v1/auth/register -> public ResponseEntity<ApiResponse<AuthResponse>> register(RegisterRequest)
  - POST /api/v1/auth/login -> public ResponseEntity<ApiResponse<AuthResponse>> login(LoginRequest)
  - POST /api/v1/auth/refresh -> public ResponseEntity<ApiResponse<AuthResponse>> refresh(String refreshToken header)
  - POST /api/v1/auth/logout -> public ResponseEntity<ApiResponse<Void>> logout(String Authorization header)

- com.orderms.controller.UserController
  - GET /api/v1/users -> getAllUsers()
  - GET /api/v1/users/{id} -> getUserById(String id)
  - DELETE /api/v1/users/{id} -> deleteUser(String id)


### order-service
- com.orderms.OrderServiceApplication
  - public static void main(String[] args)

- com.orderms.entity.Order
  - fields and getters/setters for id, userId, userEmail, status (OrderStatus), totalAmount, currency, shippingAddress, notes, paymentTransactionId, failureReason, items, createdAt, updatedAt

- com.orderms.entity.OrderItem
  - fields: id, order, productId, productName, quantity, unitPrice, lineTotal

- com.orderms.entity.OrderStatus (enum)
  - PENDING, CONFIRMED, PAYMENT_PROCESSING, PAID, PAYMENT_FAILED, SHIPPED, DELIVERED, CANCELLED, REFUNDED

- com.orderms.repository.OrderRepository extends JpaRepository<Order, String>
  - List<Order> findByUserId(String userId)
  - List<Order> findByStatus(OrderStatus status)
  - List<Order> findByUserIdAndStatus(String userId, OrderStatus status)
  - Optional<Order> findByIdAndUserId(String id, String userId)
  - long countByStatus(OrderStatus status)
  - List<Order> findAllOrderByCreatedAtDesc()

- com.orderms.service.impl.OrderServiceImpl
  - public Order createOrder(String userId, String userEmail, String shippingAddress, String currency, String notes, List<OrderItemInput> items)
  - public Order getOrderById(String orderId)
  - public List<Order> getOrdersByUser(String userId)
  - public List<Order> getAllOrders()
  - public Order cancelOrder(String orderId, String userId)
  - public record OrderItemInput(String productId, String productName, int quantity, BigDecimal unitPrice)

- com.orderms.event.OrderEventProducer
  - public void publishOrderPlaced(OrderPlacedEvent event)
  - public void publishOrderStatusChanged(OrderStatusChangedEvent event)

- com.orderms.event.PaymentEventConsumer
  - @KafkaListener onPaymentProcessed(PaymentProcessedEvent event, int partition, long offset)

- com.orderms.controller.OrderController
  - POST /api/v1/orders -> createOrder(CreateOrderRequest, UserDetails principal)
  - GET /api/v1/orders -> getOrders(UserDetails principal)
  - GET /api/v1/orders/{id} -> getOrderById(String id)
  - PATCH /api/v1/orders/{id}/cancel -> cancelOrder(String id, UserDetails principal)
  - GET /api/v1/orders/admin/all -> adminGetAll()


### payment-service
- com.orderms.PaymentServiceApplication
  - public static void main(String[] args)

- com.orderms.entity.Payment
  - fields: id, orderId, userId, userEmail, transactionId, amount, currency, status (PaymentStatus), paymentMethod, failureReason, gatewayResponse, processedAt

- com.orderms.entity.PaymentStatus (enum)
  - SUCCESS, FAILED, REFUNDED, PENDING

- com.orderms.repository.PaymentRepository extends JpaRepository<Payment, String>
  - Optional<Payment> findByOrderId(String orderId)
  - List<Payment> findByUserId(String userId)
  - List<Payment> findByStatus(PaymentStatus status)
  - Optional<Payment> findByTransactionId(String transactionId)

- com.orderms.event.PaymentEventProcessor
  - @KafkaListener onOrderPlaced(OrderPlacedEvent event, int partition, long offset)
  - private void simulateProcessingDelay()

- com.orderms.controller.PaymentController
  - GET /api/v1/payments/order/{orderId}
  - GET /api/v1/payments/transaction/{transactionId}
  - GET /api/v1/payments/user/{userId}
  - GET /api/v1/payments (ADMIN only)


### notification-service
- com.orderms.NotificationServiceApplication
  - public static void main(String[] args)

- com.orderms.service.EmailNotificationService
  - public void sendEmail(String to, String subject, String body)

- com.orderms.event.NotificationEventConsumer
  - @KafkaListener onPaymentProcessed(PaymentProcessedEvent event, int partition)
  - @KafkaListener onOrderStatusChanged(OrderStatusChangedEvent event, int partition)
  - private void dispatchEmail(String userId, String email, String orderId, String type, String subject, String body)
  - private String buildPaymentEmailBody(PaymentProcessedEvent e)
  - private String buildOrderStatusEmailBody(OrderStatusChangedEvent e)

- com.orderms.entity.NotificationLog
  - fields: id, userId, userEmail, orderId, notificationType, channel, subject, body, status, errorMessage, sentAt

- com.orderms.repository.NotificationLogRepository extends JpaRepository<NotificationLog, String>
  - List<NotificationLog> findByUserId(String userId)
  - List<NotificationLog> findByOrderId(String orderId)
  - List<NotificationLog> findByStatus(String status)

---

## Key code snippets

### JwtService — token generation, validation, blacklisting

```java
public String generateAccessToken(UserDetails userDetails) {
    return generateAccessToken(new HashMap<>(), userDetails);
}

public String generateRefreshToken(UserDetails userDetails) {
    return buildToken(new HashMap<>(), userDetails, refreshTokenExpiryMs, "REFRESH");
}

private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails,
                           long expiryMs, String tokenType) {
    Date now = new Date(System.currentTimeMillis());
    Date expiry = new Date(System.currentTimeMillis() + expiryMs);
    Map<String,Object> claims = new HashMap<>(extraClaims);
    claims.put("type", tokenType);
    claims.put("authorities", userDetails.getAuthorities());
    return Jwts.builder()
            .claims(claims)
            .subject(userDetails.getUsername())
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey(), Jwts.SIG.HS256)
            .compact();
}

public void blacklistToken(String token) {
    String jti = extractTokenId(token);
    Date expiration = extractClaim(token, Claims::getExpiration);
    long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
    redisTemplate.opsForValue().set(blacklistPrefix + jti, "true", Duration.ofSeconds(ttlSeconds));
}
```

Notes: `isTokenValid` checks subject match, expiry and Redis blacklist.


### UserServiceImpl — register, login, refresh, logout (high-level)

```java
@Transactional
public AuthResponse register(RegisterRequest req) {
    if (userRepository.existsByEmail(req.getEmail())) throw new DuplicateResourceException(...);
    User user = User.builder()
        .username(req.getUsername())
        .email(req.getEmail())
        .passwordHash(passwordEncoder.encode(req.getPassword()))
        .role(Role.ROLE_USER)
        .build();

    user = userRepository.save(user);
    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);
    return buildAuthResponse(accessToken, refreshToken, user);
}

public AuthResponse login(LoginRequest req) {
    authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsernameOrEmail(), req.getPassword()));
    User user = userRepository.findByEmailOrUsername(req.getUsernameOrEmail()).orElseThrow(...);
    // generate tokens...
}

public AuthResponse refreshToken(String refreshToken) {
    String username = jwtService.extractUsername(refreshToken);
    User user = userRepository.findByUsername(username).orElseThrow(...);
    if (!jwtService.isTokenValid(refreshToken, user)) throw new IllegalArgumentException(...);
    String newAccessToken = jwtService.generateAccessToken(user);
    return buildAuthResponse(newAccessToken, refreshToken, user);
}

public void logout(String accessToken) {
    jwtService.blacklistToken(accessToken);
}
```

### OrderServiceImpl.createOrder

```java
@Transactional
public Order createOrder(String userId, String userEmail, String shippingAddress, String currency, String notes, List<OrderItemInput> items) {
    List<OrderItem> lineItems = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;

    Order order = Order.builder()
          .userId(userId)
          .userEmail(userEmail)
          .shippingAddress(shippingAddress)
          .currency(currency != null ? currency : "USD")
          .status(OrderStatus.PENDING)
          .items(lineItems)
          .build();

    for (OrderItemInput i : items) {
        BigDecimal lineTotal = i.unitPrice().multiply(BigDecimal.valueOf(i.quantity()));
        total = total.add(lineTotal);
        lineItems.add(OrderItem.builder() ... .build());
    }

    order.setTotalAmount(total);
    Order saved = orderRepository.save(order);
    OrderPlacedEvent event = OrderPlacedEvent.builder() ... .build();
    orderEventProducer.publishOrderPlaced(event);
    return saved;
}
```

### PaymentEventProcessor.onOrderPlaced

```java
@KafkaListener(topics = KafkaTopics.ORDER_PLACED, groupId = KafkaTopics.GROUP_PAYMENT_SERVICE)
@Transactional
public void onOrderPlaced(@Payload OrderPlacedEvent event, @Header(...) int partition, @Header(...) long offset) {
    if (paymentRepository.findByOrderId(event.getOrderId()).isPresent()) return; // idempotency
    simulateProcessingDelay();
    boolean success = random.nextDouble() < successRate;
    PaymentStatus status = success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
    Payment payment = Payment.builder().orderId(event.getOrderId()).amount(event.getTotalAmount())...build();
    paymentRepository.save(payment);
    PaymentProcessedEvent result = PaymentProcessedEvent.builder()...build();
    kafkaTemplate.send(KafkaTopics.PAYMENT_PROCESSED, event.getOrderId(), result);
}
```

### NotificationEventConsumer handlers

```java
@KafkaListener(topics = KafkaTopics.PAYMENT_PROCESSED, groupId = KafkaTopics.GROUP_NOTIFICATION_SERVICE)
public void onPaymentProcessed(PaymentProcessedEvent event, @Header(...) int partition) {
    String type = "SUCCESS".equalsIgnoreCase(event.getPaymentStatus()) ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
    String subject = ...;
    String body = buildPaymentEmailBody(event);
    dispatchEmail(event.getUserId(), event.getUserEmail(), event.getOrderId(), type, subject, body);
}

@KafkaListener(topics = KafkaTopics.ORDER_STATUS_CHANGED, groupId = KafkaTopics.GROUP_NOTIFICATION_SERVICE)
public void onOrderStatusChanged(OrderStatusChangedEvent event, @Header(...) int partition) {
    String subject = "Order " + event.getOrderId() + " status updated to " + event.getNewStatus();
    String body = buildOrderStatusEmailBody(event);
    dispatchEmail(...);
}
```

### Api Gateway JwtAuthenticationFilter (main behavior)

```java
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return unauthorized(exchange);
        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
            ServerWebExchange mutated = exchange.mutate().request(r -> r.header("X-User-Id", claims.getSubject()).header("X-User-Role", String.valueOf(claims.get("authorities")))).build();
            return chain.filter(mutated);
        } catch (Exception ex) {
            return unauthorized(exchange);
        }
    };
}
```

---

## Notes & next steps

- The guide includes key classes and method signatures; if a full method-by-method literal dump is required (every private method and all parameters), request a deeper export and specify format (CSV, JSON, or extended markdown sections per Java file).
- To keep this repo tidy, the guide is saved as `DETAILED_GUIDE.md` at project root.

---

Generated from repository files (excluding tests) on 2026-05-29.
