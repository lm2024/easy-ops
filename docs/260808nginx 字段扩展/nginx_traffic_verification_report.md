# Nginx 流量监控 — 端到端验证报告

> 验证日期：2026-08-08 ｜ 环境：本机 Docker 真实管线（非 Mock）
> 验证方式：起真实 nginx 容器 → 共享卷 → agent-1 增量 tail → 上报 server → 查 API/聚合，覆盖全部功能。

## 1. 验证环境搭建（真实链路）

| 组件 | 状态 | 说明 |
|------|------|------|
| `ops-nginx-demo` (:3080) | ✅ 运行中 | 镜像从 `easyops/nginx-demo/` 构建，写 access.log 到共享卷 `nginx-access-logs` |
| `ops-agent-1` (:2123) | ✅ 重建运行 | 重新打镜像（旧镜像缺 `NginxAccessCollector`），新增挂载 `nginx-access-logs:/var/log/nginx:ro` + 复用 `agent1-data` 卷（nodeId=3） |
| server (:8081) | ✅ 运行中 | **重新打包 jar**（含白名单/告警/方法维度改动）并重启，替换原 IDEA 实例；开 auto-login 便于脚本验证 |
| 日志源 id=1 | ✅ 已建 | nodeId=3(agent-1)，路径 `/var/log/nginx/access.log`，慢阈值 2s，启用 |

数据链路：`nginx 写 access.log` → `nginx-access-logs 卷` → `agent-1 增量 tail/分钟聚合` → `POST /nginx-traffic/ingest` → `server 预聚合入库`。

## 2. 测试流量

- 真实流量：curl 打 `:3080/`、`/api/auth/captcha`（nginx 写真实访问行）。
- 构造日志 100 行（追加进共享卷），确定性覆盖：白名单候选 IP `203.0.113.50`、白名单候选 URI `/api/health`、激增 IP `198.51.100.23`(40 次)、多方法(POST/PUT/DELETE)、慢请求(5 条 >2s)、4xx/5xx。
- 入库总量实测 **131 行**（30 curl + 100 构造 + 1 原始），无重复计数。

## 3. 功能验证结果

### 3.1 实时概览 ✅
`totalRequests=131`，`slowCount=5`，`status4xx=7`，`status5xx=3`，qps/峰值正确。

### 3.2 排名分析 ✅（含新增「方法维度」）
- **rank/ip**：`198.51.100.23`=69（含 5 慢/7 4xx/3 5xx）、`192.168.65.1`=43、`203.0.113.50`=14、`203.0.113.99`=5 —— 与构造完全一致。
- **rank/uri**：`/api/products`=40、`/`=28、`/api/auth/captcha`=15、`/api/health`=15、`/api/login`=8、`/api/missing`=7(4xx)、`/api/orders/1`=6、`/api/slow-report`=5(慢)、`/api/cart/9`=4、`/api/boom`=3(5xx)。
- **rank/method（新增维度）**：GET=113、POST=8、PUT=6、DELETE=4，慢/异常归类正确。
- **rank/slow**：`/api/slow-report` requestCount=5、slowCount=5、maxRt=8100ms、avgRt=5160ms（5.5/6.2/8.1/3.7/2.3 均值）✅。

### 3.3 日志源配置 ✅
建源后 agent 30s 内拾取并开始上报（`lastReportTime` 持续更新），页面「全部日志源」现已能看到该数据源与实时上报时间。

### 3.4 白名单（IP + URI，查询侧即时生效）✅
加入白名单：`IP=203.0.113.50(EXACT)` + `URI=/api/health(EXACT)` 后复查：
- 概览 `totalRequests` 由 **131 → 112**（精确剔除 19 行：`.50` 的 14 行 ∪ `/api/health` 的 15 行）。
- rank/ip：`203.0.113.50`、`.99` 均消失（后者因 URI 白名单被剔除）。
- rank/uri：`/api/health`、`/api/cart/9` 消失（cart 流量全部来自被禁 IP）。
- 慢接口/方法维度不受影响（未被白名单命中）。

### 3.5 告警（5 类规则全部触发）✅
启用低阈值规则后，调度 `NginxTrafficAlarmScheduler`(每 60s) 评估并写入通知（`/api/notifications`）：
- `单IP访问过频`(WARNING) — 198.51.100.23=69 ≥ 30
- `接口访问过频`(WARNING) — /api/products=40 ≥ 40
- `4xx 错误过多`(WARNING) — 7 ≥ 5
- `5xx 错误过多`(CRITICAL，需确认) — 3 ≥ 1
- `慢请求过多`(WARNING) — 5 ≥ 3

告警评估同样走白名单参数，避免误报。

## 4. 收尾与注意事项

- 测试用的**低阈值告警已改回 `enabled=0`**，避免真实流量误报；如需演示可在「日志源配置 → 告警规则」重新启用。
- 白名单规则（测试 IP/URI）与数据源 id=1 保留，可在前端 UI 直接查看/清理。
- server 当前由本次重建的 jar 运行在 8081（非 IDEA 实例）；如要切回 IDEA，正常重启即可（数据在同一 H2 库）。
- 前端 `NginxTrafficView.vue`（白名单 Tab、趋势图点击跳排名、方法维度卡片）改动已在 dev vite server 生效，打开 Nginx 流量监控页即可看到实时数据。

## 结论
白名单、告警、慢接口、实时概览、排名分析（含新增方法维度）**均已通过真实 Docker 管线验证，功能与数据均正确**。此前确实只交付了代码未验证，本次已补齐。
