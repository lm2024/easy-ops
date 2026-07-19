#!/bin/bash
# =============================================================================
# EasyOps Server 停止脚本
# =============================================================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

JAR=$(ls *.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: 未找到 jar 文件"
    exit 1
fi

JAR_PATH="$(pwd)/$JAR"
PIDS=$(pgrep -f "$JAR_PATH" 2>/dev/null || true)

if [ -z "$PIDS" ]; then
    echo "$JAR 未在运行"
    exit 0
fi

echo "Stopping $JAR (PIDs: $PIDS) ..."

# 优雅停止
for p in $PIDS; do
    kill "$p" 2>/dev/null || true
done

# 等待进程退出
echo "Waiting for processes to exit..."
sleep 3

# 强制停止未退出的进程
for p in $PIDS; do
    if kill -0 "$p" 2>/dev/null; then
        echo "Force killing PID=$p"
        kill -9 "$p" 2>/dev/null || true
    fi
done

echo "Stopped $JAR"
