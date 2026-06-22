# AGENTS.md - EasyOps 项目 AI Agent 提示词

## 项目简介

本项目是一个 **分布式运维部署管理平台（EasyOps）**，采用 **Server-Agent** 架构。Server 端负责节点管理、项目配置、版本包管理、部署调度、告警监控、AI 智能分析等；Agent 端部署在目标服务器上，负责心跳上报、进程启停、文件传输、Shell 执行、日志采集等。支持 WebSocket 实时推送（终端、部署进度、监控数据）。

---

## 技术栈

### 后端 - Server (backend/server/)
- **语言**: Java 8 (1.8)
- **框架**: Spring Boot 2.7.18
- **ORM**: MyBatis (mybatis-spring-boot-starter 2.3.2)
- **数据库**: H2 2.2.224（嵌入式，MySQL 兼容模式，自动初始化 schema.sql）
- **安全**: JWT Token + BCrypt 密码哈希 + BouncyCastle + CSRF/XSS/Audit 过滤器
- **调度**: Quartz 2.3.2（定时部署）
- **邮件**: JavaMail 1.6.2（告警通知）
- **HTTP**: Apache HttpClient 5（Server→Agent 调用）+ RestTemplate
- **其他**: Hutool 5.8.28, Fastjson2 2.0.47, OSHI 6.4.13, Commons IO 2.15.1, Joda Time 2.12.5, Jackson 2.15.3, Lombok 1.18.44
- **构建工具**: Maven 多模块

### 后端 - Agent (backend/agent/)
- **语言**: Java 8 (1.8)
- **框架**: Spring Boot 2.7.18
- **HTTP**: RestTemplate（心跳上报→Server）
- **WebSocket**: Spring WebSocket Client（实时日志/终端流）
- **其他**: Hutool 5.8.28, Lombok 1.18.44
- **构建工具**: Maven（父模块统一管理）

### 后端 - Common (backend/common/)
- 共享模块：枚举（DeployStatus, NodeStatus, UserRole, FileAction, FileType）、模型（NodeModel, ProjectModel, VersionModel, DeployModel, AlarmModel, UserModel, OperationLogModel, FileAccessLogModel）、异常（BusinessException, SystemException）、响应（Result）、常量（ErrorCode, SystemConstant）

### 前端 (frontend/)
- **框架**: Vue 3.4 + Vite 5.2 + TypeScript 5.3
- **UI**: Ant Design Vue 4.2.6 + @ant-design/icons-vue 7.0
- **路由**: Vue Router 4.3（路由守卫 + Token 校验）
- **状态管理**: Pinia 2.1.7（auth store + app store）
- **终端**: @xterm/xterm 6.0 + @xterm/addon-fit 0.11（实时终端）
- **图表**: ECharts 5.4.3（监控 Dashboard）
- **加密**: jsencrypt 3.3.2（RSA 前端加密）
- **时间**: dayjs 1.11.10
- **代码编辑**: Monaco Editor 0.45（配置文件编辑）
- **HTTP**: Axios 1.6

---

## 架构说明

### Server-Agent 交互模型

```
┌──────────────┐     HTTP/RestTemplate      ┌──────────────┐
│   Server     │ ──────────────────────────→ │    Agent     │
│  (端口8081)  │   部署指令/文件传输/Shell    │  (端口2123)  │
│              │ ←────────────────────────── │              │
│              │     心跳上报(OS/CPU/内存)    │              │
│              │                             │              │
│              │ ←── WebSocket ────────────── │              │
│              │     实时终端流/部署日志       │              │
└──────────────┘                             └──────────────┘
```

- Server 通过 `AgentProxyController` 代理 Agent 的文件/日志/进程/Shell/监控接口
- Agent 通过 `HeartbeatDaemon` 定时向 Server 上报心跳（IP、OS、Java版本、CPU核数、内存）
- Agent 通过 `WebSocketClient` 连接 Server 推送实时数据
- Agent 启动时强制校验 `AGENT_TOKEN`，无 Token 直接拒绝启动（安全）

### 部署流程

