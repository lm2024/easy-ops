# EasyOps 集成 Arthas JVM 诊断 — 可行性研究报告

> 版本：v1.0 | 日期：2026-08-28 | 作者：AI 架构师
> 状态：可研通过，待排期实施

---

## 一、背景与痛点

### 1.1 现状

EasyOps 目前对 10 个传统 Java 项目的运维监控能力：

| 监控维度 | 现有能力 | 数据来源 |
|---------|---------|---------|
| 进程存活 | ✅ PID、RUNNING/STOPPED | `ps` + `jps` |
| 主机资源 | ✅ CPU%、内存%、磁盘% | OSHI |
| JVM 堆 | ✅ heapUsed / heapMax / Xmx | `jstat -gc` |
| GC 统计 | ✅ YGC 次数、FGC 次数、GC 总耗时 | `jstat -gc` |
| 健康检查 | ✅ HTTP 探针、响应时间 | HttpHealthProber |
| **哪个类占内存** | ❌ 看不到 | — |
| **哪个方法导致 FullGC** | ❌ 看不到 | — |
| **对象实例数/大小** | ❌ 看不到 | — |
| **CPU 热点方法** | ❌ 看不到 | — |
| **线程死锁/阻塞** | ❌ 看不到 | — |
| **方法调用链耗时** | ❌ 看不到 | — |

### 1.2 核心痛点

**大佬遇到的真实场景**：jar 服务频繁 FullGC，但运维系统只能看到「FGC 次数在涨」「堆内存使用率 90%」，完全不知道：

- 是哪个类的对象在堆里堆积？（比如 100 万个 Order 对象占了 1GB）
- 是哪个方法在疯狂 new 对象？
- 是静态集合泄漏？还是缓存没淘汰？还是大对象没释放？
- 线程里谁在占 CPU？谁被锁阻塞了？

**结论**：现有监控只告诉你「生病了发烧了」，但不告诉你「哪个器官出了问题」。需要 Arthas 来做「CT 扫描」。

---

## 二、Arthas 深度调研

### 2.1 版本信息

| 项目 | 值 | 来源 |
|------|-----|------|
| 官网最新版本 | **v4.3.3** | arthas.aliyun.com（2026-08-28） |
| 稳定版系列 | 3.7.x（3.7.6） | 多个技术博客 2026-08 |
| GitHub | github.com/alibaba/arthas | 官方仓库 |
| 开源协议 | Apache 2.0 | 商业友好 |
| 支持 JDK | 8 / 11 / 17 / 21 / 25 | 官网文档 |
| 支持 OS | Linux / Mac / Windows | 官网文档 |

### 2.2 核心原理（无侵入的关键）

Arthas 基于 **Java Instrumentation API + ASM 字节码增强**，工作方式：

```
目标 JVM（运行中，不需要重启）
    ↑ attach（VirtualMachine.attach(pid)）
Arthas Agent（注入到目标 JVM 内部）
    ↑ 字节码增强（修改类的方法，插入监听代码）
Arthas 命令执行 → 返回结果
```

**关键特性**：
- ✅ **不需要重启目标应用**——attach 到正在运行的 JVM
- ✅ **不需要修改目标应用代码**——完全外部注入
- ✅ **不需要目标应用引入依赖**——Arthas 自己是独立 jar
- ✅ **可随时 detach 卸载**——卸载后字节码恢复原样

### 2.3 基本功能清单（与本需求相关）

| 命令 | 功能 | 对 FullGC 排查的价值 |
|------|------|---------------------|
| `dashboard` | 实时面板：线程、内存、GC、CPU、Runtime | 第一步全局概览，看哪块内存异常 |
| `memory` | JVM 各内存区域使用情况（堆/非堆/各代） | 定位是老年代涨还是元空间涨 |
| `heapdump` | 生成堆转储 .hprof 文件 | 终极手段，导出后用 MAT 分析 |
| `thread` | 线程堆栈、CPU 占比、死锁检测 | 找 CPU 最高的线程、死锁线程 |
| `jvm` | JVM 基本信息、参数、类加载 | 确认 JVM 配置是否合理 |
| `vmoption` | 查看/修改 JVM 诊断选项 | 动态打开 GC 日志等 |
| `perfcounter` | JVM Perf Counter 性能计数器 | 更细粒度的 JVM 内部指标 |

### 2.4 高级功能清单（核心价值）

#### 2.4.1 profiler 火焰图（基于 async-profiler）

**这是定位性能瓶颈的终极武器。**

