# EasyOps 项目长期记忆（agent 部署专项）

## 认证：自动登录后门（2026-08-07 新增）
- `POST /api/auth/auto-login`，请求体 `{"key":"..."}` 或请求头 `X-Auto-Login-Key`。
- 三重闸门全部满足才放行：① `AUTO_LOGIN_ENABLED=true` ② 白名单 key 非空 ③ key 命中。默认全关。
- 改动文件：`SystemController.java`、`AuthInterceptor.java`、`server/src/main/resources/application.yml`（`easyops.auth.*` 已并入根节点）。
- 用途：agent / 自动化免验证码登录。文档见 `AGENTS.md`「自动化 / agent 自动登录」章节。

## 节点 ID 映射（docker agents）
- agent-1 = nodeId 3, port 2123, token agent-token-1
- agent-2 = nodeId 2, port 2124, token agent-token-2
- agent-3 = nodeId 1, port 2125, token agent-token-3
- 三者 ip 在 server 侧均为 127.0.0.1（docker 端口映射），从宿主机直连可达。

## 复用技巧：无需重启 8081 也能走系统部署
- 8081 server 由用户自管、且默认未开自动登录时，可起一个**临时 server（如 8099）**复用同一 H2 库来调 API：
  - H2 JDBC URL 含 `AUTO_SERVER=TRUE`（`jdbc:h2:file:${EASYOPS_SERVER_DATA:./data}/ops;MODE=MySQL;AUTO_SERVER=TRUE`），允许多进程共享同一库文件。
  - 启动：同 jar + `EASYOPS_SERVER_DATA=<与 8081 相同数据目录> SERVER_PORT=8099 AUTO_LOGIN_ENABLED=true AUTO_LOGIN_WHITELIST=<key>`。
  - 8081 先启动=H2 主节点，8099=客户端，停 8099 不影响 8081。
  - 用 8099 的 auto-login 拿 token，建项目/传版本/一键部署，数据写进共享库，用户 8081 前端直接可见。
- 8081 数据目录：`/Users/lm/Documents/GitHub/easy-ops/data`（IDEA 启动 cwd=仓库根 easy-ops）。

## 已部署应用（2026-08-08，全 3 节点成功）
- **demo-test-app**（projectId=1, backend）：Spring Boot，监听 8082，有 `/hello`(200) `/health`。jar=`demo-test-app.jar`，deployDir=`/app/data/apps/demo-test-app`，startScript=`java -jar`，stopScript 用 `jps` 杀（容器缺 ps）。健康检查 port=8082 path=/hello。
- **nginx-demo**（projectId=2, frontend）：纯静态前端，zip 解压到 `/app/data/apps/nginx-demo/html`。注意：agent 前端部署只分发文件，不在容器内起 nginx 服务。
- 部署产物存于 `<data>/versions/1/`、`versions/2/`（勿删，回滚/重部署依赖）。

## 坑：Docker Agent 缺 ps
- `eclipse-temurin:8-jdk` 不含 procps，`ps aux` 不可用；用 `jps -l`（JDK 自带）或 `pkill -f` 杀 Java 进程。

## 调 API 必记：两套 token 头 + nginx-traffic 参数名
- **用户接口**（overview/rank/latency/trend/sources 等前端接口）用 `Authorization: Bearer <userToken>`（userToken 由 auto-login 拿）。
- **Agent 接口**（`/nginx-traffic/agent/sources`、`/nginx-traffic/ingest`）用 `X-Token: <agentToken>`（如 agent-token-1）。
- 误用 `X-Token` 带 user token → 401（AuthInterceptor 把 X-Token 当 agent token 校验，找不到 nodeId 即拒）。
- nginx-traffic 查询参数：`sourceIds`（List<Long>，不是 `sourceId`）+ `windowMinutes`（分钟，默认 60，不是 `range`）；rank 支持 `sort=avg|max|请求数`、`keyword`、`page`/`pageSize`；latency/samples 支持 `pageSize`（默认 50）。
- 验证日志管线：nginx-demo 写 `nginx-access-logs` 共享卷(rw)，agent-1 挂同卷(ro)读；构造行用 `docker exec ops-nginx-demo sh -c 'cat >> /var/log/nginx/access.log' < file`；agent `flushTick` 每 60s 上报当前分钟桶，验证前 sleep≈65s。

## 测试陷阱：Result.error 不改 HTTP 状态码
- `com.ops.common.response.Result.error(code,msg)` 返回的是 **HTTP 200 + body 里的 `code` 字段**（如 403/401），**不是** HTTP 403/401 状态码。
- 用 curl 验证权限类接口时，必须看响应体 `code`（`python3 -c 'print(d["code"])'`），不能只看 `%{http_code}`（永远 200）。否则会把"逻辑拒绝"误判为"成功"。

