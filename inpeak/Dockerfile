# Multi-stage build for better optimization
FROM amazoncorretto:21-alpine3.18 AS builder

WORKDIR /app

# Copy gradle files and wrapper first for better caching
COPY gradle/ gradle/
COPY gradlew ./
COPY build.gradle ./
COPY settings.gradle ./

# Download dependencies (this layer will be cached)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM amazoncorretto:21-alpine3.18

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built jar from builder stage
COPY --from=builder /app/build/libs/inpeak-backend.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# Use exec form for better signal handling
CMD ["java", "-jar", "app.jar"]
