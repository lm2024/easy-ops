# Arthas 集成 — Server 侧详细设计

> 版本：v1.0 | 日期：2026-08-28 | 关联：01-architecture-design.md

---

## 1. 目录结构

```
backend/server/src/main/java/com/ops/server/
├── controller/
│   └── ArthasDiagnoseController.java       # REST 接口（新增）
├── arthas/                                    # 新增包
│   ├── ArthasDiagnoseService.java            # 业务编排（核心）
│   ├── ArthasAgentProxy.java                 # Agent 调用代理
│   ├── ArthasProfilerTaskManager.java        # profiler 异步任务管理
│   ├── ArthasFileService.java                # 文件存储服务
│   └── ArthasCleanupScheduler.java           # 定时清理任务
├── mapper/
│   ├── ArthasDiagnoseRecordMapper.java       # 诊断记录 Mapper（新增）
│   └── ArthasDiagnoseResultMapper.java       # 诊断结果 Mapper（新增）
└── model/
    ├── ArthasDiagnoseRecordModel.java        # 诊断记录实体（新增）
    └── ArthasDiagnoseResultModel.java        # 诊断结果实体（新增）

backend/server/src/main/resources/
└── mapper/
    ├── ArthasDiagnoseRecordMapper.xml        # MyBatis XML（新增）
    └── ArthasDiagnoseResultMapper.xml        # MyBatis XML（新增）
```

---

## 2. 数据模型

### 2.1 ArthasDiagnoseRecordModel

```java
package com.ops.server.model;

public class ArthasDiagnoseRecordModel {
    private Long id;
    private String sessionId;       // Arthas 会话 ID
    private Long projectId;
    private Long nodeId;
    private Integer pid;
    private String jarName;
    private String status;          // RUNNING/FINISHED/FAILED/DETACHED
    private String triggerBy;
    private String arthasVersion;
    private Long startTime;
    private Long endTime;
    private Integer durationMs;
    private String summary;         // JSON: 关键指标快照
    private String exception;
    private Long tenantId;
    private Long createdAt;
    private Long updatedAt;
    // getter/setter 省略
}
```

### 2.2 ArthasDiagnoseResultModel

```java
public class ArthasDiagnoseResultModel {
    private Long id;
    private Long recordId;
    private String command;
    private String commandType;     // dashboard/memory/thread/profiler等
    private String resultJson;      // 小结果直接存 JSON
    private String resultFile;      // 大结果文件路径
    private Integer resultSizeKb;
    private Long execTime;
    private Integer durationMs;
    private Boolean success;
    private String errorMsg;
    private Long tenantId;
    // getter/setter 省略
}
```

---

## 3. 数据库初始化

### 3.1 建表 SQL（加入现有 schema.sql 或独立 arthas-schema.sql）

```sql
-- 诊断会话记录
CREATE TABLE IF NOT EXISTS arthas_diagnose_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    pid INT NOT NULL,
    jar_name VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    trigger_by VARCHAR(64),
    arthas_version VARCHAR(20),
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    duration_ms INT,
    summary CLOB,
    exception CLOB,
    tenant_id BIGINT,
    created_at BIGINT,
    updated_at BIGINT
);
CREATE INDEX IF NOT EXISTS idx_arthas_record_project ON arthas_diagnose_record(project_id);
CREATE INDEX IF NOT EXISTS idx_arthas_record_node ON arthas_diagnose_record(node_id);
CREATE INDEX IF NOT EXISTS idx_arthas_record_start ON arthas_diagnose_record(start_time);
CREATE INDEX IF NOT EXISTS idx_arthas_record_tenant ON arthas_diagnose_record(tenant_id);

-- 命令执行结果
CREATE TABLE IF NOT EXISTS arthas_diagnose_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    command VARCHAR(500) NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    result_json CLOB,
    result_file VARCHAR(512),
    result_size_kb INT,
    exec_time BIGINT NOT NULL,
    duration_ms INT,
    success TINYINT NOT NULL,
    error_msg CLOB,
    tenant_id BIGINT
);
CREATE INDEX IF NOT EXISTS idx_arthas_result_record ON arthas_diagnose_result(record_id);
CREATE INDEX IF NOT EXISTS idx_arthas_result_type ON arthas_diagnose_result(command_type);
CREATE INDEX IF NOT EXISTS idx_arthas_result_exec ON arthas_diagnose_result(exec_time);
```

