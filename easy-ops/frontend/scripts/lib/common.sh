#!/bin/sh
# 前端脚本公共库（仅前端机器使用）

set -u

: "${EASYOPS_SCRIPT_ROOT:?请先设置 EASYOPS_SCRIPT_ROOT}"
_SCRIPT_DIR="$EASYOPS_SCRIPT_ROOT"
_PROJECT_FRONTEND="$(cd "$_SCRIPT_DIR/.." && pwd)"

if [ -f "$_SCRIPT_DIR/easyops.env" ]; then
  # shellcheck disable=SC1091
  . "$_SCRIPT_DIR/easyops.env"
else
  echo "[Frontend] 未找到 $_SCRIPT_DIR/easyops.env" >&2
  exit 1
fi

if [ -z "${INSTALL_DIR:-}" ]; then
  INSTALL_DIR="$_SCRIPT_DIR"
fi

resolve_paths() {
  DIST_DIR="$INSTALL_DIR/dist"
  PID_FILE="$INSTALL_DIR/frontend.pid"
  LOG_FILE="$INSTALL_DIR/frontend.log"
  NGINX_CONF="$INSTALL_DIR/nginx.conf"

  if [ ! -d "$DIST_DIR" ] && [ -d "$_PROJECT_FRONTEND/nginx/dist" ]; then
    DIST_DIR="$_PROJECT_FRONTEND/nginx/dist"
  fi
}

print_paths() {
  echo "========== EasyOps Frontend =========="
  echo "INSTALL_DIR    = $INSTALL_DIR"
  echo "DIST_DIR       = $DIST_DIR"
  echo "FRONTEND_PORT  = $FRONTEND_PORT"
  echo "SERVER_API_URL = $SERVER_API_URL"
  echo "SERVE_MODE     = $SERVE_MODE"
  echo "======================================"
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
  echo "[Frontend] 停止 PID=$1 ..."
  kill "$1" 2>/dev/null || true
  sleep 2
  kill -0 "$1" 2>/dev/null && kill -9 "$1" 2>/dev/null || true
}

resolve_nginx() {
  if [ -n "${NGINX_BIN:-}" ]; then
    NGINX="$NGINX_BIN"
  else
    NGINX="$(command -v nginx 2>/dev/null || true)"
  fi
}

write_nginx_conf() {
  cat > "$NGINX_CONF" <<EOF
# EasyOps 前端 Nginx 配置（由 start.sh 自动生成，勿手改；改 easyops.env 后 restart）
worker_processes 1;
error_log $INSTALL_DIR/nginx-error.log;
pid $INSTALL_DIR/nginx.pid;

events { worker_connections 1024; }

http {
    default_type  application/octet-stream;
    sendfile      on;
    keepalive_timeout 65;
    types {
        text/html                             html htm;
        text/css                              css;
        application/javascript                js;
        application/json                      json;
        image/png                             png;
        image/jpeg                            jpg jpeg;
        image/svg+xml                         svg svgz;
        font/woff                             woff;
        font/woff2                            woff2;
    }

    server {
        listen $FRONTEND_PORT;
        server_name _;
        root $DIST_DIR;
        index index.html;

        location / {
            try_files \$uri \$uri/ /index.html;
        }

        location /api/ {
            proxy_pass $SERVER_API_URL/api/;
            proxy_http_version 1.1;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        }

        location /ws/ {
            proxy_pass $SERVER_API_URL/ws/;
            proxy_http_version 1.1;
            proxy_set_header Upgrade \$http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host \$host;
        }
    }
}
EOF
}
