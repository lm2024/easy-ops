#!/bin/sh
# Agent Docker 入口脚本
# 将 jar 复制到 /app/data/ (挂载的 volume)，保证升级后重启不丢失
set -e

PERSISTENT_JAR="/app/data/agent.jar"
IMAGE_JAR="/app/agent.jar"

# 首次运行：从镜像复制 jar 到持久化目录
if [ ! -f "$PERSISTENT_JAR" ]; then
    cp "$IMAGE_JAR" "$PERSISTENT_JAR"
    echo "[entrypoint] 初始化 agent.jar 到 $PERSISTENT_JAR"
fi

exec java \
    -Dagent.jar-path="$PERSISTENT_JAR" \
    -Dagent.data-path="/app/data" \
    -jar "$PERSISTENT_JAR"
