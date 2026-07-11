#!/bin/sh
# Server 脚本公共库（仅 Server 机器使用）
# 由 start.sh / stop.sh 设置 EASYOPS_SCRIPT_ROOT 后 source

set -u

: "${EASYOPS_SCRIPT_ROOT:?请先设置 EASYOPS_SCRIPT_ROOT}"
_SCRIPT_DIR="$EASYOPS_SCRIPT_ROOT"
_PROJECT_SERVER="$(cd "$_SCRIPT_DIR/.." && pwd)"

if [ -f "$_SCRIPT_DIR/easyops.env" ]; then
  # shellcheck disable=SC1091
  . "$_SCRIPT_DIR/easyops.env"
else
  echo "[Server] 未找到 $_SCRIPT_DIR/easyops.env" >&2
  exit 1
fi

if [ -z "${INSTALL_DIR:-}" ]; then
  INSTALL_DIR="$_SCRIPT_DIR"
fi

resolve_java() {
  if [ -n "${JAVA_HOME:-}" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  else
    JAVA_BIN="$(command -v java 2>/dev/null || true)"
  fi
  if [ -z "$JAVA_BIN" ]; then
    echo "[Server] 找不到 Java，请设置 JAVA_HOME" >&2
    exit 1
  fi
}

resolve_paths() {
  SERVER_DATA_DIR="$INSTALL_DIR/data"
  SERVER_LOG_DIR="$SERVER_DATA_DIR/logs"
  SERVER_PID_FILE="$SERVER_DATA_DIR/server.pid"
  SERVER_LOG_FILE="$SERVER_LOG_DIR/server.log"

  SERVER_JAR="$INSTALL_DIR/$SERVER_JAR_NAME"
  if [ ! -f "$SERVER_JAR" ]; then
    _dev_jar="$_PROJECT_SERVER/target/ops-platform-server-1.0.0-SNAPSHOT.jar"
    if [ -f "$_dev_jar" ]; then
      SERVER_JAR="$_dev_jar"
      SERVER_DATA_DIR="$_PROJECT_SERVER/data"
      SERVER_LOG_DIR="$SERVER_DATA_DIR/logs"
      SERVER_PID_FILE="$SERVER_DATA_DIR/server.pid"
      SERVER_LOG_FILE="$SERVER_LOG_DIR/server.log"
    fi
  fi
}

print_paths() {
  echo "========== EasyOps Server =========="
  echo "INSTALL_DIR    = $INSTALL_DIR"
  echo "SERVER_JAR     = $SERVER_JAR"
  echo "SERVER_DATA    = $SERVER_DATA_DIR"
  echo "SERVER_PORT    = $SERVER_PORT"
  echo "AGENT_DATA_PATH= $AGENT_DATA_PATH  (Agent 机器上的路径，供部署计算用)"
  echo "===================================="
}

ensure_dirs() {
  mkdir -p "$SERVER_LOG_DIR" "$SERVER_DATA_DIR/versions"
}

read_pid() {
  if [ -f "$1" ]; then cat "$1" 2>/dev/null; fi
}

is_running() {
  _p="$(read_pid "$1")"
  [ -n "$_p" ] && kill -0 "$_p" 2>/dev/null
}

pids_on_port() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti ":$1" 2>/dev/null || true
  elif command -v ss >/dev/null 2>&1; then
    ss -lptn "sport = :$1" 2>/dev/null | grep -o 'pid=[0-9]*' | cut -d= -f2 | sort -u || true
  fi
}

stop_pid() {
  [ -z "$1" ] && return 0
  echo "[Server] 停止 PID=$1 ..."
  kill "$1" 2>/dev/null || true
  sleep 2
  kill -0 "$1" 2>/dev/null && kill -9 "$1" 2>/dev/null || true
}

server_java_args() {
  echo "-Dserver.port=$SERVER_PORT"
  echo "-Dserver.path=$SERVER_DATA_DIR"
  echo "-Dspring.datasource.url=jdbc:h2:file:${SERVER_DATA_DIR}/ops;MODE=MySQL;AUTO_SERVER=TRUE"
  echo "-Dlogging.file.name=${SERVER_LOG_FILE}"
  echo "-Dops.global.agent-data-path=$AGENT_DATA_PATH"
}
