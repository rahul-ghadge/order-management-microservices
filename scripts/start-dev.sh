#!/usr/bin/env bash
# =============================================================
#  scripts/start-dev.sh
#  One-command launcher for the full Order Management System.
#  Run from the repository ROOT:  ./scripts/start-dev.sh
# =============================================================
set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Resolve script location so it works regardless of cwd
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

echo -e "${GREEN}"
echo "=============================================================="
echo "  Order Management System – Development Stack Launcher        "
echo "=============================================================="
echo -e "${NC}"

# ── Prerequisites check ───────────────────────────────────────
echo -e "${YELLOW}[0/3] Checking prerequisites...${NC}"
command -v docker         >/dev/null 2>&1 || { echo -e "${RED}ERROR: docker not found${NC}"; exit 1; }
command -v docker compose >/dev/null 2>&1 \
  || command -v docker-compose >/dev/null 2>&1 \
  || { echo -e "${RED}ERROR: docker compose not found${NC}"; exit 1; }
echo "  docker   : $(docker --version)"
echo "  compose  : $(docker compose version 2>/dev/null || docker-compose --version)"

# ── Infrastructure ────────────────────────────────────────────
echo -e "${YELLOW}[1/3] Starting infrastructure (PostgreSQL × 4, Redis, Zookeeper, Kafka, Kafka-UI)...${NC}"
docker compose up -d \
  postgres-user postgres-order postgres-payment postgres-notification \
  redis zookeeper kafka kafka-ui

echo -n "  Waiting for Kafka to be ready"
for i in $(seq 1 30); do
  if docker compose exec -T kafka \
       kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; then
    echo " ✓"
    break
  fi
  echo -n "."
  sleep 3
  if [ "$i" -eq 30 ]; then
    echo ""
    echo -e "${RED}WARNING: Kafka did not become ready in 90 s – continuing anyway${NC}"
  fi
done

# ── Application services ──────────────────────────────────────
# NOTE: Each Dockerfile runs its own Maven build inside the container
# (multi-stage build from root context). No local mvn required.
echo -e "${YELLOW}[2/3] Building & starting application services (this may take a few minutes on first run)...${NC}"
docker compose up -d --build \
  api-gateway user-service order-service payment-service notification-service

# ── Monitoring ────────────────────────────────────────────────
echo -e "${YELLOW}[3/3] Starting monitoring (Prometheus + Grafana)...${NC}"
docker compose up -d prometheus grafana

# ── Summary ───────────────────────────────────────────────────
echo -e "${GREEN}"
echo "=============================================================="
echo "  All services started!                                       "
echo "=============================================================="
echo "  API Gateway       : http://localhost:8080                   "
echo "  User Service      : http://localhost:8081/swagger-ui.html   "
echo "  Order Service     : http://localhost:8082/swagger-ui.html   "
echo "  Payment Service   : http://localhost:8083/swagger-ui.html   "
echo "  Notification Svc  : http://localhost:8084/swagger-ui.html   "
echo "  Kafka UI          : http://localhost:8090                   "
echo "  Prometheus        : http://localhost:9090                   "
echo "  Grafana           : http://localhost:3000  (admin/admin)    "
echo "=============================================================="
echo ""
echo "  To tail all logs:  docker compose logs -f"
echo "  To stop all:       docker compose down"
echo "  To stop + wipe DB: docker compose down -v"
echo -e "${NC}"
