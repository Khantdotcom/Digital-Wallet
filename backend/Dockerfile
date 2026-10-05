FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/wallet-api-0.0.1.jar app.jar
EXPOSE 8080
# Free/Hobby boxes are small; keep heap modest unless JAVA_OPTS overrides it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60.0 -XX:+UseContainerSupport"
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD curl -sf http://localhost:${PORT:-8080}/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
