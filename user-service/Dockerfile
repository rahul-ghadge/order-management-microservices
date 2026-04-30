ARG SERVICE_NAME=user-service
ARG SERVICE_PORT=8081
# Inherits from root Dockerfile via docker-compose build context
FROM spring-boot-order-management-microservices:latest 2>/dev/null || \
     eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --gid 1001 appgroup 2>/dev/null; useradd --uid 1001 --gid appgroup appuser 2>/dev/null; true
EXPOSE 8081
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
