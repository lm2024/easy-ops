#!/bin/sh
# Agent 脚本公共库（仅 Agent 机器使用）

set -u

: "${EASYOPS_SCRIPT_ROOT:?请先设置 EASYOPS_SCRIPT_ROOT}"
_SCRIPT_DIR="$EASYOPS_SCRIPT_ROOT"
_PROJECT_AGENT="$(cd "$_SCRIPT_DIR/.." && pwd)"

if [ -f "$_SCRIPT_DIR/easyops.env" ]; then
  # shellcheck disable=SC1091
  . "$_SCRIPT_DIR/easyops.env"
else
  echo "[Agent] 未找到 $_SCRIPT_DIR/easyops.env" >&2
  exit 1
fi

if [ -z "${INSTALL_DIR:-}" ]; then
  INSTALL_DIR="$(cd "$_SCRIPT_DIR/.." && pwd)"
fi

resolve_java() {
  if [ -n "${AGENT_JAVA_BIN:-}" ]; then
    JAVA_BIN="$AGENT_JAVA_BIN"
  elif [ -n "${JAVA_HOME:-}" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  else
    JAVA_BIN="$(command -v java 2>/dev/null || true)"
  fi
  if [ -z "$JAVA_BIN" ]; then
    echo "[Agent] 找不到 Java，请设置 JAVA_HOME 或 AGENT_JAVA_BIN" >&2
    exit 1
  fi
}

resolve_paths() {
  AGENT_DATA_DIR="$INSTALL_DIR/data"
  AGENT_LOG_DIR="$AGENT_DATA_DIR/logs"
  AGENT_PID_FILE="$AGENT_LOG_DIR/agent.pid"
  AGENT_LOG_FILE="$AGENT_LOG_DIR/agent.log"
  UPGRADE_LOG_FILE="$AGENT_LOG_DIR/upgrade-restart.log"
  AGENT_JAR="$INSTALL_DIR/$AGENT_JAR_NAME"
  AGENT_RESTART_SCRIPT="$_SCRIPT_DIR/start.sh"

  if [ ! -f "$AGENT_JAR" ]; then
    _dev_jar="$_PROJECT_AGENT/target/easy-ops-agent-1.0.0-SNAPSHOT.jar"
    if [ -f "$_dev_jar" ]; then
      AGENT_JAR="$_dev_jar"
      AGENT_DATA_DIR="$_PROJECT_AGENT/data"
      AGENT_LOG_DIR="$AGENT_DATA_DIR/logs"
      AGENT_PID_FILE="$AGENT_LOG_DIR/agent.pid"
      AGENT_LOG_FILE="$AGENT_LOG_DIR/agent.log"
      AGENT_RESTART_SCRIPT="$_SCRIPT_DIR/start.sh"
    fi
  fi
}

print_paths() {
  echo "========== EasyOps Agent =========="
  echo "INSTALL_DIR     = $INSTALL_DIR"
  echo "AGENT_JAR       = $AGENT_JAR"
  echo "AGENT_DATA      = $AGENT_DATA_DIR"
  echo "AGENT_PORT      = $AGENT_PORT"
  echo "AGENT_SERVER_URL= $AGENT_SERVER_URL"
  echo "AGENT_NODE_NAME = $AGENT_NODE_NAME"
  echo "UPGRADE_LOG     = $UPGRADE_LOG_FILE"
  echo "RESTART_SCRIPT  = $AGENT_RESTART_SCRIPT"
  echo "==================================="
}

log_upgrade() {
  _msg="$1"
  _ts="$(date '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date)"
  mkdir -p "$AGENT_LOG_DIR"
  echo "[$_ts] $_msg" >> "$UPGRADE_LOG_FILE"
  echo "[Agent/upgrade] $_msg"
}

log_upgrade_fail() {
  log_upgrade "FAIL: $1"
  echo "[Agent] 升级重启失败: $1" >&2
  echo "[Agent] 详情见: $UPGRADE_LOG_FILE" >&2
}

ensure_dirs() {
  mkdir -p "$AGENT_LOG_DIR" "$AGENT_DATA_DIR/versions"
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
  echo "[Agent] 停止 PID=$1 ..."
  kill "$1" 2>/dev/null || true
  sleep 2
  kill -0 "$1" 2>/dev/null && kill -9 "$1" 2>/dev/null || true
}

export_agent_env() {
  export AGENT_RESTART_MODE
  export AGENT_DATA_PATH="$AGENT_DATA_DIR"
  export AGENT_JAR_PATH="$AGENT_JAR"
  export AGENT_SERVER_URL
  export AGENT_NODE_NAME
  export AGENT_HOST_PORT="$AGENT_PORT"
  export AGENT_RESTART_SCRIPT
  if [ -n "${AGENT_TOKEN:-}" ]; then export AGENT_TOKEN; fi
}
