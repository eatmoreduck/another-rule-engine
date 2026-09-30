# syntax=docker/dockerfile:1
# =============================================================================
# 前端（frontend/，React + Vite）静态站点镜像：node 构建 + nginx 托管
#
# 构建命令（在仓库根目录执行，context 必须是仓库根，nginx 分流配置在 deploy/docker/ 下）：
#   docker build -f deploy/docker/frontend.Dockerfile -t rule-engine/frontend:dev .
#
# 基础镜像说明：node:22（Debian 系，多架构含 arm64）、nginx:stable（Debian 系，
# 多架构含 arm64 已核实）——均为非 alpine，避开 ARM64 无镜像的坑。
# 生产 API 分流由 nginx 按路径转发（deploy/docker/nginx-frontend.conf）。
# =============================================================================
FROM node:22 AS builder
WORKDIR /build

# 先装依赖（package-lock 固定版本，npm ci 保证可复现）
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# 再拷源码并构建（产物在 /build/dist）
COPY frontend/ ./
# build 脚本 = "tsc -b && vite build"；跳过类型检查可用 npx vite build
RUN npm run build

# -----------------------------------------------------------------------------
# 托管阶段
# -----------------------------------------------------------------------------
FROM nginx:stable

RUN rm /etc/nginx/conf.d/default.conf

# nginx 站点配置：API 路径分流 + SPA history 回退 + gzip
COPY deploy/docker/nginx-frontend.conf /etc/nginx/conf.d/default.conf

COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -fsS http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
