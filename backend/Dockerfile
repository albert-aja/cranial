# =============================================================================
# Stage 1: Build CRANIAL Spring Boot Backend
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

# =============================================================================
# Stage 2: Minimal Non-Root Runtime Image
# =============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng tesseract-ocr-data-ind fontconfig ttf-dejavu \
    && addgroup -S cranial && adduser -S cranial -G cranial
USER cranial:cranial

COPY --from=builder /workspace/target/*.jar /app/cranial-backend.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=4 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/cranial-backend.jar"]

