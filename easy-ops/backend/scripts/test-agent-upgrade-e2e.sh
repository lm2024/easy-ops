#!/bin/bash
# Agent 自升级 E2E 验证（开发机 Docker）
# 场景1：裸机 shell 模式（docker-compose.baremetal-test.yml）
# 场景2：Docker restart 模式（docker-compose.yml agent-1）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

AGENT_JAR="$ROOT/agent/target/easy-ops-agent-1.0.0-SNAPSHOT.jar"
API="${EASYOPS_API:-http://127.0.0.1:8081/api}"
PASS=0
FAIL=0

log() { echo "[E2E] $*"; }
ok()  { log "✅ $*"; PASS=$((PASS + 1)); }
bad() { log "❌ $*"; FAIL=$((FAIL + 1)); }

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

wait_agent_api() {
  local url="$1" max="${2:-60}" i=1
  while [ "$i" -le "$max" ]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  return 1
}

trigger_upgrade() {
  local base="$1"
  local sha
  sha="$(sha256_file "$AGENT_JAR")"
  curl -sf -X POST "$base/system/upgrade" \
    -F "file=@$AGENT_JAR" \
    -F "sha256=$sha"
}

# --- 准备 ---
if [ ! -f "$AGENT_JAR" ]; then
  log "编译 Agent..."
  export JAVA_HOME="${JAVA_HOME:-$HOME/.jdk8/Contents/Home}"
  mvn -q -pl agent -am package -DskipTests -f "$ROOT/pom.xml"
fi

if ! docker info >/dev/null 2>&1; then
  bad "Docker 未运行，请先启动 Docker Desktop"
  exit 1
fi

if ! curl -sf "$API/auth/captcha" >/dev/null 2>&1; then
  bad "Server 未在 $API 运行，请先启动 Server"
  exit 1
fi

log "=== 场景1：裸机 shell 模式自升级 ==="
docker rm -f ops-agent-baremetal-test 2>/dev/null || true
docker volume rm backend_agent-baremetal-data 2>/dev/null || true
docker compose -f docker-compose.baremetal-test.yml up -d

if wait_agent_api "http://127.0.0.1:2130/api/system/version" 45; then
  ok "裸机测试 Agent 首次启动成功"
else
  bad "裸机测试 Agent 首次启动失败"
  docker logs ops-agent-baremetal-test 2>&1 | tail -30
  exit 1
fi

log "触发裸机自升级..."
trigger_upgrade "http://127.0.0.1:2130/api" >/dev/null || true

if wait_agent_api "http://127.0.0.1:2130/api/system/version" 60; then
  ok "裸机自升级后 Agent API 恢复"
else
  bad "裸机自升级后 Agent 未恢复"
fi

sleep 8
if docker exec ops-agent-baremetal-test test -f /opt/agent/data/logs/upgrade-restart.log 2>/dev/null; then
  LOG_TAIL="$(docker exec ops-agent-baremetal-test tail -n 25 /opt/agent/data/logs/upgrade-restart.log 2>/dev/null || true)"
  echo "$LOG_TAIL"
  if echo "$LOG_TAIL" | grep -qE 'SUCCESS|新 Agent 运行中'; then
    ok "upgrade-restart.log 含 SUCCESS"
  else
    bad "upgrade-restart.log 未找到 SUCCESS"
  fi
else
  bad "upgrade-restart.log 不存在"
fi

VER_JSON="$(curl -sf http://127.0.0.1:2130/api/system/version 2>/dev/null || echo '{}')"
if echo "$VER_JSON" | grep -q 'shell'; then
  ok "重启模式为 shell"
else
  bad "重启模式不是 shell: $VER_JSON"
fi

log "=== 场景2：Docker restart 模式自升级 (agent-1) ==="
mkdir -p "$ROOT/agent/docker"
cp -f "$AGENT_JAR" "$ROOT/agent/docker/"
docker compose build agent-1
docker compose up -d agent-1

if wait_agent_api "http://127.0.0.1:2123/api/system/version" 60; then
  ok "Docker agent-1 启动成功"
else
  bad "Docker agent-1 启动失败"
  docker logs ops-agent-1 2>&1 | tail -30
fi

BEFORE_PID="$(docker inspect -f '{{.State.Pid}}' ops-agent-1 2>/dev/null || echo 0)"
log "agent-1 container PID=$BEFORE_PID, triggering upgrade..."
trigger_upgrade "http://127.0.0.1:2123/api" >/dev/null || true

if wait_agent_api "http://127.0.0.1:2123/api/system/version" 90; then
  ok "Docker 模式升级后 API 恢复"
else
  bad "Docker 模式升级后 API 未恢复"
fi

AFTER_PID="$(docker inspect -f '{{.State.Pid}}' ops-agent-1 2>/dev/null || echo 0)"
if [ "$BEFORE_PID" != "0" ] && [ "$AFTER_PID" != "0" ] && [ "$BEFORE_PID" != "$AFTER_PID" ]; then
  ok "Docker container restarted (PID $BEFORE_PID -> $AFTER_PID)"
else
  log "NOTE: container PID unchanged ($BEFORE_PID -> $AFTER_PID)"
fi

VER2="$(curl -sf http://127.0.0.1:2123/api/system/version || echo '{}')"
if echo "$VER2" | grep -q 'docker'; then
  ok "Docker 模式 deploymentType=docker"
else
  bad "Docker 模式识别异常: $VER2"
fi

log "=== 汇总: 通过 $PASS 项, 失败 $FAIL 项 ==="
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
log "全部 E2E 验证通过"