### 3.2 数据清理配置（application.yml）

```yaml
ops:
  arthas:
    record-retention-days: 90       # 诊断记录保留天数
    result-retention-days: 90       # 结果保留天数
    file-retention-days: 30          # 火焰图等文件保留天数
    cleanup-cron: "0 0 3 * * ?"     # 每天凌晨3点清理
```

---

## 4. ArthasAgentProxy（Agent 调用代理）

### 4.1 职责
- 封装 AgentClient，调用 Agent 侧 `/api/arthas/*` 接口
- 统一处理 Agent 调用异常
- 超时控制和重试

### 4.2 方法清单

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| attach | nodeId, pid, projectId | ArthasSessionInfo | attach 到目标 PID |
| detach | nodeId, pid | void | 卸载 |
| exec | nodeId, pid, command, timeoutMs | ArthasCommandResult | 同步执行命令 |
| asyncExec | nodeId, pid, command | String (jobId) | 异步执行命令 |
| pullResults | nodeId, pid, sessionId, consumerId | List | 拉取异步结果 |
| interrupt | nodeId, pid | void | 中断命令 |
| getStatus | nodeId, pid | ArthasSessionInfo | 查询会话状态 |
| heapdumpDownload | nodeId, pid, fileName | InputStream | 下载 heapdump |

### 4.3 实现要点

```java
@Service
public class ArthasAgentProxy {
    @Autowired
    private AgentClient agentClient;

    public ArthasSessionInfo attach(Long nodeId, long pid, Long projectId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) throw new RuntimeException("节点不存在");
        String url = "http://" + node.getIp() + ":" + node.getPort() + "/api/arthas/attach";
        Map<String, Object> body = new HashMap<>();
        body.put("pid", pid);
        body.put("projectId", projectId);
        body.put("nodeId", nodeId);
        // 调用 AgentClient.post(url, body)
        // 解析返回 Result<Map>
        // 返回 ArthasSessionInfo
    }
}
```

---

## 5. ArthasDiagnoseService（业务编排，核心）

### 5.1 职责
- 诊断会话生命周期管理（start/stop）
- 命令透传与结果持久化
- profiler 异步任务编排
- 诊断报告生成
- 历史记录查询
- 租户隔离和权限校验

### 5.2 核心方法

| 方法 | 功能 |
|------|------|
| startDiagnose(projectId, nodeId, pid) | 启动诊断会话 |
| stopDiagnose(sessionId) | 结束诊断会话 |
| getStatus(sessionId) | 查询会话状态 |
| execCommand(sessionId, command, timeoutMs) | 执行命令并持久化结果 |
| startProfiler(sessionId, event, duration) | 启动 profiler 采样 |
| stopProfiler(sessionId, taskId) | 停止 profiler 并生成火焰图 |
| triggerHeapdump(sessionId, live) | 触发堆转储 |
| getHistory(projectId, page, pageSize) | 诊断历史列表 |
| getDetail(id) | 单次诊断详情 |
| getFlamegraph(recordId, taskId) | 获取火焰图 SVG |
| generateReport(sessionId) | 生成诊断报告 |

### 5.3 startDiagnose 流程

```
startDiagnose(projectId, nodeId, pid):
  1. 权限校验：securityContext.hasProjectPermission(projectId)
  2. 租户校验：resourceAccess.requireNode(nodeId)
  3. 查询项目和节点信息
  4. 生成 sessionId (UUID)
  5. 创建诊断记录（status=RUNNING, startTime=now），存入 DB
  6. 调用 arthasAgentProxy.attach(nodeId, pid, projectId)
  7. attach 成功 → 更新记录（arthasVersion, sessionId）
  8. attach 失败 → 更新记录（status=FAILED, exception），抛出异常
  9. 返回会话信息
```

### 5.4 execCommand 流程