| 事件类型 | 看什么 | 典型场景 |
|---------|--------|---------|
| `cpu` | CPU 占用采样 | CPU 飙高，找最耗 CPU 的函数 |
| `alloc` | Java 内存分配量 | **频繁 GC、堆涨太快，找谁在疯狂 new 对象** |
| `lock` | Java 锁竞争耗时 | 线程阻塞、响应变慢、锁粒度太大 |
| `wall` | 墙钟时间（含等待） | 请求整体慢但不一定是 CPU 问题 |
| `itimer` | CPU 备选方案 | 容器里 perf_event 不可用时替代 |

**输出**：SVG 格式火焰图，前端可直接渲染。X 轴宽度 = 热度，颜色无性能含义。

**对 FullGC 的价值**：`profiler start --event alloc` 采样 30 秒，直接告诉你「哪个类的哪个方法在分配最多内存」，这就是 FullGC 的元凶。

#### 2.4.2 vmtool（JVMTI 级对象操作）

| action | 功能 | 价值 |
|--------|------|------|
| `getInstances` | 按类名获取堆中存活的对象实例 | 直接看某个类有多少实例、占多少内存 |
| `forceGc` | 强制触发 Full GC | 对比 GC 前后内存变化，判断是否泄漏 |

**示例**：`vmtool --action getInstances --className com.example.OrderCache --limit 10` 直接把堆里的 OrderCache 对象捞出来看内容。

#### 2.4.3 trace / watch / monitor（方法级诊断）

| 命令 | 功能 | 价值 |
|------|------|------|
| `trace` | 追踪方法调用链和每一步耗时 | 找慢方法、找大对象创建路径 |
| `watch` | 监控方法入参、返回值、异常 | 看方法实际处理了什么数据 |
| `monitor` | 方法执行监控（成功/失败/平均耗时） | 统计方法调用频率和性能 |
| `stack` | 输出方法被调用的堆栈 | 谁在调用这个方法 |
| `tt` | 时间隧道，记录方法调用并可回放 | 复现问题后回看当时的入参 |

#### 2.4.4 类与类加载器诊断

| 命令 | 功能 |
|------|------|
| `sc` | 搜索已加载的类，查看类的详细信息 |
| `sm` | 查看类的方法信息 |
| `jad` | 反编译已加载类的源码（带行号） |
| `classloader` | 查看类加载器继承树、类加载泄漏 |
| `dump` | dump 已加载类的 byte code |

#### 2.4.5 在线热更新（高级，谨慎使用）

`jad` → `mc`（内存编译）→ `redefine`（重定义类），可在不重启的情况下热更新代码。**本项目暂不启用，仅作为应急止血能力预留。**

### 2.5 HTTP API（集成的关键接口）

Arthas 提供 **结构化 JSON 的 HTTP API**，这是我们能集成进 EasyOps 的核心基础。

**接口地址**：`POST http://{ip}:{port}/api`

**请求格式**：
```json
{
  "action": "exec",
  "command": "memory",
  "execTimeout": 10000
}
```

**支持的 action**：

| action | 用途 | 适用场景 |
|--------|------|---------|
| `exec` | 同步执行命令，超时后返回 | dashboard、memory、thread 等瞬时命令 |
| `init_session` | 创建会话 | 需要连续执行多个命令 |
| `async_exec` | 异步执行命令 | watch、trace 等持续输出的命令 |
| `pull_results` | 长轮询拉取异步结果 | 配合 async_exec 使用 |
| `interrupt_job` | 中断正在运行的命令 | 停止 watch/trace/profiler |

**响应格式（结构化 JSON）**：
```json
{
  "state": "SUCCEEDED",
  "body": {
    "results": [
      { "type": "memory", "heap": "...", "jobId": 5 },
      { "type": "status", "statusCode": 0, "jobId": 5 }
    ],
    "jobStatus": "TERMINATED"
  }
}
```

**关键优势**：相比 Telnet/WebConsole 的非结构化文本，HTTP API 返回的是结构化 JSON，程序可以直接解析字段，不需要正则匹配文本输出。

### 2.6 Arthas 能力边界（诚实评估）

| 能做 | 不能做 / 有限制 |
|------|----------------|
| ✅ 查看堆内存各区域使用 | ❌ 不能直接显示「每个类占多少字节」（需 heapdump + MAT 或 profiler alloc） |
| ✅ 生成 heapdump 供离线分析 | ❌ heapdump 文件可能很大（GB级），传输和存储需考虑 |
| ✅ profiler alloc 采样内存分配热点 | ⚠️ 采样有开销，生产环境建议短时间（30s-2min） |
| ✅ vmtool 按类名查实例数 | ⚠️ getInstances 大数量级时可能卡顿 |
| ✅ trace 方法调用链耗时 | ⚠️ 增强会有性能开销，用完需 reset |
| ✅ thread 查 CPU 热点线程和死锁 | — |
| ✅ 反编译、查看类加载器 | — |
| ✅ 动态修改日志级别、JVM 参数 | — |

