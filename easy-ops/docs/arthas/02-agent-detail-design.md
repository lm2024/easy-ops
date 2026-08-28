# Arthas 集成 — Agent 侧详细设计

> 版本：v1.0 | 日期：2026-08-28 | 关联：01-architecture-design.md

---

## 1. 目录结构

```
backend/agent/src/main/java/com/ops/agent/
├── controller/
│   └── ArthasController.java              # REST 接口（新增）
├── arthas/                                 # 新增包
│   ├── ArthasSessionManager.java          # 会话管理器（核心）
│   ├── ArthasBootstrap.java               # 启动器
│   ├── ArthasHttpClient.java              # Arthas HTTP API 客户端
│   ├── ArthasPortAllocator.java           # 端口分配器
│   ├── ArthasSession.java                 # 会话模型
│   └── ArthasCommandType.java             # 命令类型枚举
└── resources/
    └── arthas/                             # 内置 Arthas 完整包（构建时注入）
        ├── arthas-boot.jar
        ├── arthas-agent.jar
        ├── arthas-core.jar
        ├── arthas-spy.jar
        └── async-profiler/
            └── libasyncProfiler.so
```

---

## 2. ArthasSession（会话模型）

```java
package com.ops.agent.arthas;

public class ArthasSession {
    private long pid;                    // 目标进程 PID
    private int port;                    // Arthas HTTP API 端口
    private String sessionId;            // Arthas 会话 ID（HTTP API 返回）
    private String arthasVersion;        // Arthas 版本
    private long attachTime;             // attach 时间戳
    private long lastActiveTime;         // 最后活动时间戳
    private Process arthasProcess;       // arthas-boot 进程
    private String projectId;            // 关联项目 ID（透传）
    private String nodeId;               // 关联节点 ID（透传）
    private volatile boolean attached;   // 是否已 attach 成功

    // getter/setter 省略
}
```

---

## 3. ArthasPortAllocator（端口分配器）

### 3.1 职责
- 在 30000-60000 范围内分配随机端口
- 检测端口是否可用（未被占用）
- 追踪已分配端口，释放时归还

### 3.2 核心逻辑

```java
public class ArthasPortAllocator {
    private static final int PORT_MIN = 30000;
    private static final int PORT_MAX = 60000;
    private final Set<Integer> allocated = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    /** 分配一个可用端口，最多尝试 50 次 */
    public synchronized int allocate() {
        for (int i = 0; i < 50; i++) {
            int port = PORT_MIN + random.nextInt(PORT_MAX - PORT_MIN);
            if (allocated.contains(port)) continue;
            if (isPortAvailable(port)) {
                allocated.add(port);
                return port;
            }
        }
        throw new RuntimeException("无法分配可用端口（30000-60000范围）");
    }

    /** 释放端口 */
    public void release(int port) {
        allocated.remove(port);
    }

    /** 检测端口是否可用（尝试连接） */
    private boolean isPortAvailable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
            return false; // 能连上说明被占用
        } catch (IOException e) {
            return true;  // 连不上说明可用
        }
    }
}
```

---

## 4. ArthasBootstrap（启动器）

### 4.1 职责
- 检查 Arthas 完整包是否已释放到 data 目录
- 未释放则从 Agent jar 内资源释放
- 构建 arthas-boot.jar 启动命令
- 启动进程并等待 Arthas HTTP API 就绪

### 4.2 启动命令构建

```bash
java -jar {agentDataPath}/arthas/arthas-boot.jar
  --attach-only                    # 只 attach，不启动 Telnet
  --target-pid {pid}               # 目标 PID
  --http-port {port}               # HTTP API 端口（随机分配）
  --http-ip 127.0.0.1              # 只监听本地
  --tunnel-server ""                # 不使用 Tunnel Server
  --session-timeout 3600            # 会话超时 1 小时
  --arthas-home {agentDataPath}/arthas  # 指定 arthas 家目录（用本地包）
```

### 4.3 就绪检测

启动后轮询 `http://127.0.0.1:{port}/api`，发送 `version` 命令：
- 最多等待 15 秒
- 每 500ms 检测一次
- 成功返回 version 即视为就绪
- 超时则 kill 进程并报错

### 4.4 资源释放逻辑

