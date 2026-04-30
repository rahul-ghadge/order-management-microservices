# 🏗️ Spring Boot Order Management System — Microservices Architecture

> **Senior / Architect-level portfolio project** demonstrating production-ready microservices
> with event-driven communication, distributed security, observability, and containerisation.

---

## 📐 Architecture Overview

```
                        ┌──────────────────────────────────────────┐
                        │             CLIENT (Browser / Mobile)    │
                        └───────────────────┬──────────────────────┘
                                            │ HTTPS
                        ┌───────────────────▼──────────────────────┐
                        │           API GATEWAY  :8080             │
                        │  JWT validation · Rate limiting · CORS   │
                        │  Spring Cloud Gateway + Redis            │
                        └──┬──────────┬──────────┬──────────┬──────┘
                           │          │          │          │
               ┌───────────▼──┐ ┌────▼──────┐ ┌▼────────┐ ┌▼──────────────┐
               │ user-service │ │order-svc  │ │pay-svc  │ │notif-svc      │
               │    :8081     │ │  :8082    │ │ :8083   │ │    :8084      │
               │ JWT issuance │ │ Order     │ │Payment  │ │Email dispatch │
               │ Redis cache  │ │ Kafka pub │ │Kafka pub│ │Kafka consumer │
               └──────┬───────┘ └────┬──────┘ └──┬──────┘ └───────────────┘
                      │              │            │
               ┌──────▼──────┐ ┌────▼──────┐ ┌──▼────────┐
               │ PostgreSQL  │ │PostgreSQL │ │PostgreSQL │
               │  user_db    │ │ order_db  │ │payment_db │
               └─────────────┘ └─────┬─────┘ └───────────┘
                                     │
                        ┌────────────▼───────────────────┐
                        │         Apache Kafka           │
                        │  order.placed                  │
                        │  payment.processed             │
                        │  order.status.changed          │
                        └────────────────────────────────┘

                        ┌──────────────┐   ┌─────────────┐
                        │  Prometheus  │   │   Grafana   │
                        │    :9090     │──▶│    :3000    │
                        └──────────────┘   └─────────────┘
```

---

## ✨ Key Features

| Category | Feature |
|---|---|
| **Architecture** | Event-driven microservices, Domain-Driven Design, clean package structure |
| **Security** | Stateless JWT (access + refresh tokens), token blacklisting via Redis, RBAC (`ROLE_USER`, `ROLE_ADMIN`), Spring Security 6 |
| **Messaging** | Apache Kafka with idempotent producer, consumer retry (3× with back-off), DLT-ready |
| **Caching** | Redis for JWT blacklisting, user profiles (`@Cacheable`), order lookups, API Gateway rate limiting |
| **Persistence** | PostgreSQL per service (database-per-service pattern), JPA/Hibernate, UUID primary keys |
| **API** | RESTful APIs, Swagger/OpenAPI 3 on every service, standardised `ApiResponse<T>` envelope |
| **Observability** | Micrometer + Prometheus + Grafana dashboards, Spring Boot Actuator on all services |
| **Containerisation** | Docker multi-stage builds (non-root user), Docker Compose for full local stack |
| **Testing** | Unit tests (`@MockitoExtension`), controller tests (`@WebMvcTest`), idempotency guards |
| **Code Quality** | Lombok, MapStruct, `@Builder`, interface+impl pattern, constants classes, `@Transactional` |

---

## 📦 Module Structure

```
spring-boot-order-management-microservices/
│
├── common-lib/                    # Shared library (NOT a Spring Boot app)
│   └── src/main/java/com/orderms/common/
│       ├── dto/ApiResponse.java               # Generic response envelope
│       ├── event/BaseEvent.java               # Kafka event base class
│       ├── event/OrderPlacedEvent.java        # order-service → payment-service
│       ├── event/PaymentProcessedEvent.java   # payment-service → order + notification
│       ├── event/OrderStatusChangedEvent.java # order-service → notification-service
│       ├── exception/BaseException.java
│       └── util/KafkaTopics.java              # Topic + group ID constants
│
├── api-gateway/    :8080          # Spring Cloud Gateway – JWT validation at edge
├── user-service/   :8081          # Auth, JWT, user CRUD, Redis blacklist
├── order-service/  :8082          # Order lifecycle, Kafka producer + consumer
├── payment-service/:8083          # Payment processing, Kafka consumer + producer
├── notification-service/:8084     # Email dispatch, Kafka consumer
│
├── infra/
│   ├── prometheus/prometheus.yml  # Scrape config for all 5 services
│   └── grafana/provisioning/      # Auto-provisioned Prometheus datasource
│
├── scripts/
│   ├── init-databases.sql         # Creates all 4 PostgreSQL databases
│   └── start-dev.sh               # One-command dev stack launcher
│
├── docker-compose.yml             # Full stack: 5 services + 4 DBs + Kafka + Redis + monitoring
├── Dockerfile                     # Multi-stage build template
├── pom.xml                        # Maven multi-module parent POM
└── README.md
```

