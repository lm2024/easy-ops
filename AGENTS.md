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
| `/auth` | 登录/验证码 (带 captcha) |
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

启动后查日志关键字 **`启动路径`** 核对。

## 数据清理维护

`DataCleanupScheduler` 统一管理 16 张流水表的定时清理（默认凌晨 2:00，保留 3 天）。cron、保留天数均在 `application.yml` 的 `easyops.data.cleanup` 下配置。

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
