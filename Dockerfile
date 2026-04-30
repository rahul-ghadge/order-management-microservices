# =============================================================
#  ROOT Dockerfile – NOT used directly.
#
#  Each microservice has its own Dockerfile:
#
#    api-gateway/Dockerfile           → port 8080
#    user-service/Dockerfile          → port 8081
#    order-service/Dockerfile         → port 8082
#    payment-service/Dockerfile       → port 8083
#    notification-service/Dockerfile  → port 8084
#
#  All Dockerfiles:
#    - Use BUILD CONTEXT = repository root  (set by docker-compose.yml)
#    - Run a multi-stage Maven build (stage 1) so no local Maven needed
#    - Produce a minimal eclipse-temurin:21-jre-alpine runtime image
#
#  To build and start everything:
#    ./scripts/start-dev.sh
#       OR
#    docker compose up --build
# =============================================================