```
Agent 启动时 (@PostConstruct):
  1. 检查 {agentDataPath}/arthas/arthas-boot.jar 是否存在
  2. 不存在 → 从 classpath:/arthas/ 释放所有文件
  3. 存在 → 读取版本文件，与内置版本对比
  4. 版本不一致 → 删除旧文件，重新释放
  5. 设置 async-profiler 原生库可执行权限
```

---

## 5. ArthasHttpClient（HTTP API 客户端）

### 5.1 职责
- 封装对 Arthas HTTP API 的调用
- 支持 exec（同步）、async_exec（异步）、pull_results、interrupt_job
- 解析响应 JSON，提取 results
- 错误处理和重试

### 5.2 核心方法

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| exec | port, command, timeoutMs | ArthasResult | 同步执行命令 |
| asyncExec | port, sessionId, command | String (jobId) | 异步执行命令 |
| pullResults | port, sessionId, consumerId | List<ArthasResult> | 拉取异步结果 |
| interruptJob | port, sessionId | boolean | 中断当前命令 |
| initSession | port | String (sessionId) | 创建会话 |
| joinSession | port, sessionId | String (consumerId) | 加入会话 |

### 5.3 请求格式

```java
// 同步执行
Map<String, Object> req = new HashMap<>();
req.put("action", "exec");
req.put("command", command);
req.put("execTimeout", timeoutMs);

// POST http://127.0.0.1:{port}/api
// Content-Type: application/json
```

### 5.4 响应解析

```java
// 响应结构
{
  "state": "SUCCEEDED",
  "sessionId": "xxx",
  "body": {
    "results": [
      { "type": "memory", ... },   // 命令结果
      { "type": "status", "statusCode": 0 }  // 状态
    ],
    "jobStatus": "TERMINATED"
  }
}

// 提取 type != "status" 的结果作为命令输出
// 检查 status.statusCode != 0 则视为命令失败
```

---

## 6. ArthasSessionManager（会话管理器，核心）

### 6.1 职责
- 管理所有活跃的 Arthas 会话（pid → session）
- attach / detach 生命周期管理
- 超时自动清理（10 分钟无活动）
- 残留检测与清理
- Agent 关闭时全量 detach
- 并发控制（单 Agent 最多 2 个同时诊断）

### 6.2 核心数据结构

```java
@Service
public class ArthasSessionManager {
    // pid → session
    private final ConcurrentHashMap<Long, ArthasSession> sessions = new ConcurrentHashMap<>();
    // 并发控制
    private static final int MAX_CONCURRENT_SESSIONS = 2;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_SESSIONS);
    // 依赖
    private final ArthasPortAllocator portAllocator;
    private final ArthasBootstrap bootstrap;
    private final ArthasHttpClient httpClient;
}
```

### 6.3 attach 流程

```
attach(pid, projectId, nodeId):
  1. 检查信号量（最多2个并发），获取不到则报错"诊断会话已满，请稍后重试"
  2. 检查 PID 是否存活（ProcessStatusChecker.findPid 或 /proc/{pid}）
  3. 检查是否已 attach（sessions.containsKey(pid)），已存在则直接返回
  4. 分配随机端口（portAllocator.allocate()）
  5. 启动 arthas-boot.jar（bootstrap.start(pid, port)）
  6. 等待 HTTP API 就绪（最多15秒）
  7. 调用 version 命令获取 arthasVersion
  8. 创建 ArthasSession，存入 sessions
  9. 更新 lastActiveTime
  10. 返回 session 信息

  失败回滚：
    - kill arthas 进程
    - 释放端口
    - 释放信号量
    - 抛出异常
```

### 6.4 detach 流程

```
detach(pid):
  1. 从 sessions 取出 session
  2. 调用 Arthas HTTP API 执行 "stop" 命令（优雅退出）
  3. 等待 arthas 进程退出（最多5秒）
  4. 进程未退出则 destroyForcibly
  5. 释放端口（portAllocator.release(port)）
  6. 从 sessions 移除
  7. 释放信号量
```

### 6.5 命令执行流程

```
exec(pid, command, timeoutMs):
  1. 从 sessions 取出 session，不存在则报错"未 attach"
  2. 更新 lastActiveTime
  3. 调用 httpClient.exec(session.port, command, timeoutMs)
  4. 返回解析后的结果
```

