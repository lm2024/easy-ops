#!/bin/bash
set -e
cd "$(dirname "$0")"
JAR=$(ls *.jar 2>/dev/null | head -1)
[ -z "$JAR" ] && echo "ERROR: 未找到 jar" && exit 1
JAR_PATH="$(pwd)/$JAR"
PID=$(pgrep -f "$JAR_PATH" 2>/dev/null || true)
[ -n "$PID" ] && echo "$JAR 已在运行 PID=$PID" && exit 0
nohup java -Xms512m -Xmx1024m -XX:+UseG1GC -XX:-HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -jar "$JAR_PATH" > /dev/null 2>&1 &
echo "Started $JAR PID=$!"
