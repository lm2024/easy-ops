#!/bin/bash
# 后端服务启动脚本 - 固定工作目录确保数据库路径一致
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/server/target/ops-platform-server-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
  echo "编译后端..."
  JAVA_HOME="$HOME/.jdk8/Contents/Home" mvn clean package -DskipTests -q -f "$SCRIPT_DIR/pom.xml"
fi

echo "启动后端服务 (端口 8081)..."
cd "$SCRIPT_DIR/server"
export JWT_SECRET="${JWT_SECRET:-easy-ops-jwt-secret-key-2024-secure-random}"
JAVA_HOME="$HOME/.jdk8/Contents/Home" nohup "$HOME/.jdk8/Contents/Home/bin/java" \
  -jar "$JAR" \
  > "$SCRIPT_DIR/server/backend.log" 2>&1 &

PID=$!
echo "PID: $PID"
echo "日志: $SCRIPT_DIR/server/backend.log"
echo "API:  http://localhost:8081/api/"
echo ""
echo "停止: kill $PID"
