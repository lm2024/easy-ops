#!/bin/sh
set -e
EASYOPS_SCRIPT_ROOT="$(cd "$(dirname "$0")" && pwd)"
export EASYOPS_SCRIPT_ROOT
# shellcheck disable=SC1091
. "$EASYOPS_SCRIPT_ROOT/lib/common.sh"
resolve_paths

case "$SERVE_MODE" in
  nginx)
    resolve_nginx
    if [ -f "$INSTALL_DIR/nginx.pid" ]; then
      stop_pid "$(cat "$INSTALL_DIR/nginx.pid")"
      rm -f "$INSTALL_DIR/nginx.pid"
    fi
    if [ -n "$NGINX" ] && [ -f "$NGINX_CONF" ]; then
      "$NGINX" -s stop -c "$NGINX_CONF" -p "$INSTALL_DIR" 2>/dev/null || true
    fi
    ;;
  *)
    stop_pid "$(read_pid "$PID_FILE")"
    for pid in $(pids_on_port "$FRONTEND_PORT"); do stop_pid "$pid"; done
    ;;
esac

rm -f "$PID_FILE"
echo "[Frontend] 已停止"
