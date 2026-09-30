# syntax=docker/dockerfile:1
# =============================================================================
# 决策服务（modules/decision-api，8081）镜像构建
#
# 构建命令（在仓库根目录执行）：
#   docker build -f deploy/docker/decision-api.Dockerfile -t rule-engine/decision-api:dev .
#
# 基础镜像说明（ARM64 教训，勿改回 alpine）：
#   - 构建阶段 gradle:9-jdk25：官方多架构镜像（amd64/arm64 已核实），
#     Ubuntu noble 系（非 alpine）；本机 ARM64 可直接构建
#   - 运行阶段 eclipse-temurin:25-jre-jammy：Ubuntu jammy，多架构含 arm64
#     （eclipse-temurin 的 alpine 变体无 arm64 镜像，本机构建必失败）
#
# 分层缓存策略（Boot 4 实测验证）：
#   java -Djarmode=tools -jar app.jar extract --layers --destination extracted
#   产出 dependencies/（三方依赖，极少变）/ spring-boot-loader/ / snapshot-dependencies/
#   / application/（业务代码，最常变），分四个 COPY 层拷入运行镜像，
#   代码变更只重建 application 层。命令与运行方式已按
#   Spring Boot 4.1 官方文档（packaging/container-images/dockerfiles）核对并实测可启动。
# =============================================================================
FROM gradle:9-jdk25 AS builder
WORKDIR /build

# 先拷构建骨架（build-logic 约定插件 + wrapper + 版本目录），依赖解析失败时层缓存仍可用
COPY settings.gradle.kts gradle.properties gradlew ./
COPY gradle/ ./gradle/
COPY build-logic/ ./build-logic/

# 再拷模块源码（bootJar 只编译打包，不跑测试）
COPY modules/ ./modules/

# BuildKit 缓存挂载：Gradle 用户目录（依赖 + wrapper 发行包）不进镜像层，重建复用
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :modules:decision-api:bootJar --no-daemon

# 取非 plain 的 bootJar，固定名为 app.jar 后按 Spring Boot 分层解包
# （extract 会保留源 jar 名，固定名可让运行阶段 ENTRYPOINT 指向稳定的 app.jar）
RUN BOOT_JAR=$(ls modules/decision-api/build/libs/*.jar | grep -v -- -plain.jar) \
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

# 四个分层拷贝（对应 extracted 下四个目录，各自独立镜像层）
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

# JVM 参数经 JVM_OPTS 注入（K8s/compose 可覆盖）：
#   UseCompactObjectHeaders：JDK 25 默认压缩对象头（Lilliput，已为产品特性）
#   UseZGC：低停顿 GC；MaxRAMPercentage=75：容器内存感知堆上限
ENV JVM_OPTS="-XX:+UseCompactObjectHeaders -XX:+UseZGC -XX:MaxRAMPercentage=75" \
    SERVER_PORT="8081" \
    TZ="Asia/Shanghai"

USER app
EXPOSE 8081

# 就绪探针路径（Boot 4 probes：/actuator/health/liveness 与 /readiness）
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1

# exec 保持 PID 1 信号语义；JVM_OPTS 不加引号以便多参数拆分
ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS -jar app.jar"]
