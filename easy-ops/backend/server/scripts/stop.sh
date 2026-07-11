#!/bin/sh
set -e
EASYOPS_SCRIPT_ROOT="$(cd "$(dirname "$0")" && pwd)"
export EASYOPS_SCRIPT_ROOT
# shellcheck disable=SC1091
. "$EASYOPS_SCRIPT_ROOT/lib/common.sh"
resolve_paths

stop_pid "$(read_pid "$SERVER_PID_FILE")"
for pid in $(pids_on_port "$SERVER_PORT"); do stop_pid "$pid"; done
rm -f "$SERVER_PID_FILE"
echo "[Server] 已停止"
