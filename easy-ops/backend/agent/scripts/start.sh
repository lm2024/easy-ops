#!/bin/sh
# EasyOps Agent 启动（拷到 Agent 机器单独运行；亦作为裸机自升级的外置重启脚本）
set -e
EASYOPS_SCRIPT_ROOT="$(cd "$(dirname "$0")" && pwd)"
export EASYOPS_SCRIPT_ROOT
# shellcheck disable=SC1091
. "$EASYOPS_SCRIPT_ROOT/lib/common.sh"
resolve_paths
resolve_java
ensure_dirs
export_agent_env
print_paths

AGENT_JAR="${AGENT_UPGRADE_JAR:-$AGENT_JAR}"
IS_UPGRADE="${AGENT_UPGRADE:-0}"
OLD_PID="${AGENT_OLD_PID:-0}"

if [ "$IS_UPGRADE" = "1" ]; then
  log_upgrade "=== 外置 start.sh 升级重启开始 ==="
  log_upgrade "目标 Jar=$AGENT_JAR OLD_PID=$OLD_PID"
  if [ -n "$OLD_PID" ] && [ "$OLD_PID" != "0" ]; then
    log_upgrade "等待旧进程 $OLD_PID 退出..."
    _w=0
    while [ "$_w" -lt 30 ]; do
      kill -0 "$OLD_PID" 2>/dev/null || break
      _w=$((_w + 1))
      sleep 1
    done
    if kill -0 "$OLD_PID" 2>/dev/null; then
      log_upgrade_fail "旧进程 $OLD_PID 在 30 秒内未退出"
      exit 1
    fi
    log_upgrade "旧进程已退出"
  fi
elif is_running "$AGENT_PID_FILE"; then
  echo "[Agent] 已在运行 PID=$(read_pid "$AGENT_PID_FILE")"
  exit 0
fi

if [ ! -f "$AGENT_JAR" ]; then
  log_upgrade_fail "Jar 不存在: $AGENT_JAR"
  exit 1
fi

if [ ! -x "$JAVA_BIN" ] && [ ! -f "$JAVA_BIN" ]; then
  log_upgrade_fail "Java 不可用: $JAVA_BIN（设置 JAVA_HOME 或 AGENT_JAVA_BIN）"
  exit 1
fi

cd "$(dirname "$AGENT_JAR")" || { log_upgrade_fail "无法进入 $(dirname "$AGENT_JAR")"; exit 1; }

echo "[Agent] 启动，连接 $AGENT_SERVER_URL ..."
if [ "$IS_UPGRADE" = "1" ]; then
  log_upgrade "启动命令: $JAVA_BIN -jar $AGENT_JAR (port=$AGENT_PORT)"
fi

setsid nohup "$JAVA_BIN" -jar "$AGENT_JAR" \
  -Dagent.data-path="$AGENT_DATA_DIR" \
  -Dagent.jar-path="$AGENT_JAR" \
  -Dagent.restart-script="$AGENT_RESTART_SCRIPT" \
  -Dserver.port="$AGENT_PORT" \
  >> "$AGENT_LOG_FILE" 2>&1 </dev/null &
NEW_PID=$!
echo "$NEW_PID" > "$AGENT_PID_FILE"
sleep 3

if kill -0 "$NEW_PID" 2>/dev/null; then
  echo "[Agent] 成功 PID=$NEW_PID"
  echo "[Agent] 日志: $AGENT_LOG_FILE"
  if [ "$IS_UPGRADE" = "1" ]; then
    log_upgrade "SUCCESS: 新 Agent 运行中 PID=$NEW_PID"
  fi
  if [ -z "${AGENT_TOKEN:-}" ]; then
    echo "[Agent] Token 未配置，请查看 agent.log 中的自动生成 Token"
  fi
else
  log_upgrade_fail "新进程 PID=$NEW_PID 已退出"
  log_upgrade "--- agent.log 末尾 ---"
  tail -n 30 "$AGENT_LOG_FILE" >> "$UPGRADE_LOG_FILE" 2>/dev/null || true
  if [ -n "${AGENT_BACKUP_JAR:-}" ] && [ -f "$AGENT_BACKUP_JAR" ]; then
    log_upgrade "尝试备份 jar 回滚: $AGENT_BACKUP_JAR"
    cp "$AGENT_BACKUP_JAR" "$AGENT_JAR"
    setsid nohup "$JAVA_BIN" -jar "$AGENT_JAR" \
      -Dagent.data-path="$AGENT_DATA_DIR" \
      -Dagent.jar-path="$AGENT_JAR" \
      -Dagent.restart-script="$AGENT_RESTART_SCRIPT" \
      -Dserver.port="$AGENT_PORT" \
      >> "$AGENT_LOG_FILE" 2>&1 </dev/null &
    RB_PID=$!
    echo "$RB_PID" > "$AGENT_PID_FILE"
    sleep 3
    if kill -0 "$RB_PID" 2>/dev/null; then
      log_upgrade "ROLLBACK OK: 备份 jar 已恢复 PID=$RB_PID"
      exit 0
    fi
    log_upgrade_fail "备份 jar 回滚后仍无法启动"
  fi
  exit 1
fi
