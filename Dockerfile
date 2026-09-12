# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# 1) Copy only pom.xml first to leverage Docker layer caching.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 2) Then copy sources and build.
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------- Runtime stage (pinned for reproducible builds) ----------
FROM eclipse-temurin:24.0.2_12-jre

# Install Tesseract + Persian + English language data.
# --no-install-recommends keeps the image small.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-fas \
        tesseract-ocr-eng \
        wget && \
    rm -rf /var/lib/apt/lists/* && \
    tesseract --list-langs

ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata \
    PORT=8080 \
    SPRING_DATASOURCE_URL="jdbc:h2:file:/data/receiptsdb;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE" \
    H2_CONSOLE_ENABLED=false \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

# Persistent H2 directory + non-root user (security best practice).
RUN mkdir -p /data /tmp/ocr && \
    groupadd -r appuser && useradd -r -g appuser appuser && \
    chown -R appuser:appuser /app /data /tmp/ocr

COPY --from=build /app/target/receiptvision-core.jar app.jar
RUN chown appuser:appuser /app/app.jar

USER appuser

VOLUME ["/data"]
EXPOSE 8080

# Actuator-based health check (needs spring-boot-starter-actuator).
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