```
execCommand(sessionId, command, timeoutMs):
  1. 查询诊断记录，确认 status=RUNNING
  2. 命令白名单校验（复用 ArthasCommandType）
  3. 调用 arthasAgentProxy.exec(nodeId, pid, command, timeoutMs)
  4. 解析结果，判断 success
  5. 持久化结果到 arthas_diagnose_result
     - 结果 < 100KB → 存 result_json
     - 结果 ≥ 100KB → 存文件，result_file 存路径
  6. 更新记录的 updatedAt
  7. 返回结果给前端
```

### 5.5 stopDiagnose 流程

```
stopDiagnose(sessionId):
  1. 查询诊断记录
  2. 调用 arthasAgentProxy.detach(nodeId, pid)
  3. 生成诊断摘要（从已执行的结果中提取关键指标）
  4. 更新记录（status=FINISHED, endTime, durationMs, summary）
  5. 返回
```

### 5.6 诊断摘要生成（summary JSON）

```json
{
  "pid": 12345,
  "arthasVersion": "3.7.6",
  "heapUsedMb": 850,
  "heapMaxMb": 1024,
  "oldGenUsedPercent": 94,
  "fgcCount": 156,
  "fgcTimeMs": 12500,
  "threadCount": 85,
  "deadlockCount": 0,
  "topCpuThread": "http-nio-8080-exec-12",
  "topMemoryClass": "com.example.Order",
  "topMemoryClassInstances": 1200000,
  "profilerEvents": ["alloc"],
  "commandCount": 8,
  "durationMs": 305000
}
```

---

## 6. ArthasProfilerTaskManager（profiler 任务管理）

### 6.1 职责
- 管理 profiler 异步采样任务
- 支持 cpu/alloc/lock/wall 四种事件
- 定时自动停止（duration 到了自动 stop）
- 火焰图 SVG 文件存储

### 6.2 数据结构

```java
public class ArthasProfilerTaskManager {
    // taskId → ProfilerTask
    private final ConcurrentHashMap<String, ProfilerTask> tasks = new ConcurrentHashMap<>();
    // 定时任务线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    static class ProfilerTask {
        String taskId;
        String sessionId;
        Long nodeId;
        Long pid;
        String event;          // cpu/alloc/lock/wall
        int duration;          // 秒
        long startTime;
        ScheduledFuture<?> stopFuture;
        String status;         // SAMPLING/COMPLETED/FAILED
        String flamegraphPath; // SVG 文件路径
        List<Map<String, Object>> topMethods;
    }
}
```

### 6.3 startProfiler 流程

```
startProfiler(sessionId, event, duration):
  1. 查询诊断记录，获取 nodeId 和 pid
  2. 生成 taskId (prof_ + UUID 短码)
  3. 调用 Agent 执行命令: "profiler start --event {event} --duration {duration}"
     或分步: profiler start → 等待 → profiler stop --format svg
  4. 创建 ProfilerTask，存入 tasks
  5. 调度定时任务：duration 秒后自动调用 stopProfiler
  6. 返回 taskId 和状态
```

### 6.4 stopProfiler 流程

```
stopProfiler(sessionId, taskId):
  1. 从 tasks 取出 ProfilerTask
  2. 取消定时任务
  3. 调用 Agent 执行: "profiler stop --format svg --file /tmp/{taskId}.svg"
  4. 从 Agent 下载 SVG 文件（或 Agent 返回文件内容）
  5. 存储到 Server 本地: {serverDataPath}/arthas/{recordId}/profiler-{event}-{taskId}.svg
  6. 解析 SVG 或 Arthas 返回的 Top 方法列表
  7. 更新任务状态为 COMPLETED
  8. 持久化结果到 arthas_diagnose_result（command_type=profiler, result_file=路径）
  9. 返回火焰图 URL 和 Top 方法
```

---

## 7. ArthasFileService（文件服务）

### 7.1 职责
- 火焰图 SVG 文件的存储和读取
- 诊断报告 JSON 文件存储
- 文件路径管理和安全校验

### 7.2 目录结构

