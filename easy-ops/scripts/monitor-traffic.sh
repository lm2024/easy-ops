#!/bin/bash
# 模拟访问 demo-test-app，产生监控数据
# demo-test-app 运行在 Docker 容器内，端口 8082

echo "=== 开始模拟访问 demo-test-app ==="
echo "Agent 1 (ops-agent-1): demo-test-app:8082"
echo "Agent 2 (ops-agent-2): demo-test-app:8082"
echo "Agent 3 (ops-agent-3): demo-test-app:8082"
echo "按 Ctrl+C 停止"
echo ""

# 计数器
count=0

while true; do
  count=$((count + 1))
  echo "[$(date '+%H:%M:%S')] 第 $count 轮访问..."

  # Agent 1
  echo -n "  Agent 1: "
  docker exec ops-agent-1 curl -s http://localhost:8082/hello > /dev/null 2>&1 && echo -n "hello " || echo -n "hello(fail) "
  docker exec ops-agent-1 curl -s http://localhost:8082/health > /dev/null 2>&1 && echo "health OK" || echo "health FAIL"

  # Agent 2
  echo -n "  Agent 2: "
  docker exec ops-agent-2 curl -s http://localhost:8082/hello > /dev/null 2>&1 && echo -n "hello " || echo -n "hello(fail) "
  docker exec ops-agent-2 curl -s http://localhost:8082/health > /dev/null 2>&1 && echo "health OK" || echo "health FAIL"

  # Agent 3
  echo -n "  Agent 3: "
  docker exec ops-agent-3 curl -s http://localhost:8082/hello > /dev/null 2>&1 && echo -n "hello " || echo -n "hello(fail) "
  docker exec ops-agent-3 curl -s http://localhost:8082/health > /dev/null 2>&1 && echo "health OK" || echo "health FAIL"

  # 随机等待 3-6 秒
  wait_time=$(( (RANDOM % 4) + 3 ))
  echo "  等待 ${wait_time}秒..."
  sleep $wait_time
done
