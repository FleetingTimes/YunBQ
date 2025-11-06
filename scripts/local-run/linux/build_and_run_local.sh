#!/usr/bin/env bash
# YunBQ 一键本地运行（Linux）
# 目标：不启用任何开发服务器/热重载，使用生产构建与打包产物在本机启动前后端。
# 说明：请确保 Java 17、Maven、Node.js 与 npm 已安装；MySQL/Redis 可使用本机或容器。
set -euo pipefail

# 配置区（按需调整）
BACKEND_DIR=${BACKEND_DIR:-backend}
FRONTEND_DIR=${FRONTEND_DIR:-frontend}
BACKEND_PORT=${BACKEND_PORT:-6639}
FRONTEND_PORT=${FRONTEND_PORT:-5173}
API_BASE=${API_BASE:-http://localhost:6639/api}
JWT_SECRET=${JWT_SECRET:-please_replace_with_a_strong_secret}
DB_URL=${DB_URL:-jdbc:mysql://localhost:3306/yunbq?useSSL=false&serverTimezone=UTC}
DB_USER=${DB_USER:-yunbq_user}
DB_PASS=${DB_PASS:-strong_password_here}
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}
FRONTEND_BASE_URL=${FRONTEND_BASE_URL:-http://localhost:5173}

REPO_ROOT=$(cd "$(dirname "$0")"/../.. && pwd)
PIDS_DIR="$REPO_ROOT/scripts/.pids"
mkdir -p "$PIDS_DIR" "$REPO_ROOT/logs"

echo "[1/5] 构建后端 Jar..."
pushd "$REPO_ROOT/$BACKEND_DIR" >/dev/null
mvn -q -DskipTests package
JAR_PATH="$REPO_ROOT/$BACKEND_DIR/target/backend-0.0.1-SNAPSHOT.jar"
test -f "$JAR_PATH" || { echo "未找到 Jar：$JAR_PATH"; exit 1; }
popd >/dev/null

echo "[2/5] 启动后端 (port=$BACKEND_PORT)..."
nohup java -jar "$JAR_PATH" \
  --spring.profiles.active=prod \
  --server.port="$BACKEND_PORT" \
  --spring.datasource.url="$DB_URL" \
  --spring.datasource.username="$DB_USER" \
  --spring.datasource.password="$DB_PASS" \
  --spring.redis.host="$REDIS_HOST" \
  --spring.redis.port="$REDIS_PORT" \
  --jwt.secret="$JWT_SECRET" \
  --frontend.base-url="$FRONTEND_BASE_URL" \
  > "$REPO_ROOT/logs/backend.out" 2>&1 &
echo $! > "$PIDS_DIR/backend.pid"
sleep 3

echo "[3/5] 构建前端并启动静态站点 (port=$FRONTEND_PORT)..."
pushd "$REPO_ROOT/$FRONTEND_DIR" >/dev/null
npm ci
export VITE_API_BASE="$API_BASE"
npm run build
nohup npx -y serve -s dist -l "$FRONTEND_PORT" \
  > "$REPO_ROOT/logs/frontend.out" 2>&1 &
echo $! > "$PIDS_DIR/frontend.pid"
popd >/dev/null

echo "[4/5] 健康检查..."
set +e
curl -fsS "http://localhost:$BACKEND_PORT/actuator/health" || curl -fsS "http://localhost:$BACKEND_PORT/api/health"
curl -fsS "http://localhost:$FRONTEND_PORT" >/dev/null
set -e

echo "[5/5] 完成：后端 PID=$(cat "$PIDS_DIR/backend.pid"), 前端 PID=$(cat "$PIDS_DIR/frontend.pid")"