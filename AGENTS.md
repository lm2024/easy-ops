<!-- CODEGRAPH_START -->
## CodeGraph

在 `.codegraph/` 索引过的仓库中，优先用 CodeGraph 而不是 grep/find：
- **MCP**: `codegraph_explore`（一问多符号+源码+调用链）、`codegraph_node`（单符号或读文件）
- **Shell**: `codegraph explore` / `codegraph node`
<!-- CODEGRAPH_END -->

---

你负责docker agent 打包发布管理等;只要是docker中的都归你管
server,前端都归我管需要重启就告诉我
遇到问题要刨根问题找到潜在的根本原因并解决问题
不能还原代码. 或者用偏方解决问题,要通过架构.设计来从不同的角度给出解决方案

# AGENTS.md — EasyOps AI 提示词

## 项目简介

分布式运维平台：**Server (8081/api) ↔ Agent (2123/api)**，WebSocket 推送终端/部署/监控。

代码根 `easy-ops/`（这个是仓库根里的子目录，注意多一层嵌套）。

## 技术栈

| 层 | 技术 |
|----|------|
| Server | Java 8, Spring Boot 2.7, MyBatis, H2 (MySQL 模式), JWT(仅校验), Quartz |
| Agent | Java 8, Spring Boot 2.7, RestTemplate, WebSocket Client, OSHI |
| Common | 共享 model/enum/Result/异常 |
| 前端 | Vue 3 + Vite 5 + TS + Ant Design Vue 4 + Pinia + ECharts + xterm + Monaco |

构建：`cd easy-ops/backend && mvn package -DskipTests`；离线：`./build-offline.sh`。

默认管理员 `admin / Admin123!`。

## 部署（三机不共享）

每台只拷自己的 jar + 脚本，只改本机 `easyops.env`，**YAML 不动**（脚本注入路径）。

| 机器 | 脚本目录 | 必改项 |
|------|----------|--------|
| Server | `backend/server/` | `JWT_SECRET`, `AGENT_DATA_PATH`(=Agent 机 data 目录) |
| Agent | `backend/agent/` | `AGENT_SERVER_URL`, `AGENT_NODE_NAME`, `AGENT_TOKEN` |
| 前端 | `frontend/scripts/` | `SERVER_API_URL`, `SERVE_MODE=nginx` |

命令：`./start.sh` / `./stop.sh`。前端另需 `build.sh`（有网机器打 dist）。

## 部署流程

`DeployController.publish` (POST `/deploy`)：停止旧进程 → 传 jar → 启动 → 健康检查（可配置，非固定 8080/hello）→ 失败回滚。定时部署走 `DeployScheduler` + `DistributedLock`。

Agent 版本路径：`{agent-data-path}/versions/{projectId}/{version}/`。

### 前端部署契约（2026-08-18 定稿，改动前必读）

**固定规则：解压目录名 = zip 文件名去掉扩展名，禁止自定义"解压后目录名"。**

- 上传 `xxx.zip`（版本管理的 jarName）→ 解压到 `{deployDir}/xxx/`（部署目录下的同名文件夹）。
- zip 本体传到 `{deployDir}/versions/{版本名}/xxx.zip`（版本存档，回滚/多版本保留）。
- 重部署/清理**只处理**：`{deployDir}/xxx/`（同名文件夹）与本次上传的 zip；**部署目录内其他文件一律保留**，绝不做整目录 `rm -rf`。
- 备份/还原也仅针对同名文件夹：`{deployDir}/xxx.backup-{时间戳}`，失败自动还原；首次部署失败（原本无同名目录）会清理产生的空目录。
- 旧实现（frontendDirName / frontendDeployDir 决定目标目录、整目录清空）已废弃，`frontend_dir_name`/`frontend_deploy_dir` 列保留但部署不再使用。
- 安全校验链（`DeployController` 前端分支）：文件名须为单段（无路径分隔符/`..`）→ 目标不得是系统目录 → 不得落在 `versions` 存档目录内。
- Agent 解压校验：zip 未解压出任何文件（空包/伪 zip，`ZipInputStream` 对垃圾内容不抛异常）→ 直接报错，杜绝"旧版本被删后解压出空目录"的静默成功。
- 前端项目必须配置部署目录 `deployDir`，否则部署报错终止。

