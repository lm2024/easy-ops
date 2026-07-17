#!/bin/bash
# EasyOps 后端「内网」启动脚本（Linux / JDK 8，不依赖 macOS 专属路径）
# 用法: 在 backend/ 目录下执行  ./start-prod.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/server"

JAR="$SCRIPT_DIR/server/target/ops-platform-server-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
  echo "未找到 $JAR"
  echo "请先执行 ./build-offline.sh 离线构建（需工程内 backend/local-repo 与内网已装 Maven 3.9+ / JDK 8）"
  exit 1
fi

# 必须连同 server/data/ 一起拷贝，否则 H2 数据会丢失/重新初始化
# JWT_SECRET 仅用于启动校验（SecurityConfig），实际 token 由 SecureRandom 生成，非标准 JWT
export JWT_SECRET="${JWT_SECRET:-easy-ops-jwt-secret-key-2024-secure-random}"

echo "启动后端服务 (端口 8081) ..."
nohup java -jar "$JAR" > "$SCRIPT_DIR/server/backend.log" 2>&1 &
PID=$!
echo "PID: $PID"
echo "日志: $SCRIPT_DIR/server/backend.log"
echo "API:  http://localhost:8081/api/"
echo "停止: kill $PID"
