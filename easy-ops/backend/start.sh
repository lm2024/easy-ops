#!/bin/bash
# 后端服务启动脚本 - 固定工作目录确保数据库路径一致
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/server/target/ops-platform-server-1.0.0-SNAPSHOT.jar"
PID_FILE="$SCRIPT_DIR/server/server.pid"
JAVA_BIN="${JAVA_HOME:-$HOME/.jdk8/Contents/Home}/bin/java"
LOG_FILE="$SCRIPT_DIR/server/backend-live.log"

if [ ! -f "$JAR" ]; then
  echo "编译后端..."
  JAVA_HOME="$HOME/.jdk8/Contents/Home" mvn clean package -DskipTests -q -f "$SCRIPT_DIR/pom.xml"
fi

# 已在运行且可访问则跳过，避免重复启动把正在服务的进程杀掉
if curl -sf -o /dev/null "http://127.0.0.1:8081/api/auth/captcha" 2>/dev/null; then
  RUNNING_PID=$(lsof -ti :8081 2>/dev/null | head -1)
  echo "后端已在运行 (PID: ${RUNNING_PID:-unknown})"
  echo "API:  http://localhost:8081/api/"
  exit 0
fi

# 停止占用 8081 的僵死进程
OLD_PIDS=$(lsof -ti :8081 2>/dev/null || true)
if [ -n "$OLD_PIDS" ]; then
  echo "清理端口 8081 占用: $OLD_PIDS"
  kill -9 $OLD_PIDS 2>/dev/null || true
  sleep 2
fi

echo "启动后端服务 (端口 8081)..."
cd "$SCRIPT_DIR/server"
export JWT_SECRET="${JWT_SECRET:-easy-ops-jwt-secret-key-2024-secure-random}"
nohup "$JAVA_BIN" -jar "$JAR" >> "$LOG_FILE" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"
sleep 3

if curl -sf -o /dev/null "http://127.0.0.1:8081/api/auth/captcha" 2>/dev/null; then
  echo "PID: $PID"
  echo "日志: $LOG_FILE"
  echo "API:  http://localhost:8081/api/"
  echo ""
  echo "停止: kill $PID  或  bash $SCRIPT_DIR/stop.sh"
else
  echo "启动失败，请查看日志: $LOG_FILE"
  exit 1
fi
