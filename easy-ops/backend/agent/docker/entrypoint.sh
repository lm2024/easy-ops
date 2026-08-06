#!/bin/sh
# Agent Docker 入口脚本
# 将 jar 复制到 /app/data/ (挂载的 volume)，保证升级后重启不丢失
set -e

PERSISTENT_JAR="/app/data/agent.jar"
IMAGE_JAR="/app/agent.jar"

# 每次启动都用镜像中的jar覆盖持久化目录，确保代码最新
cp -f "$IMAGE_JAR" "$PERSISTENT_JAR"
echo "[entrypoint] 更新 agent.jar 到 $PERSISTENT_JAR"

exec java \
    -Dagent.jar-path="$PERSISTENT_JAR" \
    -Dagent.data-path="/app/data" \
    -jar "$PERSISTENT_JAR"