Server 端部署完整流程（`DeployController.publish`）：
1. **停旧进程**: POST `/api/process/{projectId}/stop` → Agent
2. **传输 Jar 包**: POST `/api/file/receive` → Agent（Multipart 上传）
3. **启动新进程**: POST `/api/process/{projectId}/start` → Agent
4. **健康检查**: POST `/api/shell/exec` → Agent（`curl http://127.0.0.1:8080/hello`，最多 5 次）
5. **回滚**: `rollback` 接口查找上一版本，重复上述流程

支持**定时部署**：指定 `scheduleTime` 由 `DeployScheduler` + `DistributedLock` 定时触发

---

## 环境要求

| 依赖       | 版本要求        | 说明                        |
|------------|----------------|----------------------------|
| JDK        | 1.8            | 后端编译运行（Server + Agent）|
| Maven      | 3.6+           | 后端多模块构建               |
| Node.js    | 18+            | 前端构建运行                 |
| Docker     | 20+            | Agent 容器化部署（可选）      |

> 数据库使用 H2 嵌入式，无需单独安装 MySQL 或启动 Docker 数据库容器

---

## 启动指南

### 1. 编译后端（Server + Agent + Common）

```bash
cd backend
mvn clean package -DskipTests
```

### 2. 启动 Server

```bash
cd backend
java -jar server/target/ops-platform-server-1.0.0-SNAPSHOT.jar
```

- 端口: `8081`
- 上下文路径: `/api`
- API 地址: `http://localhost:8081/api/`
- 数据库: H2 嵌入式，自动初始化 `schema.sql`

或使用脚本：
```bash
cd backend
./start.sh
```

### 3. 启动 Agent

```bash
cd backend
java -jar agent/target/ops-platform-agent-1.0.0-SNAPSHOT.jar \
  --agent.token=your-agent-token \
  --agent.node-name=node-1
```

- 端口: `2123`
- 上下文路径: `/api`
- 必须配置 `AGENT_TOKEN` 环境变量（否则启动失败）

或使用 Docker Compose 启动多 Agent 实例：
```bash
cd backend
docker-compose up -d
```

Docker Compose 启动 3 个 Agent 实例（端口 2123/2124/2125），需先在 Server 端注册对应 Token 的节点。

### 4. 启动前端

```bash
cd frontend
npm install   # 首次需要安装依赖
npm run dev
```

- 端口: `3000`
- API 代理: `/api` → `http://localhost:8081`
- WebSocket 代理: `/api` (ws: true)

---

## 关键配置

### Server 配置 (backend/server/src/main/resources/application.yml)

```yaml
server:
  port: 8081
  servlet:
    context-path: /api
  path: ./data              # 数据/日志/版本包存储目录
  heart-second: 5           # 心跳检测间隔（秒）
  offline-second: 20        # 节点离线判定阈值（秒）
  max-http-request-size: 10MB

spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:./data/ops;MODE=MySQL   # H2 嵌入式，MySQL 兼容模式
    username: sa
    password:                              # H2 默认无密码
  h2.console.enabled: ${H2_CONSOLE_ENABLED:false}  # H2 控制台（默认关闭）
  sql.init.mode: always                    # 每次启动执行 schema.sql
  servlet.multipart.max-file-size: 500MB   # Jar 包上传限制

jwt:
  secret: ${JWT_SECRET:}                   # JWT 密钥（环境变量注入）
  expire-ms: 86400000                      # Token 过期时间 24 小时

mybatis:
  map-underscore-to-camel-case: true

cors:
  allowed-origins: "http://localhost:3000,http://localhost:5173"

ai:
  apiKey: ${AI_API_KEY:}                   # AI API 密钥（环境变量注入）

websocket:
  max-connections: 1000
```

### Agent 配置 (backend/agent/src/main/resources/application.yml)

```yaml
server:
  port: 2123
  servlet:
    context-path: /api

agent:
  server-url: ${AGENT_SERVER_URL:http://localhost:8081/api}  # Server 地址
  token: ${AGENT_TOKEN:}              # 认证 Token（必须配置，否则启动失败）
  node-name: ${HOSTNAME:default-node} # 节点名称
  data-path: /app/data               # 数据存储路径
  check-interval: 30                 # 心跳间隔（秒）
```

