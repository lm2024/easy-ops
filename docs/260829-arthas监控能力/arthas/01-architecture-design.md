# Arthas 集成 — 架构设计文档

> 版本：v1.0 | 日期：2026-08-28 | 状态：待评审
> 关联文档：00-可行性研究报告.md

---

## 1. 设计目标

### 1.1 业务目标
- 在 EasyOps 运维平台中集成 Arthas JVM 诊断能力
- 运维人员可在页面上手动触发对任意 Java 进程的深度诊断
- 能够定位 FullGC 根因：哪个类、哪个方法、哪些对象导致内存问题
- 完全无侵入：不改目标应用代码、不引依赖、不重启

### 1.2 非功能目标
| 维度 | 要求 |
|------|------|
| 性能 | 日常零开销；诊断时 CPU 开销 <5%（profiler 采样模式） |
| 安全 | Arthas 只监听 127.0.0.1，Agent 转发；命令操作审计 |
| 可靠 | 会话超时自动 detach；Agent 重启清理残留；异常回滚 |
| 离线 | Arthas 完整包内置 Agent，零网络依赖 |
| 可维护 | 模块化设计，单文件 ≤400 行，遵循现有编码规范 |

---

## 2. 整体架构

### 2.1 架构分层图

```
┌──────────────────────────────────────────────────────────────────┐
│                         前端层 (Vue3 + TS)                         │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ AppMonitorView.vue (应用监控列表)                              │ │
│  │   └── 每行「JVM诊断」按钮 → 打开 ArthasDiagnoseDrawer        │ │
│  │                                                                 │ │
│  │ ArthasDiagnoseDrawer (诊断面板，右侧抽屉)                      │ │
│  │   ├── Tab: 实时诊断                                            │ │
│  │   │   ├── OverviewTab (概览/dashboard/memory)                │ │
│  │   │   ├── MemoryTab (内存分析/heapdump/对象直方图)            │ │
│  │   │   ├── ThreadTab (线程分析/CPU热点/死锁)                   │ │
│  │   │   ├── FlameGraphTab (火焰图/profiler)                     │ │
│  │   │   ├── TraceTab (方法追踪/trace/watch)                     │ │
│  │   │   └── TerminalTab (终端/xterm)                            │ │
│  │   └── Tab: 历史记录 (该应用的诊断会话列表)                     │ │
│  └──────────────────────────────────────────────────────────────┘ │
└───────────────────────────────┬──────────────────────────────────┘
                                │ HTTP REST + WebSocket
┌───────────────────────────────▼──────────────────────────────────┐
│                       Server 层 (Spring Boot 8081)                │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ ArthasDiagnoseController (/api/arthas/*)                      │ │
│  │   ├── POST /diagnose/start      (启动诊断会话)                │ │
│  │   ├── POST /diagnose/stop       (结束诊断会话)                │ │
│  │   ├── GET  /diagnose/status     (查询会话状态)                │ │
│  │   ├── POST /diagnose/exec       (执行命令)                    │ │
│  │   ├── POST /diagnose/profiler/start  (启动采样)              │ │
│  │   ├── POST /diagnose/profiler/stop   (停止采样+火焰图)       │ │
│  │   ├── GET  /diagnose/heapdump    (触发堆转储)                │ │
│  │   ├── GET  /diagnose/history     (诊断历史列表)              │ │
│  │   ├── GET  /diagnose/detail      (单次诊断详情)              │ │
│  │   └── GET  /diagnose/flamegraph  (获取火焰图SVG)             │ │
│  │                                                                 │ │
│  │ ArthasDiagnoseService (业务编排)                               │ │
│  │   ├── 会话生命周期管理                                          │ │
│  │   ├── 命令透传与结果解析                                        │ │
│  │   ├── profiler 任务管理                                         │ │
│  │   ├── 诊断报告生成                                              │ │
│  │   └── 历史记录查询                                              │ │
│  │                                                                 │ │
│  │ ArthasAgentProxy (Agent 调用代理)                              │ │
│  │   └── 封装 AgentClient，调用 Agent /api/arthas/* 接口         │ │
│  │                                                                 │ │
│  │ 数据层                                                          │ │
│  │   ├── arthas_diagnose_record (诊断会话记录)                    │ │
│  │   ├── arthas_diagnose_result (命令结果，小结果存JSON)         │ │
│  │   └── 文件存储: {serverDataPath}/arthas/{recordId}/          │ │
│  │       └── profiler-{type}.svg (火焰图文件)                    │ │
│  └──────────────────────────────────────────────────────────────┘ │
└───────────────────────────────┬──────────────────────────────────┘
                                │ HTTP REST (AgentClient)
┌───────────────────────────────▼──────────────────────────────────┐
│                        Agent 层 (Spring Boot 2123)                │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ ArthasController (/api/arthas/*)                              │ │
│  │   ├── POST /attach        (attach 到目标 PID)                 │ │
│  │   ├── POST /detach        (卸载)                               │ │
│  │   ├── POST /exec          (同步执行命令)                       │ │
│  │   ├── POST /async-exec    (异步执行命令)                       │ │
│  │   ├── GET  /pull          (拉取异步结果)                       │ │
│  │   ├── POST /interrupt     (中断命令)                           │ │
│  │   ├── GET  /status        (会话状态)                           │ │
│  │   └── GET  /heapdump/download (下载heapdump)                  │ │
│  │                                                                 │ │
│  │ ArthasSessionManager (会话管理器，核心)                         │ │
│  │   ├── ConcurrentHashMap<Long, ArthasSession>                   │ │
│  │   ├── attach/detach 生命周期                                   │ │
│  │   ├── 超时自动清理 (10分钟无活动)                              │ │
│  │   ├── 残留检测与清理                                            │ │
│  │   └── Agent 关闭时全量 detach                                  │ │
│  │                                                                 │ │
│  │ ArthasBootstrap (启动器)                                       │ │
│  │   ├── 检查 arthas 完整包是否存在                                │ │
│  │   ├── 不存在则从 Agent jar 内释放                               │ │
│  │   ├── 构建启动命令                                              │ │
│  │   └── 启动 arthas-boot.jar 并等待就绪                          │ │
│  │                                                                 │ │
│  │ ArthasHttpClient (Arthas HTTP API 客户端)                      │ │
│  │   ├── POST http://127.0.0.1:{port}/api                        │ │
│  │   ├── exec / async_exec / pull_results / interrupt_job        │ │
│  │   └── 结果解析与错误处理                                        │ │
│  │                                                                 │ │
│  │ ArthasPortAllocator (端口分配器)                                │ │
│  │   ├── 随机端口范围: 30000-60000                                │ │
│  │   ├── 端口可用性检测                                            │ │
│  │   └── 已用端口追踪                                              │ │
│  │                                                                 │ │
│  │ 本地资源                                                        │ │
│  │   ├── {agentDataPath}/arthas/                                  │ │
│  │   │   ├── arthas-boot.jar                                      │ │
│  │   │   ├── arthas-agent.jar                                     │ │
│  │   │   ├── arthas-core.jar                                      │ │
│  │   │   ├── arthas-spy.jar                                       │ │
│  │   │   └── async-profiler/libasyncProfiler.so                   │ │
│  │   └── {agentDataPath}/arthas/heapdump/                        │ │
│  │       └── {pid}-{timestamp}.hprof                              │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │              目标 JVM (用户的 jar 服务，完全无侵入)            │ │
│  │  ┌────────────────────────────────────────────────────────┐  │ │
│  │  │ Arthas Agent (attach 后注入到目标 JVM 内部)             │  │ │
│  │  │   ├── HTTP API Server (127.0.0.1:{随机端口})           │  │ │
│  │  │   ├── 字节码增强引擎 (ASM)                              │  │ │
│  │  │   ├── profiler (async-profiler 集成)                    │  │ │
│  │  │   └── 命令执行引擎                                       │  │ │
│  │  └────────────────────────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 架构原则

1. **无侵入原则**：Arthas 通过 attach 机制注入目标 JVM，不改目标应用任何代码
2. **代理转发原则**：Arthas HTTP API 只监听 127.0.0.1，所有外部请求经 Agent 转发
3. **手动触发原则**：所有诊断操作由用户在页面手动触发，不自动运行
4. **会话隔离原则**：每个 PID 独立 Arthas 会话，互不影响
5. **自动清理原则**：超时自动 detach，Agent 重启清理残留，防止资源泄漏
6. **大文件不入库原则**：heapdump、火焰图 SVG 等大文件存文件系统，不进 H2
7. **离线优先原则**：Arthas 完整包内置 Agent，零网络依赖

---

## 3. 模块划分

### 3.1 Agent 侧模块

| 模块 | 类名 | 职责 | 行数预估 |
|------|------|------|---------|
| 控制器 | ArthasController | 暴露 REST 接口，参数校验，调用 Service | ~150 |
| 会话管理 | ArthasSessionManager | 会话生命周期、超时清理、残留检测 | ~200 |
| 启动器 | ArthasBootstrap | arthas 包释放、启动命令构建、进程管理 | ~150 |
| HTTP客户端 | ArthasHttpClient | 调用 Arthas HTTP API，结果解析 | ~120 |
| 端口分配 | ArthasPortAllocator | 随机端口分配与可用性检测 | ~80 |
| 会话模型 | ArthasSession | 会话数据模型（pid、port、attachTime等） | ~50 |
| 命令枚举 | ArthasCommandType | 支持的命令类型枚举 | ~60 |
| **合计** | | | **~810** |

### 3.2 Server 侧模块

| 模块 | 类名 | 职责 | 行数预估 |
|------|------|------|---------|
| 控制器 | ArthasDiagnoseController | 暴露 REST 接口，权限校验，参数校验 | ~200 |
| 业务服务 | ArthasDiagnoseService | 会话编排、命令透传、报告生成、历史查询 | ~300 |
| Agent代理 | ArthasAgentProxy | 封装 AgentClient 调用 Agent Arthas 接口 | ~150 |
| 数据模型 | ArthasDiagnoseRecordModel | 诊断会话记录实体 | ~60 |
| 数据模型 | ArthasDiagnoseResultModel | 命令结果实体 | ~60 |
| Mapper | ArthasDiagnoseRecordMapper | 诊断记录 CRUD | ~80 |
| Mapper | ArthasDiagnoseResultMapper | 诊断结果 CRUD | ~80 |
| 任务管理 | ArthasProfilerTaskManager | profiler 异步采样任务管理 | ~120 |
| 文件服务 | ArthasFileService | 火焰图 SVG 文件存储与读取 | ~100 |
| 清理任务 | ArthasCleanupScheduler | 历史记录和文件定时清理 | ~80 |
| **合计** | | | **~1230** |

### 3.3 前端模块

| 模块 | 文件 | 职责 | 行数预估 |
|------|------|------|---------|
| API封装 | api/arthas.ts | 所有 Arthas 相关 API 调用 | ~150 |
| 类型定义 | types/arthas.ts | TypeScript 类型定义 | ~120 |
| 诊断入口 | AppMonitorView.vue (修改) | 增加「JVM诊断」按钮 | +30 |
| 诊断面板 | components/arthas/ArthasDiagnoseDrawer.vue | 右侧抽屉，Tab切换 | ~200 |
| 概览Tab | components/arthas/tabs/OverviewTab.vue | dashboard + memory + 一键体检 | ~300 |
| 内存Tab | components/arthas/tabs/MemoryTab.vue | 内存分析 + heapdump + 对象直方图 | ~350 |
| 线程Tab | components/arthas/tabs/ThreadTab.vue | CPU热点 + 死锁 + 堆栈 | ~300 |
| 火焰图Tab | components/arthas/tabs/FlameGraphTab.vue | profiler 采样 + SVG渲染 | ~250 |
| 追踪Tab | components/arthas/tabs/TraceTab.vue | trace/watch/monitor/stack | ~350 |
| 终端Tab | components/arthas/tabs/TerminalTab.vue | xterm 自由命令 | ~200 |
| 历史Tab | components/arthas/tabs/HistoryTab.vue | 诊断历史列表 + 详情回看 | ~250 |
| 火焰图组件 | components/arthas/FlameGraph.vue | SVG 火焰图渲染组件 | ~300 |
| 状态指示 | components/arthas/ArthasStatusBar.vue | 连接状态、运行时长、操作按钮 | ~100 |
| **合计** | | | **~3200** |

---

## 4. 接口设计

### 4.1 Server → 前端 API

#### 4.1.1 启动诊断会话
```
POST /api/arthas/diagnose/start
Body: { projectId: 1, nodeId: 2, pid: 12345 }
Response: {
  sessionId: "abc123",
  pid: 12345,
  status: "ATTACHED",
  arthasVersion: "3.7.6",
  attachTime: 1724812800000
}
```

#### 4.1.2 结束诊断会话
```
POST /api/arthas/diagnose/stop
Body: { sessionId: "abc123" }
Response: { status: "DETACHED" }
```

#### 4.1.3 执行命令
```
POST /api/arthas/diagnose/exec
Body: { sessionId: "abc123", command: "memory", execTimeout: 10000 }
Response: {
  state: "SUCCEEDED",
  results: [ ... ],
  commandType: "memory",
  durationMs: 120
}
```

#### 4.1.4 启动 profiler 采样
```
POST /api/arthas/diagnose/profiler/start
Body: { sessionId: "abc123", event: "alloc", duration: 30 }
Response: { taskId: "prof_001", status: "SAMPLING", event: "alloc", duration: 30 }
```

#### 4.1.5 停止 profiler 并获取火焰图
```
POST /api/arthas/diagnose/profiler/stop
Body: { sessionId: "abc123", taskId: "prof_001" }
Response: {
  taskId: "prof_001",
  status: "COMPLETED",
  flamegraphUrl: "/api/arthas/diagnose/flamegraph?recordId=1&taskId=prof_001",
  topMethods: [
    { method: "com.example.OrderService.process", percent: 38.5, samples: 1234 }
  ]
}
```

#### 4.1.6 触发 heapdump
```
GET /api/arthas/diagnose/heapdump?sessionId=abc123&live=true
Response: {
  status: "GENERATED",
  fileName: "12345-1724812800000.hprof",
  fileSizeMb: 512,
  downloadUrl: "/api/arthas/diagnose/heapdump/download?sessionId=abc123&fileName=xxx"
}
```

#### 4.1.7 诊断历史列表
```
GET /api/arthas/diagnose/history?projectId=1&page=1&pageSize=20
Response: {
  list: [ { id, projectId, nodeId, pid, status, startTime, endTime, summary } ],
  total: 42,
  page: 1,
  pageSize: 20
}
```

#### 4.1.8 诊断详情
```
GET /api/arthas/diagnose/detail?id=1
Response: {
  record: { ... },
  results: [ { command, commandType, resultJson, execTime, durationMs } ],
  flamegraphs: [ { taskId, event, fileUrl } ]
}
```

### 4.2 Server → Agent API（AgentClient 调用）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | /api/arthas/attach | attach 到 PID |
| POST | /api/arthas/detach | 卸载 |
| POST | /api/arthas/exec | 同步执行命令 |
| POST | /api/arthas/async-exec | 异步执行命令 |
| GET | /api/arthas/pull | 拉取异步结果 |
| POST | /api/arthas/interrupt | 中断命令 |
| GET | /api/arthas/status | 会话状态 |
| GET | /api/arthas/heapdump/download | 下载 heapdump（流式） |

---

## 5. 数据模型设计

### 5.1 arthas_diagnose_record（诊断会话记录）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(64) | NOT NULL, UNIQUE | Arthas 会话 ID |
| project_id | BIGINT | NOT NULL, INDEX | 项目 ID |
| node_id | BIGINT | NOT NULL, INDEX | 节点 ID |
| pid | INT | NOT NULL | 目标进程 PID |
| jar_name | VARCHAR(255) | | jar 包名（冗余，便于展示） |
| status | VARCHAR(20) | NOT NULL | RUNNING/FINISHED/FAILED/DETACHED |
| trigger_by | VARCHAR(64) | | 触发人用户名 |
| arthas_version | VARCHAR(20) | | Arthas 版本 |
| start_time | BIGINT | NOT NULL, INDEX | 开始时间戳 |
| end_time | BIGINT | | 结束时间戳 |
| duration_ms | INT | | 持续时长 |
| summary | TEXT | | 诊断摘要 JSON（关键指标快照） |
| exception | TEXT | | 异常信息（失败时） |
| tenant_id | BIGINT | INDEX | 租户 ID |
| created_at | BIGINT | | 创建时间 |
| updated_at | BIGINT | | 更新时间 |

### 5.2 arthas_diagnose_result（命令执行结果）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| record_id | BIGINT | NOT NULL, INDEX | 关联诊断记录 |
| command | VARCHAR(500) | NOT NULL | 执行的完整命令 |
| command_type | VARCHAR(50) | NOT NULL, INDEX | 命令类型（dashboard/memory/thread等） |
| result_json | TEXT | | 结构化结果 JSON（小结果直接存） |
| result_file | VARCHAR(512) | | 大结果文件路径（火焰图SVG等） |
| result_size_kb | INT | | 结果大小 |
| exec_time | BIGINT | NOT NULL, INDEX | 执行时间戳 |
| duration_ms | INT | | 执行耗时 |
| success | TINYINT | NOT NULL | 是否成功 |
| error_msg | TEXT | | 错误信息 |
| tenant_id | BIGINT | INDEX | 租户 ID |

### 5.3 文件存储结构

```
{serverDataPath}/arthas/
└── {recordId}/
    ├── profiler-cpu-{taskId}.svg
    ├── profiler-alloc-{taskId}.svg
    ├── profiler-lock-{taskId}.svg
    └── report.json (诊断报告快照)

