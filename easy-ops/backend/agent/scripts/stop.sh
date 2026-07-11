#!/bin/sh
set -e
EASYOPS_SCRIPT_ROOT="$(cd "$(dirname "$0")" && pwd)"
export EASYOPS_SCRIPT_ROOT
# shellcheck disable=SC1091
. "$EASYOPS_SCRIPT_ROOT/lib/common.sh"
resolve_paths

stop_pid "$(read_pid "$AGENT_PID_FILE")"
for pid in $(pids_on_port "$AGENT_PORT"); do stop_pid "$pid"; done
rm -f "$AGENT_PID_FILE"
echo "[Agent] 已停止"
