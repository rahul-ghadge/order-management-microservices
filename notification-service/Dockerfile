# =============================================================
#  notification-service/Dockerfile
#  Build context: repository ROOT  (docker-compose: context: .)
# =============================================================

FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml                      ./pom.xml
COPY common-lib/pom.xml           ./common-lib/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
COPY api-gateway/pom.xml          ./api-gateway/pom.xml
COPY user-service/pom.xml         ./user-service/pom.xml
COPY order-service/pom.xml        ./order-service/pom.xml
COPY payment-service/pom.xml      ./payment-service/pom.xml

RUN mvn dependency:go-offline \
      -pl common-lib,notification-service -am \
      --no-transfer-progress -B

COPY common-lib/src           ./common-lib/src
COPY notification-service/src ./notification-service/src

RUN mvn clean package \
      -pl common-lib,notification-service -am \
      -DskipTests --no-transfer-progress -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/notification-service/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8084

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
