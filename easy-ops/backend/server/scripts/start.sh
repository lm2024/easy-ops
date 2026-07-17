#!/bin/sh
# EasyOps Server 启动（拷到 Server 机器单独运行，与 Agent/前端无关）
set -e
EASYOPS_SCRIPT_ROOT="$(cd "$(dirname "$0")" && pwd)"
export EASYOPS_SCRIPT_ROOT
# shellcheck disable=SC1091
. "$EASYOPS_SCRIPT_ROOT/lib/common.sh"
resolve_paths
resolve_java
ensure_dirs
print_paths

if is_running "$SERVER_PID_FILE"; then
  echo "[Server] 已在运行 PID=$(read_pid "$SERVER_PID_FILE")"
  echo "[Server] API: http://0.0.0.0:${SERVER_PORT}/api/"
  exit 0
fi

for pid in $(pids_on_port "$SERVER_PORT"); do stop_pid "$pid"; done

if [ ! -f "$SERVER_JAR" ]; then
  echo "[Server] 未找到 jar: $SERVER_JAR" >&2
  exit 1
fi

# JWT_SECRET 仅用于 SecurityConfig 启动校验，实际 token 由 SecureRandom 生成，非标准 JWT
export JWT_SECRET
ARGS=""
for a in $(server_java_args); do ARGS="$ARGS $a"; done

echo "[Server] 启动..."
cd "$(dirname "$SERVER_JAR")"
# shellcheck disable=SC2086
setsid nohup "$JAVA_BIN" -jar "$SERVER_JAR" $ARGS >> "$SERVER_LOG_FILE" 2>&1 </dev/null &
echo $! > "$SERVER_PID_FILE"
sleep 3

if is_running "$SERVER_PID_FILE"; then
  echo "[Server] 成功 PID=$(read_pid "$SERVER_PID_FILE")"
  echo "[Server] 日志: $SERVER_LOG_FILE"
  echo "[Server] API:  http://0.0.0.0:${SERVER_PORT}/api/"
else
  echo "[Server] 失败，查看: $SERVER_LOG_FILE" >&2
  exit 1
fi
