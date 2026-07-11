#!/bin/sh
# EasyOps 前端启动（前端机器单独运行）
set -e
EASYOPS_SCRIPT_ROOT="$(cd "$(dirname "$0")" && pwd)"
export EASYOPS_SCRIPT_ROOT
# shellcheck disable=SC1091
. "$EASYOPS_SCRIPT_ROOT/lib/common.sh"
resolve_paths
print_paths

if is_running "$PID_FILE"; then
  echo "[Frontend] 已在运行 PID=$(read_pid "$PID_FILE")"
  echo "[Frontend] 访问: http://0.0.0.0:${FRONTEND_PORT}/"
  exit 0
fi

case "$SERVE_MODE" in
  nginx)
    if [ ! -d "$DIST_DIR" ]; then
      echo "[Frontend] 未找到 dist: $DIST_DIR，请先执行 build.sh 或拷贝 dist" >&2
      exit 1
    fi
    resolve_nginx
    if [ -z "$NGINX" ]; then
      echo "[Frontend] 未找到 nginx，请安装 Nginx 或设置 NGINX_BIN" >&2
      exit 1
    fi
    write_nginx_conf
    echo "[Frontend] Nginx 启动，端口 $FRONTEND_PORT ..."
    "$NGINX" -c "$NGINX_CONF" -p "$INSTALL_DIR"
    sleep 1
    if [ -f "$INSTALL_DIR/nginx.pid" ]; then
      cp "$INSTALL_DIR/nginx.pid" "$PID_FILE"
    fi
    echo "[Frontend] 成功，访问: http://0.0.0.0:${FRONTEND_PORT}/"
    ;;
  dev)
    cd "$_PROJECT_FRONTEND" || exit 1
    [ ! -d node_modules ] && npm install
    setsid nohup npm run dev -- --port "$FRONTEND_PORT" --host 0.0.0.0 >> "$LOG_FILE" 2>&1 </dev/null &
    echo $! > "$PID_FILE"
    sleep 2
    echo "[Frontend] 开发模式，访问: http://0.0.0.0:${FRONTEND_PORT}/"
    ;;
  preview)
    if [ ! -d "$DIST_DIR" ]; then
      echo "[Frontend] 未找到 dist: $DIST_DIR" >&2
      exit 1
    fi
    cd "$_PROJECT_FRONTEND" || exit 1
    setsid nohup npm run preview -- --port "$FRONTEND_PORT" --host 0.0.0.0 >> "$LOG_FILE" 2>&1 </dev/null &
    echo $! > "$PID_FILE"
    sleep 2
    echo "[Frontend] 预览模式，访问: http://0.0.0.0:${FRONTEND_PORT}/"
    ;;
  *)
    echo "[Frontend] 未知 SERVE_MODE=$SERVE_MODE（nginx|dev|preview）" >&2
    exit 1
    ;;
esac