---

## ⚡ Kafka Event Flow

```
User places order
    │
    ▼
order-service  ──[order.placed]──────────────────▶  payment-service
                                                         │
                                                         │  Simulates payment (85% success)
                                                         │
                                          ┌──────────────▼──────────────────┐
                                          │      [payment.processed]        │
                                          └──────────┬──────────────────────┘
                                                     │
                              ┌──────────────────────┼────────────────────────┐
                              │                      │                        │
                              ▼                      ▼                        ▼
                        order-service         order-service           notification-service
                     (Updates status:      [order.status.changed]     (Sends payment
                      PAID or FAILED)             │                    success/failure
                              │                   │                        email)
                              │                   ▼
                              │          notification-service
                              └────────▶  (Sends order status
                                           update email)
```

---

## 🔐 Security Architecture

### JWT Token Flow

```
1. POST /api/v1/auth/register  OR  POST /api/v1/auth/login
        │
        └──▶ user-service validates credentials
                │
                └──▶ Issues: access_token (15 min) + refresh_token (7 days)

2. Every protected request:
   Client sends: Authorization: Bearer <access_token>
        │
        └──▶ API Gateway validates JWT at edge
                │
                └──▶ Forwards X-User-Id + X-User-Role headers to downstream

3. POST /api/v1/auth/logout
        │
        └──▶ JTI (JWT ID) stored in Redis with TTL = remaining token lifetime
                └──▶ Subsequent requests with this token are rejected (blacklisted)

4. POST /api/v1/auth/refresh
        │
        └──▶ Valid refresh token → new access token (refresh token reused)
```

### RBAC

| Role | Permissions |
|---|---|
| `ROLE_USER` | Place orders, view own orders/payments, manage own profile |
| `ROLE_ADMIN` | All of the above + list all users, all orders, all payments |
| `ROLE_MANAGER` | Intermediate access (extensible) |

---

## 🚀 Quick Start

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Java | 21 |
| Maven | 3.9 |
| Docker + Docker Compose | 24 |

### One-command startup

```bash
git clone https://github.com/rahul-ghadge/spring-boot-order-management-microservices.git
cd spring-boot-order-management-microservices
chmod +x scripts/start-dev.sh
./scripts/start-dev.sh
```

### Manual startup

```bash
# 1. Build
mvn clean install -DskipTests

# 2. Start infrastructure
docker-compose up -d postgres-user postgres-order postgres-payment postgres-notification \
                     redis zookeeper kafka kafka-ui

# 3. Wait for infra, then start services
sleep 20
docker-compose up -d user-service order-service payment-service notification-service api-gateway

# 4. Start monitoring
docker-compose up -d prometheus grafana
```

---

## 🌐 Service URLs

| Service | URL | Description |
|---|---|---|
| API Gateway | http://localhost:8080 | Single entry point |
| User Service | http://localhost:8081/swagger-ui.html | Auth + user management |
| Order Service | http://localhost:8082/swagger-ui.html | Order lifecycle |
| Payment Service | http://localhost:8083/swagger-ui.html | Payment records |
| Notification Service | http://localhost:8084/swagger-ui.html | Notification logs |
| Kafka UI | http://localhost:8090 | Topic / message browser |
| Prometheus | http://localhost:9090 | Metrics scraping |
| Grafana | http://localhost:3000 | Dashboards (admin/admin) |

---

## 📋 API Reference

### Authentication

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username":  "rahulghadage",
    "email":     "rahul@example.com",
    "password":  "stringP@ssw0rd",
    "firstName": "Rahul",
    "lastName":  "ghadage"
  }'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "usernameOrEmail": "john@example.com", "password": "Password1" }'

# Save the token
TOKEN="<access_token_from_response>"

# Logout (blacklists token in Redis)
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

### Place an Order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userEmail":       "rahul@example.com",
    "shippingAddress": "123 Main Street, New York, NY 10001",
    "currency":        "USD",
    "items": [
      { "productId": "PROD-001", "productName": "Laptop Pro",  "quantity": 1, "unitPrice": 1299.99 },
      { "productId": "PROD-002", "productName": "USB-C Hub",   "quantity": 2, "unitPrice":  49.99  }
    ]
  }'
