# =========================================================
# Multi-Stage Production Dockerfile for ExPense Book
# =========================================================

# Stage 1: Build using Maven and Temurin JDK 21
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy full source and build standalone executable uber-jar
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Ultra-lightweight production JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user with full permissions on /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app && \
    chmod -R 775 /app

# Copy shaded standalone jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/expensebook-1.0.0.jar app.jar

USER appuser

# Environment defaults for Production Cloud Platforms (Railway, Koyeb, Render, Docker)
ENV PORT=8080
ENV APP_ENV=production
ENV MONGODB_DATABASE=expensebook
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
