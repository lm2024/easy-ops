#!/bin/bash
cd "$(dirname "$0")"
JAR=$(ls *.jar 2>/dev/null | head -1)
[ -z "$JAR" ] && echo "ERROR: 未找到 jar" && exit 1
JAR_PATH="$(pwd)/$JAR"
PIDS=$(pgrep -f "$JAR_PATH" 2>/dev/null || true)
[ -z "$PIDS" ] && echo "$JAR 未在运行" && exit 0
for p in $PIDS; do kill "$p" 2>/dev/null; done
sleep 2
for p in $PIDS; do kill -0 "$p" 2>/dev/null && kill -9 "$p" 2>/dev/null; done
echo "Stopped $JAR"
