ARG SERVICE_NAME=user-service
ARG SERVICE_PORT=8081

FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache parent pom + common-lib dependencies
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/

RUN mvn dependency:go-offline -pl common-lib,${SERVICE_NAME} -am -B

# Build
COPY common-lib/src common-lib/src
COPY ${SERVICE_NAME}/src ${SERVICE_NAME}/src
RUN mvn clean package -pl common-lib,${SERVICE_NAME} -am -DskipTests -B

# Runtime
FROM eclipse-temurin:21-jre-jammy
ARG SERVICE_NAME=user-service
ARG SERVICE_PORT=8081
WORKDIR /app

RUN groupadd --gid 1001 appgroup && \
    useradd  --uid 1001 --gid appgroup --shell /bin/sh appuser

COPY --from=build /workspace/${SERVICE_NAME}/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