### 前端配置 (frontend/vite.config.ts)

```typescript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8081',
      changeOrigin: true,
      ws: true    // WebSocket 代理
    }
  }
}
```

---

## 项目结构

```
easy-ops/
├── backend/                         # Maven 多模块后端
│   ├── pom.xml                      # 父 POM (ops-platform-parent)
│   ├── common/                      # 共享模块 (ops-platform-common)
│   │   └── src/main/java/com/ops/common/
│   │       ├── constant/            # ErrorCode, SystemConstant
│   │       ├── enums/               # DeployStatus, NodeStatus, UserRole, FileAction, FileType
│   │       ├── exception/           # BusinessException, SystemException
│   │       ├── model/               # NodeModel, ProjectModel, VersionModel, DeployModel, AlarmModel, UserModel, OperationLogModel, FileAccessLogModel
│   │       └── response/            # Result (统一响应封装)
│   ├── server/                      # Server 模块 (ops-platform-server)
│   │   └── src/main/java/com/ops/server/
│   │       ├── config/              # RestTemplateConfig, SecurityConfig, WebConfig, WebSocketConfig
│   │       ├── controller/          # REST 控制器（12 个）
│   │       ├── exception/           # GlobalExceptionHandler
│   │       ├── filter/              # CsrfFilter, XssFilter, KeyAuditFilter, ExternalApiGuardFilter
│   │       ├── interceptor/         # AuthInterceptor, WebSocketAuthInterceptor
│   │       ├── mapper/              # MyBatis Mapper（10 个）
│   │       ├── scheduler/           # DeployScheduler, HeartbeatChecker, DistributedLock
│   │       ├── service/             # AlarmService, DeployService, NodeService, ProjectService, VersionService
│   │       ├── util/                # SecurityContext
│   │       ├── websocket/           # ConsoleHandler, DeployHandler, MonitorHandler
│   │       └── ServerApplication.java
│   │   └── src/main/resources/
│   │       ├── db/schema.sql        # H2 数据库初始化脚本
│   │       ├── mapper/*.xml         # MyBatis XML 映射（10 个）
│   │       └── application.yml      # Server 配置
│   ├── agent/                       # Agent 模块 (ops-platform-agent)
│   │   └── src/main/java/com/ops/agent/
│   │       ├── client/              # WebSocketClient（连接 Server 推送实时数据）
│   │       ├── controller/          # FileController, ProcessController, ShellController, SystemController, SystemInfoController
│   │       ├── daemon/              # HeartbeatDaemon（心跳上报）, AutoRestartDaemon
│   │       ├── handler/             # FileCommander, LogCommander, StartCommander, StopCommander
│   │       └── AgentApplication.java
│   │   └── src/main/resources/
│   │       └── application.yml      # Agent 配置
│   ├── docker-compose.yml           # Docker 多 Agent 容器部署（3 实例）
│   └── start.sh                     # 后端启动脚本
├── frontend/                        # Vue 3 前端
│   ├── src/
│   │   ├── api/                     # API 请求封装（10 模块）
│   │   │   ├── agent.ts             # Agent 代理接口
│   │   │   ├── ai.ts                # AI 分析/对话
│   │   │   ├── auth.ts              # 登录/用户管理
│   │   │   ├── deploy.ts            # 部署管理
│   │   │   ├── fileApi.ts           # 文件操作
│   │   │   ├── monitor.ts           # 监控数据
│   │   │   ├── node.ts              # 节点管理
│   │   │   ├── operationLog.ts      # 操作审计
│   │   │   ├── project.ts           # 项目管理
│   │   │   └── version.ts           # 版本包管理
│   │   ├── components/
│   │   │   └── MainLayout.vue       # 主布局（侧边栏+导航）
│   │   ├── router/
│   │   │   └── index.ts             # 路由配置（含路由守卫 Token 校验）
│   │   ├── stores/
│   │   │   ├── app.ts               # 应用全局状态
│   │   │   └── auth.ts              # 认证状态（Token/用户信息）
│   │   ├── types/
│   │   │   └── index.ts             # TypeScript 类型定义
│   │   ├── utils/
│   │   │   └── request.ts           # Axios 请求封装（拦截器/Token 注入）
│   │   ├── views/                   # 页面视图（20 个）
│   │   │   ├── LoginView.vue        # 登录页
│   │   │   ├── NodeListView.vue     # 节点列表
│   │   │   ├── NodeFormView.vue     # 节点新增/编辑
│   │   │   ├── ProjectListView.vue  # 项目列表
│   │   │   ├── ProjectFormView.vue  # 项目新增/编辑
│   │   │   ├── ProjectDetailView.vue# 项目详情
│   │   │   ├── VersionListView.vue  # 版本包列表
│   │   │   ├── DeployListView.vue   # 部署记录列表
│   │   │   ├── DeployDetailView.vue # 部署详情
│   │   │   ├── ConsoleView.vue      # 实时终端（xterm）
│   │   │   ├── FileLogView.vue      # 日志文件查看
│   │   │   ├── ConfigEditor.vue     # 配置文件编辑（Monaco）
│   │   │   ├── DashboardView.vue    # 监控仪表盘（ECharts）
│   │   │   ├── AlarmListView.vue    # 告警列表
│   │   │   ├── AlarmConfigView.vue  # 告警配置（SMTP）
│   │   │   ├── UserListView.vue     # 用户管理
│   │   │   ├── UserFormView.vue     # 用户新增/编辑
│   │   │   ├── OperationLogView.vue # 操作审计日志
│   │   │   ├── BatchDownloadView.vue# 批量下载
│   │   │   └── AIConfigView.vue     # AI 配置/对话
│   │   ├── App.vue
│   │   └── main.ts
│   ├── vite.config.ts
│   └── package.json
├── docs/                            # 设计文档
│   └── req-doc/                     # 需求/设计文档（9 个）
├── demo-test-app/                   # 示例测试应用
└── AGENTS.md
```

