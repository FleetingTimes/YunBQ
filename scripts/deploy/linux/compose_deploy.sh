#!/usr/bin/env bash
# YunBQ 一键部署（Linux，Docker Compose）
# 作用：拷贝模板、生成/使用 env 文件、启动 Compose、进行健康检查。
set -euo pipefail

# 可传入部署目标目录（默认 /opt/yunbq）
TARGET_DIR=${TARGET_DIR:-/opt/yunbq}
REPO_ROOT=$(cd "$(dirname "$0")"/../.. && pwd)

echo "[1/6] 准备目标目录：$TARGET_DIR"
sudo mkdir -p "$TARGET_DIR" "$TARGET_DIR/nginx" "$TARGET_DIR/data/mysql" "$TARGET_DIR/data/redis" "$TARGET_DIR/uploads/avatars"

echo "[2/6] 拷贝 Compose 与 Nginx 模板..."
sudo cp -f "$REPO_ROOT/scripts/templates/docker-compose.yml" "$TARGET_DIR/docker-compose.yml"
sudo cp -f "$REPO_ROOT/scripts/templates/nginx/default.conf" "$TARGET_DIR/nginx/default.conf"

echo "[3/6] 准备 env 文件..."
if [[ ! -f "$TARGET_DIR/docker-compose.env" ]]; then
  sudo cp -f "$REPO_ROOT/scripts/env/docker-compose.env.example" "$TARGET_DIR/docker-compose.env"
  echo "已生成示例 env：$TARGET_DIR/docker-compose.env，请根据注释修改敏感值"
fi

echo "[4/6] 校验构建产物..."
test -f "$REPO_ROOT/backend/target/backend-0.0.1-SNAPSHOT.jar" || { echo "后端 Jar 未构建，先执行：(cd backend && mvn -q -DskipTests package)"; exit 1; }
test -d "$REPO_ROOT/frontend/dist" || { echo "前端未构建，先执行：设置 VITE_API_BASE 后 (cd frontend && npm ci && npm run build)"; exit 1; }

echo "[5/6] 拷贝前后端产物到部署目录（只读挂载）..."
sudo mkdir -p "$TARGET_DIR/backend/target" "$TARGET_DIR/frontend"
sudo cp -f "$REPO_ROOT/backend/target/backend-0.0.1-SNAPSHOT.jar" "$TARGET_DIR/backend/target/"
sudo rsync -a --delete "$REPO_ROOT/frontend/dist/" "$TARGET_DIR/frontend/dist/"

echo "[6/6] 启动 Compose..."
pushd "$TARGET_DIR" >/dev/null
sudo docker compose --env-file "$TARGET_DIR/docker-compose.env" up -d
popd >/dev/null

echo "部署完成，进行健康检查..."
set +e
curl -fsS "http://com.linaa.shiyan:6639/" >/dev/null && echo "前端 OK" || echo "前端检查失败"
curl -fsS "http://com.linaa.shiyan:6639/api/health" >/dev/null || curl -fsS "http://com.linaa.shiyan:6639/actuator/health" >/dev/null && echo "后端 OK" || echo "后端检查失败"
set -e

echo "提示：如需开机自启，参考 生产部署-Docker-Nginx.md 的 systemd 片段。"