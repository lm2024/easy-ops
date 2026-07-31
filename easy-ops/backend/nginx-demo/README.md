# Nginx 流量监控 — Docker 演示环境

## 一键启动

```bash
# 1. 构建前端并同步到本目录 html/
cd easy-ops/backend/nginx-demo
./build-frontend.sh
# 若 vue-tsc 报错，可在 frontend 目录执行: npx vite build

# 2. 确保 Server 在宿主机 8081 运行（用户自行启动）

# 3. 启动 Nginx + Agent-1（共享 access.log 卷）
cd ../
docker-compose up -d nginx-demo agent-1

# 4. 首次或更新 Agent jar 后同步持久化卷
docker run --rm \
  -v backend_agent1-data:/app/data \
  -v $(pwd)/agent/docker/easy-ops-agent-1.0.0-SNAPSHOT.jar:/tmp/agent.jar:ro \
  eclipse-temurin:8-jdk cp /tmp/agent.jar /app/data/agent.jar
docker restart ops-agent-1
```

## 访问地址

| 服务 | 地址 |
|------|------|
| **前端（Nginx 托管）** | http://localhost:3080 |
| EasyOps Server API | http://localhost:8081/api |
| Agent-1 | http://localhost:2123/api |

登录：`admin / Admin123!`

## 配置日志源

1. 打开 http://localhost:3080 → 登录
2. 左侧 **监控告警 → Nginx 流量监控 → 日志源配置**
3. 新增：
   - 节点：`agent-1`
   - 路径：`/var/log/nginx/access.log`
   - 启用：开

## 验证

```bash
# 制造访问流量
for i in $(seq 1 20); do
  curl -s -o /dev/null http://localhost:3080/
  curl -s -o /dev/null http://localhost:3080/api/auth/captcha
done

# 等待约 60 秒（分钟桶上报），刷新「排名分析」页面
```

## 常见问题

### 页面全是 0

1. **日志源未配置**：IDEA 启动的 Server 数据目录是仓库根 `easy-ops/data/`（看启动日志 `H2 数据源` / `server.path`），不是 `backend/server/data/`。必须在页面「日志源配置」为 `agent-1` 添加 `/var/log/nginx/access.log`。
2. **Agent 拉不到源**：`curl -s http://127.0.0.1:8081/api/nginx-traffic/agent/sources -H 'X-Token: agent-token-1'` 应返回非空 `data`。
3. **access.log 是软链**：旧版镜像把日志链到 stdout，Agent 无法 tail。需用当前 `docker-entrypoint.sh` 重建 `nginx-demo` 并 `docker-compose up -d --force-recreate nginx-demo agent-1`。
4. **统计 API 字段为 null**：重启 Server 加载最新 `NginxTrafficService`（H2 大写字段 normalize 修复）。

### 快速自检

```bash
# Agent 是否识别日志源
curl -s http://127.0.0.1:8081/api/nginx-traffic/agent/sources -H 'X-Token: agent-token-1'

# 库内是否有分钟统计（无需登录）
curl -s 'http://127.0.0.1:8081/api/db/table/nginx_minute_stat/data?pageSize=5'
```

## 架构

```
浏览器 → ops-nginx-demo:3080 → access.log (共享卷)
                              ↓
                    ops-agent-1 增量 tail
                              ↓
                    EasyOps Server 分钟统计表
                              ↓
                    前端 Nginx 流量监控页
```
