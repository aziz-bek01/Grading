# syntax=docker/dockerfile:1.7
# grading-api — Spring Boot 3.x / Java 21 modular monolith.
# Multi-stage: builder (full JDK + Maven) → runtime (slim JRE, non-root).
# No secrets baked in. No `latest` tag. Runtime config via env only.
# See infra/README.md for build / promotion rules.

# -----------------------------------------------------------------------------
# Stage 1 — Builder
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21.0.5_11-jdk-jammy AS builder

WORKDIR /workspace

# Copy wrapper + pom first so dependency resolution can be cached
# independently of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw \
 && ./mvnw -B -ntp dependency:go-offline

# Now copy sources and build the executable jar.
# Tests are run in CI (see .github/workflows/ci.yml) — image build skips them
# so that a failing test never produces an image, and image builds stay fast.
COPY src/ src/
RUN ./mvnw -B -ntp clean package -DskipTests \
 && cp target/grading-*.jar /workspace/app.jar \
 && java -Djarmode=tools -jar /workspace/app.jar extract --layers --destination /workspace/extracted || true

# -----------------------------------------------------------------------------
# Stage 2 — Runtime (slim JRE, non-root, read-only-fs friendly)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21.0.5_11-jre-jammy AS runtime

# OCI image labels — populated by buildx --label / GitHub Actions metadata.
ARG IMAGE_VERSION=dev
ARG IMAGE_REVISION=unknown
ARG IMAGE_CREATED=unknown
LABEL org.opencontainers.image.title="grading-api" \
      org.opencontainers.image.description="grading.hrlab.uz Spring Boot API" \
      org.opencontainers.image.vendor="HR Laboratories" \
      org.opencontainers.image.source="https://github.com/hrlab-uz/grading" \
      org.opencontainers.image.licenses="Proprietary" \
      org.opencontainers.image.version="${IMAGE_VERSION}" \
      org.opencontainers.image.revision="${IMAGE_REVISION}" \
      org.opencontainers.image.created="${IMAGE_CREATED}"

# Create non-root user (uid 10001 — well outside any host user range).
RUN groupadd --system --gid 10001 grading \
 && useradd  --system --uid 10001 --gid grading --home /app --shell /usr/sbin/nologin grading \
 && mkdir -p /app /tmp/grading \
 && chown -R grading:grading /app /tmp/grading

# curl is required for the HEALTHCHECK probe. Keep the layer thin.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder --chown=grading:grading /workspace/app.jar /app/app.jar

# JVM options come from env so K8s / docker-compose can tune them per env.
# Defaults are conservative; production values live in Helm values-*.yaml.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT=8080 \
    MANAGEMENT_SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Drop to non-root BEFORE the ENTRYPOINT.
USER 10001:10001

# Container-native healthcheck — Kubernetes uses its own probes, but this is
# valuable for docker-compose and local dev.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
