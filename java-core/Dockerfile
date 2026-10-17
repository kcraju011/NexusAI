# Build the application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src
COPY test ./test

RUN mvn --batch-mode --no-transfer-progress clean package -DskipTests

# Run using a smaller JRE image
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home --uid 10001 nexus \
    && mkdir -p /app/logs \
    && chown -R nexus:nexus /app

COPY --from=build /build/target/nexusai.jar /app/nexusai.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/nexusai.jar"]
