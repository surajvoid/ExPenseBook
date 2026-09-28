# =========================================================
# Multi-Stage Production Dockerfile for ExPense Book
# =========================================================

# Stage 1: Build using Maven and Temurin JDK 21
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy full source and build standalone executable uber-jar
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

# Environment defaults
ENV PORT=8080
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