### 6.6 超时清理定时任务

```
@Scheduled(fixedDelay = 30000)  // 每30秒检查一次
cleanupTimeoutSessions():
  遍历 sessions:
    if (now - lastActiveTime > 10分钟):
      detach(pid)
      log.info("Arthas 会话超时自动 detach: pid={}", pid)
```

### 6.7 残留清理（Agent 启动时）

```
@PostConstruct
cleanupResidual():
  1. 扫描 30000-60000 端口，查找可能残留的 Arthas HTTP API
  2. 对每个发现的端口，发送 "stop" 命令
  3. 查找残留的 arthas-boot.jar 进程（ps aux | grep arthas-boot）
  4. kill 残留进程
  5. 清理 heapdump 目录中超过 7 天的文件
```

### 6.8 Agent 关闭时全量 detach

```
@PreDestroy
shutdown():
  遍历所有 sessions:
    detach(pid)
  等待所有进程退出
```

---

## 7. ArthasController（REST 接口）

### 7.1 接口清单

| 方法 | 路径 | 请求体 | 功能 |
|------|------|--------|------|
| POST | /api/arthas/attach | {pid, projectId, nodeId} | attach 到 PID |
| POST | /api/arthas/detach | {pid} | 卸载 |
| POST | /api/arthas/exec | {pid, command, timeoutMs} | 同步执行命令 |
| POST | /api/arthas/async-exec | {pid, command} | 异步执行命令 |
| GET | /api/arthas/pull | pid, sessionId, consumerId | 拉取异步结果 |
| POST | /api/arthas/interrupt | {pid} | 中断当前命令 |
| GET | /api/arthas/status | pid（可选） | 会话状态/列表 |
| GET | /api/arthas/heapdump/download | pid, fileName | 下载 heapdump（流式） |

### 7.2 attach 接口实现要点

```java
@PostMapping("/attach")
public Result<Map<String, Object>> attach(@RequestBody Map<String, Object> body) {
    long pid = Long.parseLong(body.get("pid").toString());
    String projectId = (String) body.get("projectId");
    String nodeId = (String) body.get("nodeId");

    try {
        ArthasSession session = sessionManager.attach(pid, projectId, nodeId);
        Map<String, Object> data = new HashMap<>();
        data.put("pid", session.getPid());
        data.put("port", session.getPort());
        data.put("sessionId", session.getSessionId());
        data.put("arthasVersion", session.getArthasVersion());
        data.put("attachTime", session.getAttachTime());
        data.put("status", "ATTACHED");
        return Result.success(data);
    } catch (Exception e) {
        return Result.error(500, "attach 失败: " + e.getMessage());
    }
}
```

### 7.3 exec 接口实现要点

```java
@PostMapping("/exec")
public Result<Map<String, Object>> exec(@RequestBody Map<String, Object> body) {
    long pid = Long.parseLong(body.get("pid").toString());
    String command = (String) body.get("command");
    int timeoutMs = body.containsKey("timeoutMs")
        ? Integer.parseInt(body.get("timeoutMs").toString()) : 30000;

    // 命令白名单校验（MVP 阶段）
    if (!ArthasCommandType.isAllowed(command)) {
        return Result.error(403, "命令不在白名单中: " + command);
    }

    try {
        ArthasResult result = sessionManager.exec(pid, command, timeoutMs);
        Map<String, Object> data = new HashMap<>();
        data.put("state", result.getState());
        data.put("results", result.getResults());
        data.put("commandType", ArthasCommandType.detect(command));
        data.put("durationMs", result.getDurationMs());
        return Result.success(data);
    } catch (Exception e) {
        return Result.error(500, "命令执行失败: " + e.getMessage());
    }
}
```

### 7.4 heapdump 下载接口

```java
@GetMapping("/heapdump/download")
public void downloadHeapdump(@RequestParam long pid,
                              @RequestParam String fileName,
                              HttpServletResponse response) {
    // 安全校验：fileName 不能包含 ../，必须在 heapdump 目录内
    File file = new File(agentDataPath + "/arthas/heapdump/", fileName);
    if (!file.exists() || !file.getCanonicalPath()
            .startsWith(new File(agentDataPath + "/arthas/heapdump/").getCanonicalPath())) {
        response.setStatus(404);
        return;
    }
    // 流式输出
    response.setContentType("application/octet-stream");
    response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
    Files.copy(file.toPath(), response.getOutputStream());
}
```

