#!/bin/bash
# 构建前端并同步到 nginx-demo/html，供 Docker Nginx 使用
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/../../frontend" && pwd)"
HTML_DIR="$SCRIPT_DIR/html"

echo ">>> 构建前端..."
cd "$FRONTEND_DIR"
npm run build

echo ">>> 同步 dist 到 nginx-demo/html ..."
rm -rf "$HTML_DIR"
mkdir -p "$HTML_DIR"
cp -r "$FRONTEND_DIR/nginx/dist/"* "$HTML_DIR/"

echo ">>> 完成: $HTML_DIR"
