# syntax=docker/dockerfile:1.7
# =============================================================================
# Phase 187 — Production deployment baseline
#
# Multi-stage build for the Telegram Engineering Operations Platform.
#
#   Build stage:    JDK 21 + repo Maven wrapper (./mvnw) — fully reproducible
#                   without a host Maven install.
#   Runtime stage:  JRE 21 only, non-root user, curl available for the
#                   container HEALTHCHECK against /actuator/health.
#
# No code is mutated; the image only packages the already-tested Spring Boot
# fat jar produced by `./mvnw -DskipTests package`. Tests run on the host or
# CI before image build per CLAUDE.md "production-grade, no shortcuts" rule.
# =============================================================================

# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy Maven wrapper + descriptor first so dependency resolution layers can
# be cached independently of source changes.
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml pom.xml
RUN chmod +x mvnw \
    && ./mvnw -B -ntp -DskipTests dependency:go-offline || true

# Copy sources and build the executable Spring Boot jar.
COPY src src
RUN ./mvnw -B -ntp -DskipTests package \
    && cp target/platform-*.jar target/app.jar

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# curl is the smallest dependency that lets compose / `docker inspect` poll
# the Spring Boot actuator health endpoint as a real container HEALTHCHECK.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Run as a dedicated non-root user. Fixed UID/GID makes host bind-mount
# permissions predictable even though the runtime image does not expect any.
RUN groupadd --system --gid 1000 engops \
    && useradd  --system --uid 1000 --gid engops --home-dir /app \
                --shell /sbin/nologin engops

COPY --from=build --chown=engops:engops /workspace/target/app.jar /app/app.jar

USER engops
EXPOSE 8080

# Operator-tunable JVM flags. Default chosen to behave well in container
# memory limits without forcing the operator to set anything.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0"

# Healthcheck honours the Phase 146 actuator posture: /actuator/health is
# `permitAll` (Kubernetes/load-balancer style probe), other actuator paths
# require auth.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

# `sh -c` is required so the JAVA_OPTS expansion happens at container start
# (not at image build time), allowing operator JVM tuning via the env var.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
