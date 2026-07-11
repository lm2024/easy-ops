#!/bin/sh
# 在有 Node.js 的构建机上执行（可联网），打出 dist 后拷到前端机器
set -e
EASYOPS_SCRIPT_ROOT="$(cd "$(dirname "$0")" && pwd)"
export EASYOPS_SCRIPT_ROOT
# shellcheck disable=SC1091
. "$EASYOPS_SCRIPT_ROOT/lib/common.sh"
resolve_paths

if ! command -v npm >/dev/null 2>&1; then
  echo "[Frontend] 需要 Node.js 18+ 和 npm" >&2
  exit 1
fi

cd "$_PROJECT_FRONTEND"
[ ! -d node_modules ] && npm install

echo "[Frontend] 打包中..."
npm run build

mkdir -p "$INSTALL_DIR/dist"
rm -rf "$INSTALL_DIR/dist"
cp -r nginx/dist "$INSTALL_DIR/dist"

echo "[Frontend] 打包完成: $INSTALL_DIR/dist"
echo "[Frontend] 请将以下内容拷到前端机器:"
echo "  - dist/"
echo "  - scripts/easyops.env scripts/start.sh scripts/stop.sh scripts/restart.sh scripts/lib/"
