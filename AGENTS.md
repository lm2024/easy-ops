# AGENTS.md — EasyOps AI Agent 提示词

## 项目是什么

**分布式运维部署平台（Server-Agent）**：Server 管节点/项目/版本/部署/监控/告警/AI；Agent 跑在目标机，负责心跳、文件传输、进程启停、Shell、日志采集。WebSocket 推送终端、部署进度、监控数据。

```
Server :8081/api  ──HTTP──→  Agent :2123/api  （部署/文件/Shell）
         ←──心跳 + WebSocket──  Agent
前端 :3000  ──代理 /api──→  Server
```

**代码根目录**：`easy-ops/`（本文件在仓库根 `AGENTS.md`）。

---

## 技术栈（精简）

| 层 | 技术 |
|----|------|
| Server | Java 8, Spring Boot 2.7, MyBatis, H2, JWT, Quartz, WebSocket |
| Agent | Java 8, Spring Boot 2.7, RestTemplate, WebSocket Client |
| Common | 共享 model/enum/Result/异常 |
| 前端 | Vue 3 + Vite 5 + TS + Ant Design Vue 4 + Pinia + ECharts + xterm |

构建：`cd easy-ops/backend && mvn package -DskipTests`  
离线构建：`./build-offline.sh`（用 `backend/local-repo/`）

---

## Jar 与端口

| 组件 | Jar | 端口 | 上下文 |
|------|-----|------|--------|
| Server | `ops-platform-server-1.0.0-SNAPSHOT.jar` | 8081 | `/api` |
| Agent | `easy-ops-agent-1.0.0-SNAPSHOT.jar` | 2123 | `/api` |
| 前端 | `npm run dev` / Nginx 托管 dist | 3000 | — |

默认管理员：`admin / admin123`

---

## 私有化三机部署（重要）

**Server / Agent / 前端各一台，互不共享配置。** 每台只拷自己的 jar + `scripts/`，只改本机 `scripts/easyops.env`，**YAML 不用改**（路径由启动脚本 `-D` 注入）。

| 机器 | 脚本目录 | easyops.env 必改项 |
|------|----------|-------------------|
| Server | `backend/server/scripts/` | `JWT_SECRET`、`AGENT_DATA_PATH`（= Agent 机 `INSTALL_DIR/data`） |
| Agent | `backend/agent/scripts/` | `AGENT_SERVER_URL`、`AGENT_NODE_NAME` |
| 前端 | `frontend/scripts/` | `SERVER_API_URL`、`SERVE_MODE=nginx` |

每台：`./start.sh` / `./stop.sh` / `./restart.sh`；前端另有 `build.sh`（构建机打 dist 后拷到前端机）。

详细步骤见 `easy-ops/backend/DEPLOY.md` 第九节。

### 开发机快速启动

```bash
# Server
easy-ops/backend/server/scripts/start.sh
# Agent
easy-ops/backend/agent/scripts/start.sh
# 前端
easy-ops/frontend/scripts/start.sh   # SERVE_MODE=dev
```

Docker Compose（`backend/docker-compose.yml`）仅**开发用**，生产内网通常**无 Docker**。

---

## 部署流程（Server 侧）

`DeployController.publish`：停旧 → 传 Jar 到 Agent → 启动 → 健康检查 →（失败可回滚）  
定时部署：`DeployScheduler` + `DistributedLock`

Agent 默认版本目录：`{agent.data-path}/versions/{projectId}/{version}/`

---

## 关键配置

敏感项走环境变量：`JWT_SECRET`、`AI_API_KEY`、`AGENT_TOKEN`（禁止硬编码）。

| 配置项 | 说明 |
|--------|------|
| `server.path` | Server 数据根（H2、版本包、日志）；脚本注入 |
| `agent.data-path` | Agent 数据根；脚本注入 |
| `ops.global.agent-data-path` | Server 计算 Agent 路径用，须与 Agent 机实际路径一致 |
| `AGENT_TOKEN` | 机器身份证；**可留空**，启动时自动生成并打日志 |
| `cors.allowed-origins` | 默认 `*`（内网） |

`application.yml` 已加中文注释；私有化优先改各机 `scripts/easyops.env`。

---

## Agent 自升级

| 环境 | 拉起方式 |
|------|----------|
| Docker | 换 jar → `exit` → 容器 restart |
| 裸机 | 备份 jar → 换 jar → 脚本等旧进程退出 → `setsid` 拉新 → 失败回滚 |

