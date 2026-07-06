# ============================================================
# Stage 1: Build — full JDK + Maven, compiles the fat JAR
# ============================================================
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper + config first (layer-cached until pom.xml changes)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and package (only re-runs when source changes)
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ============================================================
# Stage 2: Runtime — slim JRE only, runs the JAR
# ============================================================
FROM eclipse-temurin:25-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Own the working directory
RUN chown -R appuser:appgroup /app
USER appuser

# Container-aware JVM tuning
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