---

## API 接口一览

### Server 端 Controller

| Controller | 路径前缀 | 主要功能 |
|-----------|---------|---------|
| **SystemController** | `/auth` | 登录（BCrypt+JWT）、用户 CRUD、权限校验 |
| **NodeController** | `/nodes` | 节点 CRUD、CSV 导入/导出、心跳接收、标签管理 |
| **ProjectController** | `/projects` | 项目 CRUD、权限校验（SEC-004 用户-项目关系） |
| **VersionController** | `/versions` | 版本包上传（SHA-256 校验）、列表、删除 |
| **DeployController** | `/deploy` | 部署发布（停旧→传Jar→启动→健康检查）、回滚、定时部署、取消 |
| **AlarmController** | `/alarms` | 告警记录查询、告警配置（SMTP）、发送告警 |
| **AIAnalyzeController** | `/ai` | AI 配置管理、日志分析（读日志→调 OpenAI 兼容 API）、AI 对话 |
| **MonitorController** | `/monitor` | 节点监控数据（CPU/内存/磁盘） |
| **LogController** | `/logs` | 日志查询 |
| **FileController** | `/files` | 文件操作 |
| **ProcessController** | `/process` | 进程管理（查看/启停） |
| **AgentProxyController** | `/agent` | 代理转发到 Agent 的文件/日志/进程/Shell/监控接口 |

### Agent 端 Controller

| Controller | 路径前缀 | 主要功能 |
|-----------|---------|---------|
| **FileController** | `/file` | 文件接收（Jar 包上传）、文件浏览 |
| **ProcessController** | `/process` | 进程启停（接收 Server 指令执行 start/stop 脚本） |
| **ShellController** | `/shell` | Shell 命令执行（用于健康检查等） |
| **SystemController** | `/system` | Agent 系统信息 |
| **SystemInfoController** | `/system-info` | 系统硬件信息（OSHI：CPU/内存/磁盘详细数据） |

---

