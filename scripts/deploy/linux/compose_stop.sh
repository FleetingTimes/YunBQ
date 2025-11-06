#!/usr/bin/env bash
# YunBQ 一键停止部署（Linux，Docker Compose）
set -euo pipefail

TARGET_DIR=${TARGET_DIR:-/opt/yunbq}
pushd "$TARGET_DIR" >/dev/null
sudo docker compose down
popd >/dev/null
echo "已停止 YunBQ Compose 堆栈：$TARGET_DIR"