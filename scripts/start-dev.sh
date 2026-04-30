#!/usr/bin/env bash
# =============================================================
#  start-dev.sh  –  Build and start the full OMS stack
# =============================================================
set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Order Management System – Dev Start   ${NC}"
echo -e "${GREEN}========================================${NC}"

# ── 1. Build all Maven modules ────────────────────────────────
echo -e "${YELLOW}[1/3] Building Maven modules...${NC}"
mvn clean install -DskipTests -B

# ── 2. Start infrastructure first ────────────────────────────
echo -e "${YELLOW}[2/3] Starting infrastructure (Kafka, Redis, Postgres)...${NC}"
docker-compose up -d postgres-user postgres-order postgres-payment postgres-notification \
                     redis zookeeper kafka kafka-ui

echo "Waiting 20s for infrastructure to be ready..."
sleep 20

# ── 3. Start application services + monitoring ───────────────
echo -e "${YELLOW}[3/3] Starting application services & monitoring...${NC}"
docker-compose up -d api-gateway user-service order-service payment-service \
                     notification-service prometheus grafana

echo -e "${GREEN}"
echo "============================================================"
echo "  All services started!                                     "
echo "============================================================"
echo "  API Gateway   : http://localhost:8080                     "
echo "  User Service  : http://localhost:8081/swagger-ui.html     "
echo "  Order Service : http://localhost:8082/swagger-ui.html     "
echo "  Payment Svc   : http://localhost:8083/swagger-ui.html     "
echo "  Notif. Svc    : http://localhost:8084/swagger-ui.html     "
echo "  Kafka UI      : http://localhost:8090                     "
echo "  Prometheus    : http://localhost:9090                     "
echo "  Grafana       : http://localhost:3000  (admin/admin)      "
echo "============================================================"
echo -e "${NC}"
