#!/usr/bin/env bash
# YunBQ 一键停止本地运行（Linux）
# 说明：读取 scripts/.pids 下记录的后端与前端 PID 并停止。
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "$0")"/../.. && pwd)
PIDS_DIR="$REPO_ROOT/scripts/.pids"

stop_by_pid() {
  local name="$1" file="$2"
  local path="$PIDS_DIR/$file"
  if [[ ! -f "$path" ]]; then
    echo "未发现 $name 的 PID 文件：$path"; return 0
  fi
  local pid=$(head -n1 "$path")
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" || true
    echo "已停止 $name 进程 (PID=$pid)"
  else
    echo "$name 进程不存在 (PID=$pid)"
  fi
  rm -f "$path"
}

stop_by_pid "后端" "backend.pid"
stop_by_pid "前端" "frontend.pid"

echo "完成：本地运行进程已尝试停止。"