```
{serverDataPath}/arthas/
└── {recordId}/
    ├── profiler-cpu-{taskId}.svg
    ├── profiler-alloc-{taskId}.svg
    ├── profiler-lock-{taskId}.svg
    ├── profiler-wall-{taskId}.svg
    └── report.json
```

### 7.3 核心方法

| 方法 | 功能 |
|------|------|
| saveFlamegraph(recordId, taskId, event, svgContent) | 保存火焰图 SVG |
| getFlamegraph(recordId, taskId) | 读取火焰图 SVG 内容 |
| saveReport(recordId, reportJson) | 保存诊断报告 |
| getReport(recordId) | 读取诊断报告 |
| cleanupExpiredFiles(beforeDate) | 清理过期文件 |

---

## 8. ArthasCleanupScheduler（定时清理）

### 8.1 职责
- 清理过期的诊断记录和结果
- 清理过期的火焰图文件
- 防止 H2 数据库膨胀

### 8.2 执行逻辑

```java
@Scheduled(cron = "${ops.arthas.cleanup-cron:0 0 3 * * ?}")
public void cleanup() {
    long cutoff = System.currentTimeMillis() - retentionDays * 86400000L;

    // 1. 查询过期记录 ID 列表
    List<Long> expiredRecordIds = recordMapper.findExpiredIds(cutoff);

    // 2. 删除关联的结果
    resultMapper.deleteByRecordIds(expiredRecordIds);

    // 3. 删除关联的文件
    for (Long recordId : expiredRecordIds) {
        fileService.deleteRecordFiles(recordId);
    }

    // 4. 删除记录
    recordMapper.deleteByIds(expiredRecordIds);

    log.info("Arthas 清理完成: 删除 {} 条过期记录", expiredRecordIds.size());
}
```

---

## 9. ArthasDiagnoseController（REST 接口）

### 9.1 接口清单

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| POST | /api/arthas/diagnose/start | 项目权限 | 启动诊断会话 |
| POST | /api/arthas/diagnose/stop | 项目权限 | 结束诊断会话 |
| GET | /api/arthas/diagnose/status | 项目权限 | 查询会话状态 |
| POST | /api/arthas/diagnose/exec | 项目权限 | 执行命令 |
| POST | /api/arthas/diagnose/profiler/start | 项目权限 | 启动 profiler |
| POST | /api/arthas/diagnose/profiler/stop | 项目权限 | 停止 profiler |
| GET | /api/arthas/diagnose/heapdump | 项目权限 | 触发堆转储 |
| GET | /api/arthas/diagnose/heapdump/download | 项目权限 | 下载 heapdump |
| GET | /api/arthas/diagnose/history | 项目权限 | 诊断历史列表 |
| GET | /api/arthas/diagnose/detail | 项目权限 | 诊断详情 |
| GET | /api/arthas/diagnose/flamegraph | 项目权限 | 获取火焰图 SVG |

### 9.2 接口实现要点

```java
@RestController
@RequestMapping("/arthas")
public class ArthasDiagnoseController {

    @Autowired
    private ArthasDiagnoseService diagnoseService;
    @Autowired
    private SecurityContext securityContext;

    @PostMapping("/diagnose/start")
    public Result<?> start(@RequestBody Map<String, Object> body) {
        Long projectId = Long.parseLong(body.get("projectId").toString());
        Long nodeId = Long.parseLong(body.get("nodeId").toString());
        long pid = Long.parseLong(body.get("pid").toString());

        // 权限校验
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        return Result.success(diagnoseService.startDiagnose(projectId, nodeId, pid));
    }

    @PostMapping("/diagnose/exec")
    public Result<?> exec(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        String command = (String) body.get("command");
        int timeoutMs = body.containsKey("timeoutMs")
            ? Integer.parseInt(body.get("timeoutMs").toString()) : 30000;
        return Result.success(diagnoseService.execCommand(sessionId, command, timeoutMs));
    }

    @GetMapping("/diagnose/flamegraph")
    public void flamegraph(@RequestParam Long recordId,
                            @RequestParam String taskId,
                            HttpServletResponse response) {
        // 权限校验
        // 读取 SVG 文件
        // 设置 Content-Type: image/svg+xml
        // 流式输出
    }
}
```

---

