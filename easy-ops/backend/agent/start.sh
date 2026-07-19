#!/bin/bash
# =============================================================================
# EasyOps Agent 启动脚本
# =============================================================================
# 生产部署：修改下方环境变量或创建 easyops.env 文件
# 所有配置通过环境变量或 JVM 参数覆盖 application.yml 默认值
# =============================================================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---- 加载环境变量（如果存在 easyops.env 文件）----
if [ -f "$SCRIPT_DIR/easyops.env" ]; then
    . "$SCRIPT_DIR/easyops.env"
fi

# ---- 默认配置（可通过环境变量或 easyops.env 覆盖）----
AGENT_PORT="${AGENT_PORT:-2123}"
AGENT_SERVER_URL="${AGENT_SERVER_URL:-http://localhost:8081/api}"
AGENT_TOKEN="${AGENT_TOKEN:-}"
AGENT_NODE_NAME="${AGENT_NODE_NAME:-default-node}"
AGENT_DATA_PATH="${AGENT_DATA_PATH:-/app/data}"
AGENT_RESTART_MODE="${AGENT_RESTART_MODE:-auto}"
JAVA_BIN="${JAVA_BIN:-java}"

# ---- JVM 参数 ----
JVM_OPTS="${JVM_OPTS:--Xms128m -Xmx256m -XX:+UseG1GC -XX:-HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8}"

# ---- 查找 JAR 文件 ----
cd "$SCRIPT_DIR"
JAR=$(ls *.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: 未找到 jar 文件"
    exit 1
fi
JAR_PATH="$(pwd)/$JAR"

# ---- 检查是否已运行 ----
PID=$(pgrep -f "$JAR_PATH" 2>/dev/null || true)
if [ -n "$PID" ]; then
    echo "$JAR 已在运行 PID=$PID"
    exit 0
fi

# ---- 启动服务 ----
echo "Starting $JAR ..."
echo "  Port: $AGENT_PORT"
echo "  Server: $AGENT_SERVER_URL"
echo "  Node: $AGENT_NODE_NAME"

nohup $JAVA_BIN $JVM_OPTS \
    -Dserver.port="$AGENT_PORT" \
    -Dagent.server-url="$AGENT_SERVER_URL" \
    -Dagent.token="$AGENT_TOKEN" \
    -Dagent.node-name="$AGENT_NODE_NAME" \
    -Dagent.data-path="$AGENT_DATA_PATH" \
    -Dagent.restart-mode="$AGENT_RESTART_MODE" \
    -jar "$JAR_PATH" > /dev/null 2>&1 &

NEW_PID=$!
sleep 2

if kill -0 "$NEW_PID" 2>/dev/null; then
    echo "Started $JAR PID=$NEW_PID"
else
    echo "ERROR: 启动失败，请检查日志"
    exit 1
fi
