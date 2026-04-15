# ---- Stage 1: Build ----
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
# 先下载依赖（利用 Docker 缓存层）
RUN gradle dependencies --no-daemon || true
RUN gradle bootJar --no-daemon -x test

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="another-rule-engine"

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r app && useradd -r -g app app

# 从 builder 阶段复制 JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# 创建数据目录
RUN mkdir -p /app/logs && chown -R app:app /app

USER app

# 环境变量（可通过 docker-compose / docker run 覆盖）
ENV DB_URL=jdbc:postgresql://db:5432/yare_engine \
    DB_USERNAME=yare \
    DB_PASSWORD=yare_secret \
    JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