{agentDataPath}/arthas/
├── arthas-boot.jar
├── arthas-agent.jar
├── arthas-core.jar
├── arthas-spy.jar
├── async-profiler/
│   └── libasyncProfiler.so
└── heapdump/
    └── {pid}-{timestamp}.hprof
```

---

## 6. 部署架构

### 6.1 Arthas 包内置方案

```
Agent 构建流程 (mvn package):
  ├── 编译 Agent 源码
  ├── 下载 Arthas 完整包 (固定版本 3.7.6)
  │   └── 从 Maven 仓库或内网镜像下载
  ├── 解压 Arthas 包到 target/classes/arthas/
  └── 打包进 agent.jar (作为 classpath 资源)

Agent 运行时:
  ├── 启动时检查 {agentDataPath}/arthas/arthas-boot.jar
  ├── 不存在 → 从 jar 内资源释放到 {agentDataPath}/arthas/
  └── 存在 → 校验版本，不一致则重新释放
```

### 6.2 Docker 环境要求

```dockerfile
# Docker Agent 需要增加的配置
# 方式1：cap-add（推荐）
docker run --cap-add=SYS_PTRACE ...

# 方式2：Dockerfile 预装 procps（已有）
RUN apt-get install -y procps

# Arthas attach 需要的 Java 工具
# JDK 自带 tools.jar，JRE 可能没有 → Agent 容器必须用 JDK 不是 JRE
```

### 6.3 裸机环境要求

- **无需额外安装**：Arthas 完整包内置 Agent
- **JDK 要求**：目标进程和 Agent 都使用 JDK（不是 JRE），因为 attach 需要 tools.jar
- **权限要求**：Agent 进程和目标 Java 进程必须是同一个用户（或 root）
- **端口要求**：30000-60000 范围本地端口，Arthas 只监听 127.0.0.1

---

## 7. 安全设计

### 7.1 网络安全
- Arthas HTTP API 只监听 `127.0.0.1`，不暴露外网
- Agent 转发请求时校验来源（Server 的 AGENT_TOKEN）
- 不使用 Arthas Tunnel Server，避免额外开放端口

### 7.2 权限控制
- 诊断操作需要用户登录态（JWT）
- 租户隔离：只能诊断当前租户有权限的项目
- 操作审计：所有诊断操作记录到 operation_log

### 7.3 命令安全
- MVP 阶段：白名单命令（dashboard/memory/thread/heapdump/profiler/vmtool/jad/sc/sm）
- 高级阶段：终端 Tab 自由命令，但记录审计日志
- 危险命令（redefine/stop/shutdown）默认禁用，需管理员权限

### 7.4 数据安全
- heapdump 可能包含敏感数据（用户信息、密钥），留在 Agent 本地，不传到 Server
- heapdump 下载需要鉴权，7 天自动清理
- 诊断结果中的敏感字段可配置脱敏

---

## 8. 与现有系统的集成点

### 8.1 Agent 侧集成
| 现有模块 | 集成方式 |
|---------|---------|
| ProcessStatusChecker | attach 前确认 PID 存活 |
| ProcessMetricsHelper | 与 Arthas dashboard 数据互补展示 |
| ShellController | 不复用，Arthas 有独立 HTTP API |
| WebSocketClient | 推送诊断状态和实时结果（可选，MVP 用轮询） |
| HeartbeatDaemon | 心跳中附带 Arthas 会话数（可选） |

### 8.2 Server 侧集成
| 现有模块 | 集成方式 |
|---------|---------|
| AgentClient | 复用，新增 Arthas 相关调用方法 |
| MonitorCollectorService | 不复用，诊断是独立流程 |
| MonitorSnapshotMapper | 不复用，诊断有独立表 |
| DataCleanupScheduler | 复用模式，新增 Arthas 清理任务 |
| SecurityContext | 复用，权限校验 |
| TenantResourceAccessService | 复用，租户隔离 |
| GlobalExceptionHandler | 复用，统一异常处理 |

### 8.3 前端集成
| 现有模块 | 集成方式 |
|---------|---------|
| AppMonitorView.vue | 增加「JVM诊断」按钮 |
| router/index.ts | 不新增路由（诊断用抽屉，不跳新页面） |
| MainLayout.vue | 不新增菜单项 |
| api/ (现有API封装) | 新增 arthas.ts |
| types/index.ts | 新增 arthas 类型（或独立 arthas.ts） |
| ECharts | 复用，内存趋势图 |
| xterm | 复用，终端 Tab |

---

## 9. 技术选型

| 决策点 | 选择 | 理由 |
|--------|------|------|
| Arthas 版本 | 3.7.6 稳定版 | 生产验证充分，JDK 8 完全兼容；4.x 待验证 |
| 集成模式 | Agent 侧 attach + HTTP API 转发 | 不引入 Tunnel Server，复用现有架构 |
| Arthas 包管理 | 完整包内置 Agent | 离线部署，零网络依赖 |
| 通信协议 | HTTP REST（MVP）→ WebSocket（进阶） | MVP 简单可靠，实时性要求高时升级 WS |
| 火焰图渲染 | 原生 SVG 组件 | 不引入额外依赖，交互性好 |
| 终端组件 | xterm.js（项目已有） | 复用现有依赖 |
| 数据库 | H2（现有） | 复用，小结果存 JSON，大结果存文件 |
| 异步任务 | ThreadPoolExecutor（现有模式） | 复用 MonitorCollectorService 的线程池模式 |

---

## 10. 风险与应对

| 风险 | 等级 | 应对措施 |
|------|------|---------|
| attach 失败（权限/容器） | 中 | 明确错误提示；Docker 加 SYS_PTRACE；文档说明 |
| Arthas 进程残留 | 低 | 会话管理器超时清理；Agent 启动时扫描清理 |
| heapdump 磁盘占满 | 中 | 限制单文件大小；7天自动清理；磁盘空间检测 |
| profiler 采样影响生产 | 低 | 默认30秒短采样；明确提示；用户手动触发 |
| trace/watch 增强未 reset | 中 | 会话结束自动 reset；前端显示已增强列表；手动 reset 按钮 |
| H2 数据库膨胀 | 低 | 大文件不入库；90天自动清理；复用现有压缩机制 |
| 多实例并发诊断压垮 Agent | 中 | 单 Agent 同时最多 2 个诊断会话；队列等待 |
| 目标进程 OOM 时 attach 失败 | 低 | OOM 时 JVM 可能无法响应 attach；建议 heapdump 触发 OOM 前 |
| Arthas 与目标应用类冲突 | 极低 | Arthas 使用独立 ClassLoader，隔离性好 |

---

## 11. 术语表

| 术语 | 说明 |
|------|------|
| attach | Java Attach API，将 Agent 注入到运行中的 JVM |
| detach | 从目标 JVM 卸载 Arthas Agent |
| dashboard | Arthas 实时面板命令 |
| heapdump | 堆转储，生成 .hprof 文件 |
| profiler | Arthas 集成的 async-profiler，生成火焰图 |
| flame graph | 火焰图，可视化性能热点 |
| trace | Arthas 方法调用链追踪命令 |
| watch | Arthas 方法入参/返回值监控命令 |
| vmtool | Arthas JVMTI 工具，查实例/强制GC |
| 诊断会话 | 从 attach 到 detach 的一次完整诊断过程 |
