<!-- CODEGRAPH_START -->
## CodeGraph

在 `.codegraph/` 索引过的仓库中，优先用 CodeGraph 而不是 grep/find：
- **MCP**: `codegraph_explore`（一问多符号+源码+调用链）、`codegraph_node`（单符号或读文件）
- **Shell**: `codegraph explore` / `codegraph node`
<!-- CODEGRAPH_END -->

---

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

每台只拷自己的 jar + `scripts/`，只改本机 `easyops.env`，**YAML 不动**（脚本注入路径）。

| 机器 | 脚本目录 | 必改项 |
|------|----------|--------|
| Server | `backend/server/scripts/` | `JWT_SECRET`, `AGENT_DATA_PATH`(=Agent 机 INSTALL_DIR/data) |
| Agent | `backend/agent/scripts/` | `AGENT_SERVER_URL`, `AGENT_NODE_NAME` |
| 前端 | `frontend/scripts/` | `SERVER_API_URL`, `SERVE_MODE=nginx` |

命令：`./start.sh` / `./stop.sh` / `./restart.sh`。前端另需 `build.sh`（有网机器打 dist）。

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

启动后查日志关键字 **`启动路径`** 核对。
