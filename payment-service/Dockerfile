FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 1001 appgroup && useradd --uid 1001 --gid appgroup appuser
COPY target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8083
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