**结论**：Arthas 能覆盖 90% 以上的 JVM 诊断场景。对于「哪个类占内存最多」这种问题，最佳路径是 `profiler --event alloc`（实时采样分配热点）或 `heapdump` + MAT（离线全量分析），两者结合使用。

---

## 三、可行性分析

### 3.1 技术可行性：✅ 完全可行

| 评估项 | 结论 | 依据 |
|--------|------|------|
| 无侵入 attach | ✅ | Arthas 官方核心能力，生产验证多年 |
| HTTP API 结构化输出 | ✅ | 官方文档明确支持，返回 JSON |
| 与现有 Agent 集成 | ✅ | Agent 已有 Shell 执行、进程管理、PID 发现能力 |
| 与现有 Server 集成 | ✅ | Server 已有 AgentClient、异步任务、监控快照体系 |
| 前端展示 | ✅ | 现有 Vue3 + Ant Design Vue + ECharts，火焰图有 SVG 渲染方案 |
| JDK 8 兼容 | ✅ | 项目用 Java 8，Arthas 明确支持 JDK 8 |
| 离线部署 | ✅ | arthas-boot.jar 可内置到 Agent 包，首次运行下载到 data 目录 |

### 3.2 无侵入性验证

**对目标应用的影响**：
1. **不需要修改代码**——Arthas 是外部 attach
2. **不需要引入依赖**——目标应用 classpath 不变
3. **不需要重启**——运行中 attach
4. **可随时卸载**——`stop` 命令后 Arthas 退出，字节码恢复
5. **attach 本身开销极小**——仅注入一个 agent jar

**诊断命令的性能开销分级**：

| 命令类型 | 开销 | 建议使用方式 |
|---------|------|------------|
| dashboard / memory / thread / jvm | 极低（<1%） | 可频繁使用 |
| heapdump | 中（暂停时间与堆大小相关） | 手动触发，避免高峰期 |
| profiler（cpu/alloc/lock） | 低-中（采样模式，5%以内） | 短时间采样（30s-2min） |
| vmtool getInstances | 中（遍历堆） | 限定 limit，避免全量 |
| trace / watch / monitor | 中-高（字节码增强） | 用完立即 reset，不长期运行 |
| jad / sc / sm | 低 | 只读，无增强 |

### 3.3 与现有系统的对接点

| 现有模块 | 可复用能力 | Arthas 集成方式 |
|---------|-----------|----------------|
| Agent `ProcessStatusChecker` | 发现 Java 进程 PID | attach 前先确认 PID 存活 |
| Agent `ProcessMetricsHelper` | jstat 采集 JVM 指标 | 与 Arthas dashboard 数据互补 |
| Agent `ShellController` | 执行 shell 命令 | 启动 arthas-boot.jar、管理进程 |
| Agent `WebSocketClient` | 与 Server 双向通信 | 推送诊断进度和实时结果 |
| Server `AgentClient` | HTTP 调用 Agent | 透传 Arthas 命令 |
| Server `MonitorCollectorService` | 异步采集任务框架 | 复用异步任务模式 |
| Server `MonitorSnapshotMapper` | 监控快照存储 | 新增诊断记录表 |
| 前端 `AppMonitorView.vue` | 应用监控列表页 | 增加「JVM诊断」入口按钮 |
| 前端 ECharts | 图表渲染 | 内存趋势、GC 趋势图 |
| 前端 xterm | 终端组件 | 高级用户自由命令终端 |

### 3.4 风险评估与应对

| 风险 | 等级 | 应对措施 |
|------|------|---------|
| attach 失败（权限不足/容器限制） | 中 | 检测失败原因，给出明确提示；Docker Agent 需确保 `SYS_PTRACE` 或 `--pid=host` |
| Arthas 进程残留 | 低 | Agent 维护会话管理器，超时自动 detach；Agent 重启时清理残留 |
| heapdump 文件过大 | 中 | 限制文件大小，超过阈值提示；文件存 Agent 本地，提供下载链接，不传到 Server |
| profiler 采样影响生产 | 低 | 默认短时间（30s），用户可配置；明确提示「采样中」 |
| trace/watch 增强后忘记 reset | 中 | 诊断会话结束时自动 reset；前端有「重置增强」按钮 |
| 端口冲突 | 低 | Arthas HTTP API 使用随机端口（30000-60000），Agent 管理端口映射 |
| 目标应用 JDK 版本不兼容 | 低 | 项目统一 Java 8，Arthas 完全支持； attach 前检测 java 版本 |
| 多实例同时诊断压垮 Agent | 中 | 限制单 Agent 同时诊断数（默认 2）；队列等待 |