## 关键配置

敏感项走环境变量：`JWT_SECRET`、`AI_API_KEY`、`AGENT_TOKEN`。

| 配置项 | 说明 |
|--------|------|
| `server.path` | Server 数据根（H2、版本包、日志） |
| `agent.data-path` | Agent 数据根 |
| `ops.global.agent-data-path` | Server 计算 Agent 路径用 |
| `cors.allowed-origins` | 默认 `http://localhost:3000,http://localhost:5173` |
| `AGENT_TOKEN` | 留空自动生成 |

## Agent 自升级

| 环境 | 方式 |
|------|------|
| Docker | 换 jar → `exit` → 容器 restart |
| 裸机 | 外置脚本等旧进程退出 → `setsid` 拉新 → 失败回滚 |

日志 `{data}/logs/upgrade-restart.log`。生产 `AGENT_RESTART_MODE=shell` + 脚本路径。

## API 一览

### Server 控制器映射（按前缀）

| 前缀 | 功能 |
|------|------|
| `/auth` | 登录/验证码 (带 captcha)；`/auth/auto-login` 白名单免验证码自动登录（见下节） |
| `/nodes` | 节点管理/心跳/Agent 升级 |
| `/projects` `/versions` `/deploy` | 项目/版本/部署 |
| `/files` | 文件浏览/下载/传输 |
| `/logs` `/log-mgmt` | 日志查看/聚合搜索 |
| `/process` | Server 侧启停代理 |
| `/agent` | 透传 Agent 接口 |
| `/system` | 系统路径配置 |
| `/db` | H2 数据表管理 |
| `/monitor` | 监控 (含 `/app/*` 应用监控仪表盘) |
| `/alarms` | 告警记录 |
| `/ai` | AI 配置 (`/config`)、诊断 (`/diagnose`) |
| `/self-heal` | 自愈策略/事件/熔断 |
| `/notifications` | 通知/已读/确认 |
| `/config` | 配置文件管理/比对/分发/扫描 |
| `/kb` `/kb/collab` `/kb/permissions` `/kb/search` `/kb/share-links` `/kb/tags` `/kb/templates` | 知识库（文档/分类/评论/协作/权限/标签/模板/分享） |

### Agent 控制器

| 前缀 | 功能 |
|------|------|
| `/file` | 接收 jar、配置、日志文件 |
| `/process` | 项目进程启停 |
| `/shell` | Shell 执行 |
| `/sys` | 心跳(`/heartbeat`)、系统信息(`/info`) |
| `/system` | Agent 版本、自升级(`/upgrade`) |

### WebSocket

`/ws/console` `/ws/deploy` `/ws/monitor` `/ws/notification` `/ws/kb-collab`

## 自动化 / agent 自动登录（白名单免验证码）

调试或自动化（agent 走系统 API 做部署等）时，每次都要解验证码太反人类。提供了**白名单自动登录**：携带白名单 key 直接拿已登录 token，跳过账号密码与验证码。

- 接口：`POST /api/auth/auto-login`
- 入参（二选一）：请求体 `{"key":"<白名单key>"}` 或请求头 `X-Auto-Login-Key: <白名单key>`
- 返回：`{ token, username, role }`，与 `/auth/login` 结构一致，后续请求带 `Authorization: Bearer <token>`
- 默认**全部关闭**，等同接口不存在；必须由配置显式开启，且必须配置白名单 key，否则一律拒绝。

### 配置（三处，缺一不可）

| 配置项（环境变量） | 作用 | 默认 |
|-------------------|------|------|
| `AUTO_LOGIN_ENABLED=true` | 总开关 | `false`（关闭） |
| `AUTO_LOGIN_USERNAME=admin` | 自动登录后 impersonate 的账号 | `admin` |
| `AUTO_LOGIN_WHITELIST=key1,key2` | 逗号分隔的 key 白名单，命中其一即放行 | 空（拒绝） |