**排障日志**：`{data}/logs/upgrade-restart.log`（逐步记录 FAIL 原因，与 agent.log 分离）  
生产：`AGENT_RESTART_MODE=shell` + `scripts/start.sh` 作为 `agent.restart-script`

---

## 主要 API（Server）

| 前缀 | 功能 |
|------|------|
| `/auth` | 登录、用户 |
| `/nodes` | 节点、心跳、Agent 批量升级 |
| `/projects` `/versions` `/deploy` | 项目、版本、部署 |
| `/agent` | 代理 Agent 接口 |
| `/monitor` `/alarms` `/ai` | 监控、告警、AI |

Agent：`/file` `/process` `/shell` `/system` `/system-info`

WebSocket：`/ws/console` `/ws/deploy` `/ws/monitor`

---

## 核心表（H2）

`node_info` `project_info` `version_package` `deploy_record` `sys_user` `user_project_relation` `sys_config` `scheduler_lock` — schema 在 `backend/server/src/main/resources/db/schema.sql`，启动 `mode: always` 自动迁移。

---

## 编码规范

1. 单文件 ≤400 行（上限 500），超限拆分  
2. 模块隔离，禁止跨模块重叠改动  
3. Controller 异常走 `GlobalExceptionHandler`，统一 `Result<T>`  
4. common ← server/agent，禁止 server 依赖 agent  
5. 前端全 TS，类型在 `frontend/src/types/index.ts`  
6. 敏感配置环境变量注入  

---

## 已知坑（必读）

### 路径

- **H2 路径跟启动目录走**：`server.path` 默认 `./data`，从不同 cwd 启动会落到不同库（如 `backend/data` vs `backend/server/data`）。私有化必须用 `scripts/start.sh` 注入绝对路径。
- **Server 与 Agent 路径要对齐**：Server 的 `AGENT_DATA_PATH`（easyops.env）必须等于 Agent 机上的 `{INSTALL_DIR}/data`，否则部署/监控算错目录。
- **禁止硬编码 `/app/data`**：已改为 `ops.global.agent-data-path` / `agent.data-path`；改路径只改 env + 启动脚本。

### Agent

- **Token 非强制**：未配 `AGENT_TOKEN` 会自动生成；两台 Agent 不能共用同一 Token。
- **Docker 心跳离线**：Java 8 容器连 `host.docker.internal` 可能走 IPv6 → 加 `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`（见 docker-compose）。
- **裸机自升级**：脚本先等旧进程退出（释放端口），再 `setsid` 拉起；失败自动回滚 jar → 查 `upgrade-restart.log`

### 部署

- **健康检查**：默认 `8080/hello` 含 `Hello`；无健康端口的项目须在项目管理里**关闭健康检查**或改端口/路径/关键字。
- **启动失败假象**：Agent `ProcessController` 启动应用前须 `mkdir -p logs`，否则 `>> logs/startup.log` 重定向失败，进程起不来。
- **部署目录**：Agent 收包路径 `{data-path}/versions/{projectId}/{version}/`，与 Server 推送路径一致。

### 构建与脚本

- **`backend/start.sh`** 写死 macOS JDK 路径，**内网 Linux 用 `server/scripts/start.sh`** 或 `start-prod.sh`。
- **JDK 8 构建**：测试代码禁用 `Map.of`/`Path.of` 等 Java 9+ API。
- **离线包**：运行只需 fat jar；重打包需 `local-repo/` + Maven。

### 前端

- 内网生产：**只需 Nginx + dist**，不需 Node；`build.sh` 在有网的构建机执行。
- `vite build` 输出到 `frontend/nginx/dist`。

---

## 目录速查

```
easy-ops/
├── backend/
│   ├── server/scripts/     # Server 启停 + easyops.env
│   ├── agent/scripts/      # Agent 启停 + easyops.env
│   ├── common/ server/ agent/
│   ├── docker-compose.yml  # 开发用
│   └── DEPLOY.md           # 内网/裸机/三机部署详解
├── frontend/scripts/       # 前端启停/打包 + easyops.env
└── demo-test-app/          # 部署测试样例
```

启动后查日志关键字 **`启动路径`**（`ServerStartupPathLogger` / `AgentStartupPathLogger`）核对路径是否正确。