## 数据库表结构

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `node_info` | 节点信息 | name, ip, port, token, status, os_info, tags, cpu_cores, total_memory_mb |
| `project_info` | 项目配置 | name, node_ids, start_script, stop_script, jvm_opts, env_vars, jar_name, deploy_dir |
| `version_package` | 版本包 | project_id, jar_name, file_path, file_size, version, sha256 |
| `deploy_record` | 部署记录 | project_id, version_id, node_id, status, log, schedule_time |
| `alarm_record` | 告警记录 | project_id, node_id, type, content, send_result |
| `sys_user` | 系统用户 | username, password(BCrypt), role(admin/operator), status |
| `operation_log` | 操作审计 | user_id, module, action, content, ip |
| `file_access_log` | 文件访问审计 | user_id, node_id, file_type, file_path, action |
| `alarm_config` | 告警配置 | smtp_host, smtp_port, smtp_ssl, sender_email, receivers |
| `sys_config` | 系统配置 | config_key, config_value |
| `scheduler_lock` | 分布式调度锁 | lock_name, instance_id, locked_at, expire_at |
| `user_project_relation` | 用户-项目关系 | user_id, project_id（权限控制） |

> 默认管理员: `admin / admin123`（BCrypt 哈希存储）

---

## 安全机制

| 安全措施 | 实现位置 | 说明 |
|---------|---------|------|
| JWT 认证 | `AuthInterceptor` | Token 校验，24 小时过期 |
| 密码哈希 | JBCrypt | BCrypt 加盐哈希，不存明文 |
| CSRF 防护 | `CsrfFilter` | 防止跨站请求伪造 |
| XSS 防护 | `XssFilter` | 过滤恶意脚本输入 |
| API Key 审计 | `KeyAuditFilter` | 记录 API Key 使用 |
| 外部接口守卫 | `ExternalApiGuardFilter` | 限制外部 API 调用 |
| Agent Token | `HeartbeatDaemon` | Agent 启动强制校验 Token，无 Token 拒绝启动 |
| 权限控制 | SEC-003/SEC-004 | 用户-项目关系表，项目详情/修改/删除需权限校验 |
| 操作审计 | `operation_log` + `file_access_log` | 记录所有用户操作和文件访问 |
| 分布式锁 | `DistributedLock` + `scheduler_lock` 表 | 防止部署任务并发冲突 |
| RSA 加密 | 前端 `jsencrypt` | 密码传输 RSA 加密 |

---

## WebSocket 实时推送

| Handler | 路径 | 推送内容 |
|---------|------|---------|
| `ConsoleHandler` | `/ws/console` | 实时终端输出流（xterm 渲染） |
| `DeployHandler` | `/ws/deploy` | 部署进度/日志实时推送 |
| `MonitorHandler` | `/ws/monitor` | 监控数据实时推送（CPU/内存/磁盘） |

Agent 端通过 `WebSocketClient` 连接 Server，将实时数据推送至对应 Handler。

---

## 编码规范

1. **行数限制**: 类和文档不超过 400 行，上限 500 行，超过则拆分
2. **模块隔离**: 代码修改模块/文件相互隔离，禁止范围重叠
3. **任务拆分**: 按「整体需求 → 主任务 → 原子任务」拆分，单会话执行单个原子任务
4. **命名规范**: Java 使用驼峰命名，MyBatis 开启下划线转驼峰 (`map-underscore-to-camel-case: true`)
5. **异常处理**: 所有 Controller 层接口必须有异常兜底处理（`GlobalExceptionHandler`）
6. **编码格式**: 所有文件使用 UTF-8 编码
7. **统一响应**: 所有接口返回 `Result<T>` 封装，含 code/message/data
8. **安全优先**: 敏感信息（JWT_SECRET、AI_API_KEY、AGENT_TOKEN）通过环境变量注入，禁止硬编码
9. **Maven 多模块**: common 模块存放共享代码，server/agent 模块依赖 common，禁止 server 直接依赖 agent
10. **前端 TypeScript**: 所有前端代码使用 TypeScript，类型定义集中在 `types/index.ts`