> 配置写在 `server/src/main/resources/application.yml` 的 `easyops.auth.*` 下，也支持同名环境变量覆盖。建议 `AUTO_LOGIN_WHITELIST` 用随机长字符串，仅内网/本机使用。

### 启用示例（本机调试）

在 `backend/server/scripts/start.sh` 或启动命令加：

```bash
export AUTO_LOGIN_ENABLED=true
export AUTO_LOGIN_WHITELIST=ops-auto-login-2026
```

重启 server 后，agent 一行拿到 token：

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/auto-login \
  -H 'Content-Type: application/json' \
  -d '{"key":"ops-auto-login-2026"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
echo "Bearer $TOKEN"
```

之后所有 API 直接带 `-H "Authorization: Bearer $TOKEN"` 即可，无需再碰验证码。

### 安全边界

- 开关关 / 白名单空 / key 不匹配 → 直接 403，外部无法利用。
- 仅跳过"人肉验证码+密码"，仍走同一套 token 生成与缓存，权限与正常登录完全一致（默认 admin 角色）。
- 仅用于内网调试与自动化，生产务必关闭或限制白名单 key。

## 数据库（H2，36 表）

核心表：`node_info` `project_info` `version_package` `deploy_record` `sys_user` `operation_log` `file_access_log` 等。详见 `schema.sql`。完整列表：

`node_info` `project_info` `version_package` `deploy_record` `alarm_record` `alarm_config` `sys_user` `operation_log` `file_access_log` `sys_config` `scheduler_lock` `user_project_relation` `project_config_file` `node_config_snapshot` `config_distribute_record` `project_log_profile` `project_health_probe` `monitor_snapshot` `ai_diagnosis_record` + `self_heal_policy` `self_heal_event` `notification_record` `user_notification_state` + 12 张 `kb_*` 表。

## Agent 额外组件

`AutoRestartDaemon`（进程守护）`HeartbeatDaemon` `FileCommander` `StartCommander` `StopCommander` `LogCommander` `ProcessStatusChecker` `ProcessMetricsHelper` `HttpHealthProber` `ShellCompletionService` `ConfigFileService` `LogFileService` `LogDiscoveryService`

## 编码规范

1. 单文件建议 ≤400 行（但 DeployController 等已超限，容错 500+）
2. 模块隔离，禁止跨模块依赖
3. Controller 异常走 `GlobalExceptionHandler`，统一 `Result<T>`
4. common ← server/agent，禁止反向
5. 前端全 TS，类型在 `frontend/src/types/index.ts`
6. 敏感配置走环境变量

## 已知坑

- **H2 路径跟 cwd 走**：必须用 `scripts/start.sh` 注入绝对路径
- **Server/Agent 路径要对齐**：`AGENT_DATA_PATH` (Server) = `agent.data-path` (Agent)
- **Token 非强制**：留空自动生成；两台 Agent 不能共用
- **Docker 心跳离线**：Java 8 连 host.docker.internal 可加 `-Djava.net.preferIPv4Stack=true`
- **健康检查可配置**：每项目独立 URL/方法/状态码/关键字，非固定 8080/hello
- **启动失败假象**：`ProcessController` 已`mkdir -p logs` + `setsid`
- **前端生产只需 Nginx + dist**：`vite build` outDir 为 `nginx/dist`
- **JDK 8 限制**：不用 `Map.of`/`Path.of` 等 Java 9+ API
- **Docker Agent 缺 `ps` 命令**：`eclipse-temurin:8-jdk` 不自带 `procps`，`ProcessStatusChecker.findPid()` 用 `ps aux | grep` 检测进程 PID 会静默失败。Dockerfile 需加 `RUN apt-get update && apt-get install -y --no-install-recommends procps && rm -rf /var/lib/apt/lists/*`，否则应用监控的 PID 和进程状态全部丢失
- **心跳进程状态误判**：`NodeController.saveMonitorSnapshot()` 原逻辑先无条件设 `processStatus=RUNNING`（仅因 Agent 在线），再检查 `processes` 列表；若列表为空（未找到应用进程），状态保持 RUNNING 但 PID 为 null。修复：默认应设为 `STOPPED`，仅当 processes 列表中 `alive=true` 时才设为 RUNNING
- **监控显示僵尸 PID（defunct）**：Agent 以 `exec java` 当 PID 1 时，被 `stop`/`restart` 杀掉的旧应用进程不会被回收，变成 `Z` 状态僵尸，仍出现在 `ps` 输出里。`HeartbeatDaemon.collectProcessMetrics()` 会把僵尸和应用新进程一起上报，监控可能显示**已死但未回收的旧 PID**（重启后 PID "没变" 的假象）。两层根治：① `ProcessStatusChecker.findPid` / `HeartbeatDaemon.listJavaProcesses` 增加 `isZombie(pid)` 过滤（读 `/proc/<pid>/stat` 第 3 字段 `Z` 则跳过）；② Dockerfile 用 `tini` 作 PID 1（`ENTRYPOINT ["/usr/bin/tini","--","/entrypoint.sh"]`），`entrypoint.sh` 用循环拉起 agent，僵尸由 tini 回收，且 agent 重启时应用进程被 tini 接管不会被杀。
- **H2 删除不缩文件**：MVStore 的 DELETE 只标记空闲页，文件只增不减，且大事务 DELETE 会让文件反涨。压缩只在**数据库正常关闭**时自动发生（kill -9/崩溃不压缩，下次打开也不补）。规则：定时清理只控行数，压缩必须靠重启（详见下方章节）。
- **H2 URL 是库名、磁盘上是库名.mv.db**：`jdbc:h2:file:.../ops` → 磁盘文件 `ops.mv.db`。解析文件路径要补 `.mv.db`；URL 带 `.mv.db` 会建 `ops.mv.db.mv.db` 空库。
- **YAML map 不能用 @Value 读**：map 配置（如 `table-retain-days`）在 Environment 展开成扁平 key，`@Value` 读不到（死配置坑）。要用 `@ConfigurationProperties` 绑定。
- **bash 变量后紧跟全角字符会 unbound**：`$tbl（`、`$DATA_DIR。` 会把全角标点并入变量名报 unbound。一律写 `${tbl}`、`${DATA_DIR}`。

### Nginx 流量监控 · 白名单与维度扩展（2026-08-08 新增）

- **存储是预聚合表**：`nginx_minute_stat` 按 `(source_id, bucket_time, client_ip, uri, method)` 唯一聚合，所有统计都是在几列上 `GROUP BY`。**任何新维度 = 改存储列 + 改 Agent 聚合 key + 改 SQL，三步成对**，不是前端随便加。
- **白名单（查询侧 L3）**：`nginx_source_whitelist(source_id, type[IP|URI|URI_PREFIX|METHOD], match_value, match_mode[EXACT|PREFIX|CONTAINS], enabled)`。过滤在 `NginxMinuteStatMapper.xml` 的 `<sql id="whitelistFilter">` 片段统一收口，**overview/rank(ip,uri,ip-uri,slow,method)/trend/告警评估**全排除。历史+实时立即生效、配置零延迟。service 端 `buildWhitelistParam(sourceIds)` 加载启用白名单并拆成 `ipExact/ipLike/uriExact/uriLike/methodExact` 传给 Mapper。`WhitelistFilter` 负责匹配逻辑（SQL 侧 like 已带通配符）。新增表由 `NginxTrafficBootstrap.onReady()` 幂等 `CREATE TABLE IF NOT EXISTS` 自动建，老库重启即生效，**无需手动 DDL**。
- **白名单 UI**：日志源配置弹窗新增「白名单」Tab（与基本配置/告警规则并列），增删改随日志源一起保存（`PUT /nginx-traffic/sources/{id}/whitelist`）。
- **趋势图点击跳转**：实时概览趋势图（ECharts）点击某分钟桶 → 自动设 `customRange` 为该分钟 → 切到「排名分析」tab 重新统计，定位激增来源（`jumpToRankAt`）。
- **新增维度：请求方法**：表已有 `method` 列，新增 `rank/method` 后端接口 + 前端「请求方法」排名卡片（`sumByMethod`/`countByMethod`）。
- **维度扩展约束**：状态码（具体 200/404/500）、地区、运营商维度需把对应列加进预聚合唯一键，会显著放大行数（状态码还可能要改 `nginx_minute_stat` 粒度）；地区/运营商依赖 GeoIP 库。这三类列为**后续专项**，不要无脑塞进现有聚合表。

启动后查日志关键字 **`启动路径`** 核对。

## H2 数据库膨胀 · 启动压缩（2026-08-17）

事故：`ops.mv.db` 膨胀到 10GB 占满磁盘、server 起不来。根因：**H2 删除不缩文件，压缩只发生在数据库正常关闭时**；运行中定时清理只控行数、控不了文件大小。

规则：
- **启动前压缩（已实现）**：`ServerApplication.compactBeforeStart()` 在 Spring 启动前执行 `SHUTDOWN COMPACT`。文件超 `COMPACT_THRESHOLD_MB`（环境变量，默认 256MB，0=每次强制）才压；日志看 `[compact] 压缩完成`。Java 内实现，start.sh/systemd/docker 全场景生效，脚本无需干预。
- **运维**：每周 `./stop.sh && ./start.sh` 重启一次，重启即瘦身；重启前确认无其他进程连着库（8099 临时 server 等），否则压缩失败。
- **坑**：① 解析 H2 URL 得库名，须补 `.mv.db` 再判断文件存在（否则静默跳过）；② 残留锁 `ops.mv.db.lock.db`（异常退出遗留）确认无进程占用后可删；③ `SHUTDOWN COMPACT` 末尾报 `already closed` 是无害提示，以文件变小为准；④ 大表 DELETE 用 LIMIT 分批（H2 2.2 子查询 LIMIT 正常），避免长锁。
- **应急**：服务已因磁盘满起不来时，用 `scripts/rescue-h2.sh <data目录> [server.jar] [--yes]` 原地压缩；`scripts/prevent-disk-full.sh` 可做磁盘水位告警（可选）。

## 数据清理维护

`DataCleanupScheduler` 统一管理 21 张流水表的定时清理（默认凌晨 2:00，保留 3 天）。cron、保留天数均在 `application.yml` 的 `easyops.data.cleanup` 下配置。
规则：`table-retain-days` 按表覆盖保留天数（@ConfigurationProperties 绑定，已生效）；`nginx_*` 系列不在此配置，统一走 `nginx-traffic.minute-retain-days`；新增表改 3 处见下。

### 新增清理表（需改 3 处）

| 顺序 | 文件 | 改动 |
|------|------|------|
| 1 | `easyops.data.cleanup.table-retain-days` 加一行 | 声明保留天数 |
| 2 | `DataCleanupScheduler.buildTasks()` 加一行 `tasks.add(task(...))` | 注册清理逻辑 |
| 3 （仅新表） | 对应 `*Mapper.java` + `*Mapper.xml` 补充 `deleteBefore(Long cutoff)` | 如已有则跳过 |

若表不是按 `create_time` 驱动（如 `notification_record` 按 `expire_time`），用 `task(name, IntSupplier)` 重载。

### 修改保留天数

改 `application.yml` 中 `easyops.data.cleanup.retain-days` 即可，重启生效。需要按表单独设的改 `table-retain-days` 下对应值。

### 删除清理表

从 `DataCleanupScheduler.buildTasks()` 移除对应行即可，YML 配置项可同步删除。

### H2DataController 手动清理

`/api/db/cleanup` 手动接口也有一份表列表，新增表时同步更新。

## 应用监控踩坑记录

### 进程存活检测

- **ps aux 输出截断导致 findPid 间歇失败**：`ps aux` 的 COMMAND 列有宽度限制，deployDir 路径长时会被截断，grep 匹配不稳定，导致进程状态在 RUNNING/STOPPED 间抖动。修复：`collectProcessMetrics` 改为直接 `jps -lm` + `ps aux | grep java` 发现所有 Java 进程（排除 Agent 自身），不再依赖目录扫描和 deployDir 匹配。**jps 不可用时自动回退 ps，其中任一找到即判定存活**。
- **Docker 容器内 jps 输出不含 deployDir**：jps 只输出 `PID jar文件名`，没有完整路径。原 `getJpsCandidates` 要求同时匹配 deployDir+jarName，导致候选被全量过滤。修复：候选筛选只要求 jarName 匹配，deployDir 精准确认由后续 `/proc/<pid>/cwd` 完成。
- **部署目录 ≠ 扫描目录**：`ProcessController` 默认部署到 `versions/{projectId}/`，旧代码 `collectProcessMetrics` 只扫描 `apps/`，导致生产环境进程扫不到。修复后已废弃目录扫描，改用 jps/ps 直接发现进程。

### 监控状态一致性

- **健康状态与进程状态不联动**：`saveMonitorSnapshot` 中健康状态在第 426 行就设好了（只看 CPU/内存），进程状态到第 470 行才确定——顺序反了。导致 "进程已停止 + 健康显示 UP" 的矛盾组合。修复：先确定进程状态，再根据进程状态决定健康（STOPPED→DOWN，RUNNING→看资源）。
- **WS 推送覆盖 HTTP 状态**：`broadcastMonitorUpdate` 曾用 `computeAndAttachStatus` 计算 processStatus/healthStatus 并通过 WS 推前端，但该方法在 processes 为空时直接返回 STOPPED（缺少 `inheritPreviousStatus` 逻辑），覆盖前端 HTTP 加载的正确 RUNNING 状态。修复：WS 只推实时指标（CPU/内存/磁盘），状态字段统一走 HTTP 轮询。
- **多项目节点快照继承错误**：`inheritPreviousStatus` 用 `projectIds.get(0)` 查上一个快照，多项目节点可能取到错误项目的状态。修复：改为按 nodeId 查（不按 projectId）。

### 数据混淆

- **WS 推送的堆内存是 Agent 自身的不是应用的**：`metrics.heapMaxMB` 是 Agent JVM 的堆，`metrics.processes[0].heapMaxMb` 才是应用进程的堆。前端 `updateMonitorData` 曾经拿 `metrics.heapMaxMB` 覆盖应用节点的堆显示，导致 3GB/10GB 交替跳动。修复：WS 不再更新 heapUsedMb/heapMaxMb，堆内存只走 HTTP/DB。
- **主机 CPU 和进程 CPU 是两回事**：`hostCpuPercent` 来自 Agent 主机采集，`cpuPercent` 来自 processes[0]。前端已正确区分显示 "总:xx / 进程:xx"，WS 只更新 hostCpuPercent，进程 CPU 走 DB。

### 前端刷新

- **手动刷新时数值跳动**：`fetchDashboard` 用 `dashboard.value = res.data` 全量替换，DB 里的快照是上次心跳写入的（最老 30 秒前），WS 实时推送的最新 CPU/内存被覆盖成旧值。修复：改为 `mergeDashboard`，状态字段用 DB 权威值，实时指标保留 WS 已更新的最新值，只有当 DB 有更新且 collectTime 更大时才覆盖。

### Docker 部署

- **`entrypoint.sh` 只在首次运行时复制 jar**：持久化卷 `/app/data/agent.jar` 已存在时跳过复制，导致旧 jar 一直生效。每次更新镜像后需手动覆盖：`docker exec ops-agent-1 cp /app/agent.jar /app/data/agent.jar && docker restart ops-agent-1`。

### 日志规范

- **监控关键日志统一中文、补异常栈**：`saveMonitorSnapshot`、HeartbeatDaemon、DataCleanupScheduler、HeartbeatChecker 等全部改为中文。WARN/ERROR 必须传异常对象 `e` 而非 `e.getMessage()`。
- **日志级别**：高频循环日志（心跳快照）用 DEBUG，关键运维事件（节点离线、清理完成）用 INFO，异常可恢复用 WARN，严重异常用 ERROR。
- **日志大小控制**：Server 单文件 10MB/保留 7 天/总上限 50MB，Agent 单文件 10MB/保留 7 天/总上限 30MB。Agent 新增文件日志（之前只输出 stdout）。
- **mapper SQL 日志降级**：`com.ops.server.mapper: WARN`，避免控制台被 SQL 刷屏。排查时临时改回 DEBUG。
- **调度器日志降级**：`com.ops.server.scheduler: INFO`，避免分布式锁 DEBUG 刷屏。



端口冲突不能改代码，必须结束掉冲突端口。再启动
agent 部署在 Docker 中
server 我自己验证是自己启动，前端也一样；我需要你全部验证你就应该自己重启自己验证
不管开发任何功能都要有兜底方案
任何任务。 开发完成后必须做功能测试。纯后端修改直接测试 api 
前后端都修改了必须用无头浏览器验证功能，每个修改的按钮，每个返回的字段都要验证
自测发现问题自己修改好，然后再次验证；做测试要模拟用户的各种奇葩的场景以及表单的各种奇葩输入，防止空指针或宕机；
编写代码要极简风格。能用三行解决的问题不要写 十行；
优先考虑性能。尤其是微服务整体性能。对 db 操作的性能，并发性能
不要迎合我，要客观用最佳实践进行评审修改
保持 jdk8，java8语法
每次都要我造数据。真的好烦。我希望系统默认就是有数据的， 我在公司，在家里每次都好自己造数据
我需要你造完数据了给我保存好！ 做成脚本， 随时可以重置数据。 让我继续测试。


## 租户隔离规则（CRUD 必读）

### 核心原则
- **管理员（sys_user.role=admin）= 终极兜底**：`isSuperAdmin()` 放行优先于一切租户校验，管理员永远能访问全部资源。
- **tenantId=null 代表平台视图**（管理员未切换租户），此时 `tenantScopeEnabled(null)=false`，跳过所有租户过滤。
- **tenantId=-1 是哨兵值**：非管理员用户无租户成员关系时赋值，所有租户校验都会失败，用户无法访问任何资源。

### 数据列表页 vs 节点管理页的隔离策略差异

| 场景 | 过滤方式 | 原因 |
|------|----------|------|
| 告警/部署/监控/自愈/版本等**数据列表** | 只按 `tenantId` 过滤 | 数据严格属于当前租户，不能泄露 |
| 节点管理列表 | `tenantId OR defaultTenantId` | 用户需要看到默认租户池节点才能认领 |
| Agent 状态页 | 只按 `tenantId` 过滤 | 状态监控只看本租户节点，不含池节点 |
| Agent 升级页 | `tenantId OR defaultTenantId` | 升级需要覆盖所有可达节点 |
| 节点导出 | `tenantId OR defaultTenantId` | 与节点管理一致 |

### 新增/修改列表页的 checklist
1. 确定当前页是**数据展示**还是**资源管理**（含认领/操作池资源）
2. 数据展示 → 只用 `securityContext.getCurrentTenantId()` 过滤，**不要传 defaultTenantId**
3. 资源管理 → 用 `findByStatusInTenant(tenantId, defaultTenantId)` 包含池节点
4. 管理员平台视图（tenantId=null）→ 走全量查询分支，不经过租户过滤
5. 创建资源时 → 自动设置 `tenantId`，平台视图下归默认租户（避免孤儿数据）

### 已知正确的实现参考
- `AlarmController.listAlarms()` — 数据列表，单 tenantId
- `DeployController.listRecords()` — 数据列表，单 tenantId
- `AppMonitorController.agentStatus()` — 状态页，单 tenantId
- `NodeController.listNodes()` — 资源管理，tenantId + defaultTenantId（含池节点）

