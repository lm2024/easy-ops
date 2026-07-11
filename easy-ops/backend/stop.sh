#!/bin/bash
# 停止后端服务
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/server/server.pid"

PIDS=$(lsof -ti :8081 2>/dev/null || true)
if [ -z "$PIDS" ] && [ -f "$PID_FILE" ]; then
  PIDS=$(cat "$PID_FILE" 2>/dev/null)
fi

if [ -z "$PIDS" ]; then
  echo "后端未在运行"
  rm -f "$PID_FILE"
  exit 0
fi

echo "停止后端: $PIDS"
kill $PIDS 2>/dev/null || kill -9 $PIDS 2>/dev/null || true
rm -f "$PID_FILE"
echo "已停止"
