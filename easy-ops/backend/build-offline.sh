#!/bin/bash
# EasyOps 后端「内网」离线构建脚本（无需联网）
# 仅使用工程内私有仓库 backend/local-repo，对应前端的 node_modules 私有化思路
# 前提: 内网已安装 JDK 8 与 Maven 3.9+
# 用法: 在 backend/ 目录下执行  ./build-offline.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -d "$SCRIPT_DIR/local-repo" ]; then
  echo "未找到 $SCRIPT_DIR/local-repo"
  echo "本机导出时漏了私有仓库，请确认已把 backend/local-repo 一并拷贝到内网。"
  exit 1
fi

echo ">>> 使用工程内私有仓库 backend/local-repo 离线重新打包 (mvn -o) ..."
mvn -o -Dmaven.repo.local="$SCRIPT_DIR/local-repo" clean package -DskipTests

echo ""
echo ">>> 构建完成:"
echo "    Server: $SCRIPT_DIR/server/target/ops-platform-server-1.0.0-SNAPSHOT.jar"
echo "    Agent : $SCRIPT_DIR/agent/target/easy-ops-agent-1.0.0-SNAPSHOT.jar"
echo ">>> 启动: ./start-prod.sh"