---

## 四、架构设计

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Vue3)                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ 监控列表页    │  │ JVM诊断面板   │  │ 诊断历史/报告     │  │
│  │ +诊断入口按钮 │→ │ (多Tab)       │→ │ (快照对比)        │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP / WebSocket
┌────────────────────────────▼────────────────────────────────┐
│                      Server (8081)                            │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐ │
│  │ ArthasController│  │ ArthasDiagnoseSvc│  │ 诊断记录表  │ │
│  │ /api/arthas/*   │←→│ (任务编排/透传)   │←→│ H2         │ │
│  └────────┬────────┘  └──────────────────┘  └────────────┘ │
│           │ AgentClient (HTTP)                                │
└───────────┼─────────────────────────────────────────────────┘
            │
┌───────────▼─────────────────────────────────────────────────┐
│              Agent (2123) — 每台机器一个                     │
│  ┌────────────────────┐  ┌──────────────────────────────┐   │
│  │ ArthasController   │  │ ArthasSessionManager         │   │
│  │ /api/arthas/*      │  │ (PID→端口映射/超时清理/残留检测)│   │
│  └─────────┬──────────┘  └──────────────┬───────────────┘   │
│            │                              │                   │
│            │ HTTP API                     │ attach/detach     │
│            ▼                              ▼                   │
│  ┌──────────────────────────────────────────────────────┐    │
│  │          Arthas (注入到目标 JVM 内部)                  │    │
│  │   arthas-boot.jar → attach → HTTP API (随机端口)      │    │
│  │   dashboard / memory / profiler / thread / vmtool...  │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│  目标 JVM (用户的 jar 服务，完全无侵入)                       │
└───────────────────────────────────────────────────────────────┘
```

### 4.2 Agent 侧设计

#### 4.2.1 新增文件

```
backend/agent/src/main/java/com/ops/agent/
├── controller/
│   └── ArthasController.java          # Arthas 诊断接口
├── arthas/
│   ├── ArthasSessionManager.java      # 会话管理（核心）
│   ├── ArthasBootstrap.java           # 启动/attach 逻辑
│   ├── ArthasHttpClient.java          # 调用 Arthas HTTP API
│   └── ArthasPortAllocator.java       # 随机端口分配
└── resources/
    └── arthas/                        # 内置 arthas-boot.jar（可选）
```

#### 4.2.2 ArthasController 接口设计

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/arthas/attach` | attach 到指定 PID，启动 HTTP API |
| POST | `/api/arthas/detach` | 卸载指定 PID 的 Arthas |
| POST | `/api/arthas/exec` | 执行 Arthas 命令（同步） |
| POST | `/api/arthas/async-exec` | 异步执行命令（watch/trace/profiler） |
| GET | `/api/arthas/pull` | 拉取异步命令结果 |
| POST | `/api/arthas/interrupt` | 中断正在运行的命令 |
| GET | `/api/arthas/status` | 查看 attach 状态和会话列表 |
| GET | `/api/arthas/heapdump/download` | 下载 heapdump 文件 |

#### 4.2.3 attach 流程

```
1. 接收 { pid, projectId, nodeId }
2. 检查 PID 是否存活（ProcessStatusChecker）
3. 检查是否已 attach（ArthasSessionManager）
4. 分配随机端口（30000-60000，检测端口可用）
5. 启动 arthas-boot.jar：
   java -jar arthas-boot.jar --attach-only
     --target-pid {pid}
     --tunnel-server ""（不使用 tunnel）
     --http-port {port}
     --http-ip 127.0.0.1（只监听本地，Agent 转发）
6. 等待 Arthas HTTP API 就绪（轮询 /api，最多 15s）
7. 记录会话：{ pid, port, attachTime, projectId, lastActiveTime }
8. 返回 { sessionId, port, pid, arthasVersion }
```

**关键设计决策**：
- Arthas HTTP API **只监听 127.0.0.1**，不暴露到外网，由 Agent 转发请求，安全可控
- 使用 `--attach-only` 模式，不启动 Telnet，只启动 HTTP API
- arthas-boot.jar 内置到 Agent 包（约 150KB），首次运行时自动下载完整 arthas 到 `{agentDataPath}/arthas/` 目录

#### 4.2.4 会话管理与自动清理

```
ArthasSessionManager：
- ConcurrentHashMap<Long, ArthasSession>  // pid → session
- 定时任务（每 30s）：
  - 检查 lastActiveTime，超过 10 分钟无活动 → 自动 detach
  - 检查目标 PID 是否还存活，进程退出 → 清理会话
  - 检查 arthas 进程是否残留，异常退出 → 清理端口映射
- Agent 关闭时：遍历所有会话，执行 detach
```

### 4.3 Server 侧设计

#### 4.3.1 新增文件

```
backend/server/src/main/java/com/ops/server/
├── controller/
│   └── ArthasDiagnoseController.java   # 诊断 API 入口
├── arthas/
│   ├── ArthasDiagnoseService.java      # 诊断业务编排
│   └── ArthasAgentProxy.java           # 代理调用 Agent Arthas 接口
├── mapper/
│   └── ArthasDiagnoseRecordMapper.java # 诊断记录持久化
└── model/
    └── ArthasDiagnoseRecordModel.java  # 诊断记录实体
```

#### 4.3.2 数据库表设计

**表1：arthas_diagnose_record（诊断会话记录）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| project_id | BIGINT | 项目 ID |
| node_id | BIGINT | 节点 ID |
| pid | INT | 目标进程 PID |
| session_id | VARCHAR(64) | Arthas 会话 ID |
| status | VARCHAR(20) | RUNNING / FINISHED / FAILED / DETACHED |
| trigger_by | VARCHAR(64) | 触发人 |
| start_time | BIGINT | 开始时间戳 |
| end_time | BIGINT | 结束时间戳 |
| summary | TEXT | 诊断摘要（JSON，关键指标快照） |
| tenant_id | BIGINT | 租户 ID |

**表2：arthas_diagnose_result（诊断命令结果，可选，大结果存文件）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| record_id | BIGINT | 关联诊断记录 |
| command | VARCHAR(255) | 执行的命令 |
| command_type | VARCHAR(50) | dashboard / memory / profiler / thread... |
| result_json | TEXT | 结构化结果（小结果直接存） |
| result_file | VARCHAR(512) | 大结果文件路径（heapdump / 火焰图 SVG） |
| exec_time | BIGINT | 执行时间戳 |
| duration_ms | INT | 执行耗时 |

**存储策略**：
- dashboard / memory / thread 等小结果 → 直接存 result_json
- profiler 火焰图 SVG → 存文件（`{serverDataPath}/arthas/{recordId}/profiler.svg`）
- heapdump → 不传到 Server，留在 Agent 本地，提供下载代理

#### 4.3.3 ArthasDiagnoseController API 设计

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/arthas/diagnose/start` | 启动诊断会话（attach） |
| POST | `/api/arthas/diagnose/stop` | 结束诊断会话（detach） |
| GET | `/api/arthas/diagnose/status` | 查询诊断会话状态 |
| POST | `/api/arthas/diagnose/exec` | 执行诊断命令 |
| POST | `/api/arthas/diagnose/profiler/start` | 启动 profiler 采样 |
| POST | `/api/arthas/diagnose/profiler/stop` | 停止 profiler 并获取火焰图 |
| GET | `/api/arthas/diagnose/heapdump` | 触发 heapdump |
| GET | `/api/arthas/diagnose/history` | 诊断历史列表 |
| GET | `/api/arthas/diagnose/detail` | 单次诊断详情 |
| GET | `/api/arthas/diagnose/flamegraph` | 获取火焰图 SVG |

### 4.4 前端设计

#### 4.4.1 入口

在 `AppMonitorView.vue` 的应用列表每行操作列增加 **「JVM 诊断」** 按钮（仅对 processStatus=RUNNING 且有 PID 的 Java 进程启用）。

#### 4.4.2 诊断面板（Drawer 或新页面）

点击后打开右侧抽屉或跳转诊断详情页，顶部显示：

```
┌─────────────────────────────────────────────────────────┐
│ 🔍 JVM 诊断 — order-service (node-01)  PID: 12345      │
│ 状态：● 已连接  Arthas v4.3.3  已运行 02:35            │
│ [一键体检] [生成报告] [结束诊断]                          │
├─────────────────────────────────────────────────────────┤
│ [概览] [内存分析] [线程分析] [火焰图] [方法追踪] [终端]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│              各 Tab 内容区                               │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### 4.4.3 Tab 1：概览（Dashboard）

**自动执行**：进入面板后自动执行 `dashboard`（取一次快照，不持续刷新）+ `memory` + `jvm`

**展示内容**：

| 区域 | 数据 | 组件 |
|------|------|------|
| 线程概览 | 总线程数、RUNNABLE、WAITING、BLOCKED、死锁数 | 统计卡片 |
| 内存概览 | 堆 used/max、老年代 used/max、Eden、Survivor、元空间 | 进度条 + 数值 |
| GC 概览 | YGC 次数/耗时、FGC 次数/耗时、GC 总耗时 | 统计卡片 |
| CPU/系统 | 进程 CPU%、系统负载、JVM 版本、启动时间 | 信息列表 |
| 异常标记 | 老年代使用率 >80% 标红、FGC 频率高亮、死锁标红 | 自动告警提示 |

**一键体检**：自动执行 dashboard + memory + thread -n 5 + vmtool forceGc（对比 GC 前后），生成「体检报告」标注异常项。

#### 4.4.4 Tab 2：内存分析

**操作按钮**：
- `[采集内存快照]` — 执行 `memory`，展示各区域详细数据
- `[对象直方图]` — 执行 `sc -d`（类统计）或 `vmtool getInstances` 按类查实例
- `[生成 Heapdump]` — 执行 `heapdump --live`，生成后提供下载
- `[强制 GC 对比]` — 执行 `vmtool --action forceGc`，对比 GC 前后内存变化

**展示内容**：

1. **内存区域明细表**：
   | 区域 | 已用(MB) | 最大(MB) | 使用率 | 趋势 |
   |------|---------|---------|--------|------|
   | 堆 | 850 | 1024 | 83% 🔴 | ↑ |
   | 老年代 | 720 | 768 | 94% 🔴 | ↑↑ |
   | Eden | 100 | 256 | 39% | — |
   | Survivor | 30 | 64 | 47% | — |
   | 元空间 | 120 | 256 | 47% | — |

2. **Top 占用类列表**（来自 profiler alloc 或 sc 统计）：
   | 排名 | 类名 | 实例数 | 总大小(MB) | 占比 | 操作 |
   |------|------|--------|-----------|------|------|
   | 1 | com.example.Order | 1,200,000 | 480 | 56% | [查看实例] [trace创建] |
   | 2 | com.example.CacheItem | 800,000 | 160 | 19% | [查看实例] |
   | 3 | byte[] | 50,000 | 80 | 9% | — |

3. **Heapdump 下载区**：文件大小、生成时间、下载按钮、「用 MAT 分析」指引

#### 4.4.5 Tab 3：线程分析

**操作按钮**：
- `[CPU Top 5]` — 执行 `thread -n 5`，展示 CPU 最高的 5 个线程及堆栈
- `[死锁检测]` — 执行 `thread -b`，一键检测死锁
- `[全部线程]` — 执行 `thread`，展示所有线程状态统计和列表

**展示内容**：

1. **线程状态统计**：RUNNABLE / WAITING / TIMED_WAITING / BLOCKED 数量饼图

2. **CPU 热点线程表**：
   | 排名 | 线程名 | 状态 | CPU% | 堆栈摘要 | 操作 |
   |------|--------|------|------|---------|------|
   | 1 | http-nio-8080-exec-12 | RUNNABLE | 45% | com.example.OrderService.process(OrderService.java:128) | [展开堆栈] |
   | 2 | GC-thread | RUNNABLE | 30% | java.lang.ref.Finalizer.run | — |

3. **死锁检测结果**：有死锁时红色告警展示，无线程时绿色「未检测到死锁」

4. **线程堆栈详情**：点击展开，完整堆栈可复制

#### 4.4.6 Tab 4：火焰图（Profiler）

**操作区**：
- 事件类型选择：`[CPU] [内存分配 alloc] [锁竞争 lock] [墙钟 wall]`
- 采样时长：`[30s] [60s] [120s] [自定义]`
- `[开始采样]` / `[停止并生成火焰图]`

**展示内容**：

1. **采样状态**：采样中显示倒计时和进度条
2. **火焰图渲染**：SVG 直接嵌入，支持鼠标悬停显示方法名和占比，支持点击缩放
3. **Top 热点方法表**（从火焰图数据提取）：
   | 排名 | 方法 | 占比 | 类型 |
   |------|------|------|------|
   | 1 | com.example.OrderService.process | 38% | 内存分配 |
   | 2 | com.example.CacheManager.get | 22% | 内存分配 |
   | 3 | java.util.HashMap.put | 15% | 内存分配 |

**关键价值**：`alloc` 事件火焰图直接告诉你「哪个方法在分配最多内存」，这就是 FullGC 的元凶定位。

#### 4.4.7 Tab 5：方法追踪

**操作区**：
- 类名输入框（支持模糊匹配，如 `*OrderService`）
- 方法名输入框（支持模糊匹配，如 `*process*`）
- 命令选择：`[trace 调用链] [watch 入参/返回] [monitor 统计] [stack 调用栈]`
- 执行次数：`-n 5`（默认）
- `[执行]`

**展示内容**：

1. **trace 结果**：方法调用树，每一步显示耗时，慢步骤标红
   ```
   `--- OrderService.process() [850ms]
       +--- CacheManager.get() [50ms, 5%]
       +--- OrderMapper.select() [200ms, 23%]
       +--- OrderConverter.convert() [500ms, 59%] 🔴
       `--- NotificationService.send() [100ms, 12%]
   ```

2. **watch 结果**：入参、返回值、异常的 JSON 展示

3. **增强管理**：显示当前已增强的类/方法列表，提供 `[reset 全部]` 按钮

#### 4.4.8 Tab 6：终端（高级用户）

- 基于 xterm 组件
- 自由输入 Arthas 命令
- 支持命令历史、Tab 补全（调用 Agent shell/complete）
- 标注「高级模式，命令直接透传到 Arthas」

---

## 五、诊断工作流（用户视角）

### 5.1 标准 FullGC 排查流程

```
步骤1：发现问题
  └─ 监控页看到某应用 FGC 频繁 / 老年代使用率 90%
     ↓
步骤2：启动诊断
  └─ 点击「JVM诊断」→ Server → Agent attach 到目标 PID
     ↓ （3-5秒，自动完成）
步骤3：一键体检
  └─ 点击「一键体检」→ 自动执行 dashboard + memory + thread -n 5
     → 面板展示：老年代 94% 🔴、FGC 每分钟 3 次 🔴、无死锁 ✅
     ↓
步骤4：定位内存分配热点
  └─ 切到「火焰图」Tab → 选择「内存分配 alloc」→ 采样 30 秒
     → 火焰图显示：OrderService.process 占 38% 内存分配
     → Top 表显示：Order 对象 120 万个，占 480MB
     ↓
步骤5：确认对象来源
  └─ 切到「内存分析」Tab → 对象直方图 → 看到 Order 类实例数 120万
     → 点击「trace创建」→ 自动 trace OrderService.process
     → 看到 process 方法每次 new 1000 个 Order 对象，且被静态 List 持有
     ↓
步骤6：生成诊断报告
  └─ 点击「生成报告」→ 保存所有采集结果到诊断记录
     → 报告包含：异常指标、Top 占用类、热点方法、堆栈证据
     ↓
步骤7：交给开发
  └─ 开发根据报告：OrderService.process 方法有问题，静态 List 泄漏
     → 优化代码：加缓存淘汰、限制 List 大小、用完清理
     ↓
步骤8：结束诊断
  └─ 点击「结束诊断」→ Arthas detach，目标 JVM 恢复原样
```

### 5.2 与现有监控的协同

| 现有监控 | Arthas 诊断 | 关系 |
|---------|------------|------|
| 7×24 小时持续采集 | 手动触发，按需诊断 | 互补：监控发现问题 → Arthas 定位根因 |
| 聚合指标（GC 次数、堆使用率） | 细粒度数据（类、方法、对象、堆栈） | 互补：指标告诉你「有问题」，Arthas 告诉你「为什么」 |
| 低开销，可长期运行 | 有一定开销，短时间使用 | 互补：日常用监控，排障用 Arthas |
| 历史趋势曲线 | 单次快照/采样 | 互补：趋势看变化，快照看细节 |

---

## 六、实施路线图

### 阶段一：MVP（最小可用版）— 预计 3-5 天

**目标**：能 attach、能执行基础命令、能看到 dashboard 和 memory

- [ ] Agent 侧：ArthasController + ArthasSessionManager + attach/detach
- [ ] Agent 侧：arthas-boot.jar 内置 + 首次下载逻辑
- [ ] Server 侧：ArthasDiagnoseController + 透传 exec
- [ ] 前端：监控页增加「JVM诊断」按钮 + 概览 Tab（dashboard + memory）
- [ ] 数据库：arthas_diagnose_record 表

### 阶段二：核心诊断能力 — 预计 5-7 天

**目标**：能定位 FullGC 根因

- [ ] 前端：内存分析 Tab（memory 详情 + heapdump + 对象直方图）
- [ ] 前端：线程分析 Tab（thread -n + 死锁检测 + 堆栈展示）
- [ ] 前端：火焰图 Tab（profiler cpu/alloc + SVG 渲染 + Top 方法表）
- [ ] Server：profiler 结果存储（SVG 文件）+ heapdump 下载代理
- [ ] Agent：异步命令支持（async_exec + pull_results）
- [ ] 一键体检功能

### 阶段三：高级功能 — 预计 3-5 天

**目标**：方法级诊断 + 报告体系

- [ ] 前端：方法追踪 Tab（trace / watch / monitor / stack）
- [ ] 前端：终端 Tab（xterm 自由命令）
- [ ] 诊断报告生成与历史对比
- [ ] 诊断结果分享/导出
- [ ] 增强自动 reset 机制

### 阶段四：优化与打磨 — 预计 2-3 天

- [ ] 性能优化（大结果分页、懒加载）
- [ ] 安全加固（命令白名单、操作审计）
- [ ] 多实例并发控制
- [ ] Docker 环境兼容性验证
- [ ] 文档和使用手册

---

## 七、结论

### 7.1 可行性结论：✅ 完全可行，建议实施

| 维度 | 评估 |
|------|------|
| 技术可行性 | ✅ Arthas HTTP API 提供结构化 JSON，与现有 Agent/Server 架构完美对接 |
| 无侵入性 | ✅ attach 模式，不改代码、不引依赖、不重启，可随时卸载 |
| 功能覆盖 | ✅ 覆盖 FullGC 排查全链路：内存概览 → 分配热点 → 对象实例 → 方法追踪 → 堆转储 |
| 性能影响 | ✅ 可控，手动触发短时间使用，日常监控不受影响 |
| 开发成本 | ⚠️ 中等，约 15-20 人天（含前后端 + 测试） |
| 运维成本 | ✅ 低，arthas-boot.jar 内置，自动管理生命周期 |

### 7.2 核心价值

1. **从「看不到」到「看得清」**：能定位到具体哪个类、哪个方法导致 FullGC
2. **从「猜」到「证据」**：火焰图、对象直方图、堆栈都是实锤，开发不用猜
3. **从「重启大法」到「根因修复」**：有了诊断证据，开发能针对性优化代码
4. **无侵入、零风险**：不影响目标应用，诊断完就卸载
5. **与现有监控互补**：监控发现问题 → Arthas 定位根因 → 开发修复 → 监控验证

### 7.3 关键技术决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 集成模式 | Agent 侧 attach + HTTP API 转发 | 不引入 Tunnel Server，复用现有 Agent 架构，安全可控 |
| Arthas 版本 | 3.7.6 稳定版（或 4.3.3 最新版） | 3.7.x 生产验证充分；4.x 功能更新，需测试兼容性 |
| HTTP API 监听 | 127.0.0.1 随机端口 | 不暴露外网，Agent 转发，安全 |
| heapdump 存储 | Agent 本地，不传到 Server | 文件可能很大，减少 Server 存储压力和网络传输 |
| 火焰图格式 | SVG | 前端可直接渲染，交互性好 |
| 诊断触发 | 纯手动 | 符合需求，避免自动诊断影响生产 |

---

## 附录 A：Arthas 命令与 FullGC 排查映射表

| 排查目标 | 首选命令 | 备选命令 | 结果类型 |
|---------|---------|---------|---------|
| 全局概览（哪块内存异常） | `dashboard` | `memory` | 结构化 |
| 各内存区域详细使用 | `memory` | `jvm` | 结构化 |
| 谁在疯狂分配内存 | `profiler --event alloc` | `trace` + 大对象 | SVG火焰图 |
| 哪个类实例最多 | `sc -d` + `vmtool getInstances` | heapdump + MAT | 结构化 |
| 对象实际内容 | `vmtool --action getInstances` | `watch` | 结构化 |
| 是否内存泄漏 | `vmtool --action forceGc` + 对比 | 多次 heapdump 对比 | 结构化 |
| 全量堆分析 | `heapdump --live` | — | .hprof 文件 |
| CPU 热点方法 | `profiler --event cpu` | `thread -n 5` | SVG火焰图 |
| 锁竞争 | `profiler --event lock` | `thread -b` | SVG火焰图 |
| 死锁检测 | `thread -b` | — | 结构化 |
| 方法调用链耗时 | `trace` | `profiler --event wall` | 树形文本 |
| 方法入参/返回值 | `watch` | `tt` | 结构化 |
| 类加载泄漏 | `classloader` | — | 结构化 |

## 附录 B：参考资料

- Arthas 官网：https://arthas.aliyun.com/
- Arthas 命令列表：https://arthas.aliyun.com/doc/commands.html
- Arthas HTTP API：https://arthas.aliyun.com/doc/http-api.html
- Arthas profiler：https://arthas.aliyun.com/en/doc/profiler.html
- Arthas vmtool：https://arthas.aliyun.com/3.x/doc/vmtool.html
- Arthas Tunnel：https://arthas.aliyun.com/3.x/doc/tunnel.html
- GitHub：https://github.com/alibaba/arthas
