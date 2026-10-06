# syntax=docker/dockerfile:1.7
#
# Multi-stage Dockerfile for the mcp-java-benchmark (Fase 1).
#
# Stage 1 (builder): Maven 3 + JDK 17 to compile & package target-codebase.
# Stage 2 (runtime): minimal JRE Alpine, non-root user, tiny attack surface.
#
# The benchmark has no web service; the container runs the benchmark harness
# (com.sandbox.benchmark.BenchmarkRunner) and exits with a deterministic JSON
# summary. This makes the image a drop-in CI verification artifact.

# ---------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 1) pom.xml first to maximise layer cache for dependency downloads.
COPY target-codebase/pom.xml .
RUN mvn -B -q dependency:go-offline

# 2) sources (main + test + benchmark).
COPY target-codebase/src ./src

# 3) compile + test + package (tests are part of the build contract).
RUN mvn -B clean package

# ---------------------------------------------------------------------------
# Stage 2: runtime (JRE Alpine, non-root, minimal)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Non-root user for least privilege.
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /opt/app
COPY --from=builder /build/target/target-codebase-1.0-SNAPSHOT.jar /opt/app/app.jar

# Tunable JVM memory (no container-specific flags, portable).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Deterministic benchmark entrypoint: runs the O(N) pipeline and prints JSON.
ENTRYPOINT ["sh", "-c"]
CMD ["java $JAVA_OPTS -cp /opt/app/app.jar com.sandbox.benchmark.BenchmarkRunner"]