# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies separately from source so code changes don't
# force a full re-download every rebuild.
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user - standard hardening for a production image.
RUN addgroup -S karmaq && adduser -S karmaq -G karmaq
COPY --from=build /app/target/*.jar app.jar
USER karmaq

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