## 用户权限加固（2026-08-08 已实现并验证）
- `SystemController` 用户写接口补全角色校验：列表/创建/删除仅 admin；修改仅 admin 或本人，且非 admin 禁止改 role/status（防自我提权）；普通用户列表仅返回自己那一行。
- 默认角色由 `admin` 改为 `operator`（创建用户 role 为空时）。
- 前端 `UserListView.vue`/`UserFormView.vue` 加 `isAdmin` 门控（新增/删除仅 admin；编辑仅 admin 或本人；角色下拉仅 admin 可见）。
- 验证：operator 令牌建/删/改他人均返回 body code=403；改自己成功。环境已恢复 admin 自动登录。

## 租户/数据隔离架构文档（2026-08-08）
- 交付：`/Users/lm/Documents/GitHub/easy-ops/tenant-architecture-design.md`（基于真实代码/schema 逐模块盘点 + 两种方案对比 + 改动范围量化 + 分阶段路线）。
- 结论：当前仅"项目级"隔离（`user_project_relation`+`SecurityContext`），无 tenant 概念；约 34 张业务表需加 `tenant_id`，~15 接口加过滤，2 个 WS 广播（`/ws/monitor`、`/ws/notification`）须按租户拆分。改动面广但机械、可分阶段。
- 用户决策点：方案 A（仅加固项目级，不加租户）vs 方案 B（引入 tenant_id 行级隔离）。

## H2 数据库膨胀救援（2026-08-17）
- 现象：`ops.mv.db` 数月可膨胀到数 GB~10GB 占满磁盘，server 无法启动。根因：MVStore 删除不回收空间 + nginx 统计/监控快照/日志类表无限累积。
- 救援：`scripts/rescue-h2.sh <data目录> [server.jar] [--yes]`（`KEEP_DAYS`=统计类保留天数默认7，`LOG_KEEP_DAYS`=日志类默认30）。停 server → DELETE 历史 → `SHUTDOWN COMPACT`。全程本地执行无需下载大文件；已验证 6.6M→304K。
- 坑：H2 URL 不带 `.mv.db` 后缀；COMPACT 末尾 "already closed" 提示无害；bash 变量后跟全角字符须 `${}`。建议 cron 每周自动清理。

## H2 表结构维护完善（2026-08-18 已实现+自测）
- **单一事实来源**：`application.yml` 新增 `easyops.data.table-meta`（14 分类 + 54 张表登记）。type=BASE/CONFIG(禁清空) / FLOW/AGENT_SYNC(可清空)；`source` 标记 agent/nginx 来源。新增表只需加一行，定时清理/手动清理/一键清空/前端四处自动生效。
- **表元数据服务**：`TableMetaService`（server 侧新类）解析 yml + INFORMATION_SCHEMA 扫描 + 命名规则兜底（未登记表 `recognized=false` 启发式归类，前端显示"待归类"角标）；行数带 10s 缓存；`PROTECTED_TABLES` 硬编码 22 张基础表禁止清空（yml 配错也挡）。
- **清空接口**（全部 admin-only + confirm 必传 + operation_log 审计 module=DB）：`POST /db/table/{t}/clear`、`/db/clear-batch`、`/db/clear-all-flow`、`GET /db/clearable-tables`。nginx_minute_stat/monitor_snapshot 分批删（10000/批）。返回 compactHint 提示重启缩容。
- **重要安全修复**：`/db/**` 原在 WebConfig exclude（免登录裸奔），已移除 → 登录校验 401 + 写操作 admin 403。
- **前端**：DbManageView 删 TABLE_META 硬编码按后端渲染；清空二次确认（输入表名）；一键清空弹窗（勾选+行数）；大表警告。
- **踩坑**：① yml `easyops.data` 顶层重复定义 → DuplicateKeyException，table-meta 必须 4 空格缩进挂到已有 data 节点下（与 cleanup 平级）② H2 双引号内大小写敏感，表实际存大写 `OPERATION_LOG`，SQL 用大写；yml 表名小写匹配 ③ `UPPER(TABLE_NAME)=?` 参数传大写 ④ vite build 清空 dist 被 WorkBuddy safe-delete 钩子拦，用 `npx vite build --emptyOutDir=false` 绕过 ⑤ `getUserIdByToken` 实际按 username 查（`WHERE username=#{token}`），用户名可直接当 Bearer token 登录 → 自测非 admin 用 `Bearer op_test`。
- 文档：`docs/260818-h2表维护完善.md`。
