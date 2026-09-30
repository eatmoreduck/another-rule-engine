# syntax=docker/dockerfile:1
# =============================================================================
# 管理服务（modules/admin-api，8080）镜像构建
#
# 构建命令（在仓库根目录执行）：
#   docker build -f deploy/docker/admin-api.Dockerfile -t rule-engine/admin-api:dev .
#
# 基础镜像与分层策略说明同 decision-api.Dockerfile 头注释（jammy/noble 系、非 alpine，
# gradle:9-jdk25 与 eclipse-temurin:25-jre-jammy 均为多架构含 arm64，已核实可构建）。
# =============================================================================
FROM gradle:9-jdk25 AS builder
WORKDIR /build

# 先拷构建骨架（build-logic 约定插件 + wrapper + 版本目录）
COPY settings.gradle.kts gradle.properties gradlew ./
COPY gradle/ ./gradle/
COPY build-logic/ ./build-logic/

# 再拷模块源码（bootJar 只编译打包，不跑测试）
COPY modules/ ./modules/

# BuildKit 缓存挂载：Gradle 用户目录不进镜像层
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :modules:admin-api:bootJar --no-daemon

# 取非 plain 的 bootJar，固定名为 app.jar 后按 Spring Boot 分层解包
# （extract 会保留源 jar 名，固定名可让运行阶段 ENTRYPOINT 指向稳定的 app.jar）
RUN BOOT_JAR=$(ls modules/admin-api/build/libs/*.jar | grep -v -- -plain.jar) \
    && echo "bootJar: ${BOOT_JAR}" \
    && cp "${BOOT_JAR}" app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# -----------------------------------------------------------------------------
# 运行阶段
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-jammy

# 非 root 运行用户 + curl（HEALTHCHECK 用；temurin jammy 基础镜像无 wget/curl）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app \
    && useradd -r -g app -d /app app \
    && mkdir -p /app/logs \
    && chown -R app:app /app

WORKDIR /app

COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

ENV JVM_OPTS="-XX:+UseCompactObjectHeaders -XX:+UseZGC -XX:MaxRAMPercentage=75" \
    SERVER_PORT="8080" \
    TZ="Asia/Shanghai"

USER app
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS -jar app.jar"]
