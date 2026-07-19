#!/bin/bash
# =============================================================================
# EasyOps Server 启动脚本
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
SERVER_PORT="${SERVER_PORT:-8081}"
JWT_SECRET="${JWT_SECRET:-easyops-dev-secret-key-for-local-development-only-2026}"
SERVER_DATA="${EASYOPS_SERVER_DATA:-./data}"
AGENT_DATA_PATH="${AGENT_DATA_PATH:-/app/data}"
JAVA_BIN="${JAVA_BIN:-java}"

# ---- JVM 参数 ----
JVM_OPTS="${JVM_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC -XX:-HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8}"

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
echo "  Port: $SERVER_PORT"
echo "  Data: $SERVER_DATA"

nohup $JAVA_BIN $JVM_OPTS \
    -Dserver.port="$SERVER_PORT" \
    -Dserver.path="$SERVER_DATA" \
    -Dspring.datasource.url="jdbc:h2:file:${SERVER_DATA}/ops;MODE=MySQL;AUTO_SERVER=TRUE" \
    -Djwt.secret="$JWT_SECRET" \
    -Dagent.data-path="$AGENT_DATA_PATH" \
    -Dops.global.agent-data-path="$AGENT_DATA_PATH" \
    -jar "$JAR_PATH" > /dev/null 2>&1 &

NEW_PID=$!
sleep 2

if kill -0 "$NEW_PID" 2>/dev/null; then
    echo "Started $JAR PID=$NEW_PID"
else
    echo "ERROR: 启动失败，请检查日志"
    exit 1
fi