```

After placing the order, watch the Kafka event chain:
1. `order.placed` → payment-service processes payment (85% success rate)
2. `payment.processed` → order-service updates status; notification-service sends email
3. `order.status.changed` → notification-service sends status email

```bash
# Check order status (cached in Redis)
curl http://localhost:8080/api/v1/orders/<order-id> \
  -H "Authorization: Bearer $TOKEN"

# Check payment result
curl http://localhost:8080/api/v1/payments/order/<order-id> \
  -H "Authorization: Bearer $TOKEN"

# Check notification logs
curl http://localhost:8080/api/v1/notifications/order/<order-id> \
  -H "Authorization: Bearer $TOKEN"
```

### Cancel an Order (only PENDING or CONFIRMED)

```bash
curl -X PATCH http://localhost:8080/api/v1/orders/<order-id>/cancel \
  -H "Authorization: Bearer $TOKEN"
```

---

## 🗃️ Database Schema

Each service owns its own PostgreSQL database (Database-per-Service pattern):

| Service | Database | Key Tables |
|---|---|---|
| user-service | `user_db` | `users` |
| order-service | `order_db` | `orders`, `order_items` |
| payment-service | `payment_db` | `payments` |
| notification-service | `notification_db` | `notification_logs` |

---

## 📊 Observability

### Health Checks

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

### Prometheus Metrics

All services expose metrics at `/actuator/prometheus` — automatically scraped by Prometheus.

**Key metrics to monitor:**
- `http_server_requests_seconds` — request latency per endpoint
- `kafka_consumer_fetch_manager_records_consumed_total` — event throughput
- `jvm_memory_used_bytes` — heap usage per service
- `hikaricp_connections_active` — DB connection pool saturation
- `spring_cache_gets_total` — Redis cache hit/miss ratio

### Grafana

Navigate to http://localhost:3000, login with `admin/admin`.
The Prometheus datasource is auto-provisioned. Import dashboard ID **4701** (JVM Micrometer)
or **11378** (Spring Boot Statistics) from grafana.com/dashboards.

---

## 🧪 Running Tests

```bash
# All tests
mvn test

# Specific module
mvn test -pl user-service
mvn test -pl order-service
mvn test -pl payment-service
mvn test -pl notification-service

# Test coverage report
mvn test jacoco:report
```

### Test Inventory

| Module | Tests | Coverage |
|---|---|---|
| user-service | `UserServiceImplTest` (8 tests), `AuthControllerTest` (7 tests) | Auth, CRUD, validation |
| order-service | `OrderServiceImplTest` (5 tests), `OrderControllerTest` (7 tests) | Lifecycle, RBAC, cancel guards |
| payment-service | `PaymentEventProcessorTest` (4 tests) | Idempotency, success/failure, Kafka publish |
| notification-service | `NotificationEventConsumerTest` (5 tests) | Email dispatch, failure handling, NPE guards |

---

## ⚙️ Configuration Reference

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | (long default) | HS256 signing secret – **change in production** |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_USERNAME` | `postgres` | PostgreSQL username |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `NOTIFICATION_EMAIL_ENABLED` | `false` | Set `true` to send real emails |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP server host |
| `MAIL_USERNAME` | — | SMTP username |
| `MAIL_PASSWORD` | — | SMTP password |

---

## 🏛️ Architecture Decisions (ADR Summary)

| Decision | Choice | Rationale |
|---|---|---|
| Inter-service communication | Kafka (async) | Decouples services; payment/notification don't block order creation |
| Synchronous API | REST/JSON | Gateway routing + Swagger; simpler than gRPC for CRUD |
| Auth token storage | Stateless JWT + Redis blacklist | Horizontal scaling without shared session; instant logout |
| Database strategy | Database-per-service (separate PostgreSQL) | Independent schema evolution; blast radius containment |
| Caching | Redis | Shared infrastructure; supports rate limiting + token blacklist + entity cache |
| Idempotency | JTI-based blacklist + orderId guard in payment | Prevents double-processing on consumer retry |
| Secret management | Environment variables | 12-Factor App compliance; Kubernetes-compatible |


---

## 📝 License

Apache 2.0 — see [LICENSE](LICENSE).




<img width="1016" height="384" alt="image" src="https://github.com/user-attachments/assets/fe935658-a8cb-405a-bb89-919e3ceffd7a" />


<img width="1620" height="568" alt="image" src="https://github.com/user-attachments/assets/cd9c2ab4-b3a7-4b9c-aa36-984a731cd20b" />


<img width="1564" height="967" alt="image" src="https://github.com/user-attachments/assets/5d8a9093-bf4a-46fa-9fbf-dd2c9c9104b7" />

<img width="1576" height="592" alt="image" src="https://github.com/user-attachments/assets/135694ad-ed57-4746-a2e5-9863b38cb2d4" />

