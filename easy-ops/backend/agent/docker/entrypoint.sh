#!/bin/sh
# Agent Docker 入口脚本
# 将 jar 复制到 /app/data/ (挂载的 volume)，保证升级后重启不丢失
# 由 tini 作为 PID 1 拉起；agent 以循环方式运行，
# 升级/崩溃退出后自动重启，且被 detach 的应用进程由 tini 接管（不被杀、僵尸被回收）。

PERSISTENT_JAR="/app/data/agent.jar"
IMAGE_JAR="/app/agent.jar"

# 每次启动都用镜像中的jar覆盖持久化目录，确保代码最新
cp -f "$IMAGE_JAR" "$PERSISTENT_JAR"
echo "[entrypoint] 更新 agent.jar 到 $PERSISTENT_JAR"

while true; do
  java \
    -Dagent.jar-path="$PERSISTENT_JAR" \
    -Dagent.data-path="/app/data" \
    -jar "$PERSISTENT_JAR"
  echo "[entrypoint] agent 进程退出，3s 后由 tini 重启..."
  sleep 3
done