## 10. Mapper 设计

### 10.1 ArthasDiagnoseRecordMapper

| 方法 | 功能 |
|------|------|
| insert(record) | 插入记录 |
| updateById(record) | 更新记录 |
| findById(id) | 按 ID 查询 |
| findBySessionId(sessionId) | 按会话 ID 查询 |
| findByProjectId(projectId, page, pageSize) | 按项目分页查询 |
| findLatestByProjectNode(projectId, nodeId) | 查询项目节点最新记录 |
| findExpiredIds(cutoffTime) | 查询过期记录 ID |
| deleteByIds(ids) | 批量删除 |
| countByProjectId(projectId) | 统计项目诊断次数 |

### 10.2 ArthasDiagnoseResultMapper

| 方法 | 功能 |
|------|------|
| insert(result) | 插入结果 |
| findByRecordId(recordId) | 按记录 ID 查询所有结果 |
| findByRecordIdAndType(recordId, commandType) | 按记录和类型查询 |
| deleteByRecordIds(recordIds) | 批量删除 |
| findLatestByRecordId(recordId, limit) | 查询最近 N 条结果 |

---

## 11. 租户隔离

遵循项目现有租户隔离规范：

| 操作 | 隔离规则 |
|------|---------|
| 创建诊断记录 | 自动设置 tenantId（从 SecurityContext 获取） |
| 查询历史列表 | 按 tenantId 过滤（admin 超级管理员放行） |
| 查询详情 | 校验记录的 tenantId 与当前用户一致 |
| 执行命令 | 通过 sessionId → record → projectId → 权限校验 |
| 清理任务 | 按 tenantId 分别清理（或全局清理，不跨租户） |

---

## 12. 异常处理

所有异常走 `GlobalExceptionHandler`，统一返回 `Result<T>`。

| 异常场景 | HTTP 状态 | 错误码 | 提示 |
|---------|----------|--------|------|
| 项目无权限 | 403 | 403 | 无权限访问该项目 |
| 节点不存在 | 404 | 1004 | 节点不存在 |
| 进程不存在 | 404 | 2001 | 目标进程不存在或已退出 |
| 会话不存在 | 404 | 2002 | 诊断会话不存在或已结束 |
| attach 失败 | 500 | 2003 | Arthas attach 失败: {原因} |
| 命令超时 | 504 | 2004 | 命令执行超时 |
| 命令不在白名单 | 403 | 2005 | 命令被禁用: {命令} |
| 并发数已满 | 429 | 2006 | 诊断会话已满，请稍后重试 |
| heapdump 过大 | 413 | 2007 | 堆转储文件超过大小限制 |
| Agent 不可达 | 502 | 2008 | Agent 不可达，请检查节点状态 |

---

## 13. 与现有模块的集成

### 13.1 AgentClient
- 复用现有 HTTP 调用能力
- 新增 Arthas 相关调用方法（或在 ArthasAgentProxy 中直接用 RestTemplate）

### 13.2 SecurityContext
- 复用 `hasProjectPermission(projectId)` 做权限校验
- 复用 `getCurrentTenantId()` 做租户隔离

### 13.3 TenantResourceAccessService
- 复用 `requireNode(nodeId)` 做节点权限校验

### 13.4 DataCleanupScheduler
- 复用清理任务模式，新增 Arthas 清理调度
- 或独立 `ArthasCleanupScheduler`

### 13.5 GlobalExceptionHandler
- 复用统一异常处理
- 新增 Arthas 相关异常类型（可选）

### 13.6 operation_log
- 诊断操作记录审计日志
- start/stop/exec/profiler/heapdump 均记录

---

## 14. 配置项

```yaml
ops:
  arthas:
    enabled: true
    record-retention-days: 90
    file-retention-days: 30
    cleanup-cron: "0 0 3 * * ?"
    command-timeout-ms: 30000
    attach-timeout-ms: 15000
    max-result-json-kb: 100       # 超过此大小存文件
    profiler:
      default-duration: 30          # 默认采样时长（秒）
      max-duration: 300             # 最大采样时长（秒）
    heapdump:
      max-size-mb: 2048             # 最大文件大小
```