---

## 8. ArthasCommandType（命令类型枚举）

### 8.1 白名单命令

| 命令类型 | 命令前缀 | 说明 |
|---------|---------|------|
| DASHBOARD | dashboard | 实时面板 |
| MEMORY | memory | 内存信息 |
| JVM | jvm | JVM 信息 |
| THREAD | thread | 线程信息 |
| HEAPDUMP | heapdump | 堆转储 |
| PROFILER | profiler | 火焰图采样 |
| VMTOOL | vmtool | JVMTI 工具 |
| JAD | jad | 反编译 |
| SC | sc | 搜索类 |
| SM | sm | 搜索方法 |
| CLASSLOADER | classloader | 类加载器 |
| VMOPTION | vmoption | JVM 选项 |
| PERFCOUNTER | perfcounter | 性能计数器 |
| SYSENV | sysenv | 环境变量 |
| SYSPROP | sysprop | 系统属性 |

### 8.2 禁用命令（MVP 阶段）

| 命令 | 原因 |
|------|------|
| redefine | 热更新代码，风险高 |
| retransform | 类重转换，风险高 |
| stop | 停止 Arthas（由 detach 管理） |
| shutdown | 关闭目标 JVM，极度危险 |
| quit / exit | 退出会话（由 detach 管理） |
| ognl | 任意代码执行，风险高（高级阶段可开放） |
| mc | 内存编译，配合 redefine 使用 |

---

## 9. 配置项

在 `application.yml` 中新增：

```yaml
agent:
  arthas:
    enabled: true                    # 是否启用 Arthas 诊断
    max-concurrent-sessions: 2       # 最大并发诊断数
    session-timeout-minutes: 10      # 会话超时（分钟）
    port-range:                       # 端口范围
      min: 30000
      max: 60000
    attach-timeout-seconds: 15       # attach 超时
    command-timeout-ms: 30000        # 命令默认超时
    heapdump:
      dir: ${agent.data-path}/arthas/heapdump  # heapdump 存储目录
      retention-days: 7              # heapdump 保留天数
      max-size-mb: 2048              # 单文件最大大小（MB）
    arthas-home: ${agent.data-path}/arthas  # Arthas 家目录
```

---

## 10. 异常处理

| 异常场景 | 处理方式 |
|---------|---------|
| PID 不存在 | 返回 404，提示"进程不存在" |
| PID 不是 Java 进程 | 返回 400，提示"目标进程不是 Java 进程" |
| attach 超时 | kill 进程，释放端口，返回 504 |
| 并发数已满 | 返回 429，提示"诊断会话已满（最多2个），请稍后重试" |
| 命令执行超时 | 中断命令，返回超时错误 |
| Arthas HTTP API 连接失败 | 标记会话异常，尝试 detach，返回错误 |
| heapdump 文件过大 | 返回 413，提示"堆转储文件超过限制" |
| 命令不在白名单 | 返回 403，提示命令被禁用 |

---

## 11. 日志规范

遵循项目现有日志规范：
- 中文日志
- 异常补全堆栈
- 高频操作 DEBUG，关键操作 INFO，可恢复 WARN，严重 ERROR

```
INFO: Arthas attach成功: pid=12345, port=34567, version=3.7.6
INFO: Arthas detach成功: pid=12345, duration=305s
INFO: Arthas 命令执行: pid=12345, command=memory, duration=120ms, success=true
WARN: Arthas 会话超时自动 detach: pid=12345, idle=605s
ERROR: Arthas attach失败: pid=12345, error=连接超时
DEBUG: Arthas 端口分配: port=34567
```

---

## 12. 与现有模块的交互

### 12.1 ProcessStatusChecker
- attach 前调用 `findPid` 确认进程存活
- 复用其 PID 发现逻辑

### 12.2 AgentApplication
- `@PostConstruct` 触发 Arthas 资源释放和残留清理
- `@PreDestroy` 触发全量 detach

### 12.3 HeartbeatDaemon
- 心跳上报中可附带 `arthasSessionCount`（可选，MVP 不做）

### 12.4 WebSocketClient
- 诊断状态变化时可推送到 Server（可选，MVP 用 HTTP 轮询）
