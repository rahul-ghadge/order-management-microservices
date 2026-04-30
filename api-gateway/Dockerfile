# =============================================================
#  api-gateway/Dockerfile
#  Build context: repository ROOT  (docker-compose: context: .)
#  Stage 1 – Maven build (only api-gateway + common-lib)
#  Stage 2 – Minimal JRE runtime image
# =============================================================

# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Copy only the POMs first so Docker layer-caches the dependency download
COPY pom.xml                      ./pom.xml
COPY common-lib/pom.xml           ./common-lib/pom.xml
COPY api-gateway/pom.xml          ./api-gateway/pom.xml
# Stub sibling POMs so the parent reactor resolves without their source
COPY user-service/pom.xml         ./user-service/pom.xml
COPY order-service/pom.xml        ./order-service/pom.xml
COPY payment-service/pom.xml      ./payment-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml

# Download dependencies (cached unless a POM changes)
RUN mvn dependency:go-offline \
      -pl common-lib,api-gateway -am \
      --no-transfer-progress -B

# Copy source for the modules we actually build
COPY common-lib/src   ./common-lib/src
COPY api-gateway/src  ./api-gateway/src

# Build — skip tests (tests run in CI, not in Docker image build)
RUN mvn clean package \
      -pl common-lib,api-gateway -am \
      -DskipTests --no-transfer-progress -B

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/api-gateway/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
