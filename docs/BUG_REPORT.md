# 代码质量审计报告 - 潜在 Bug 分析

> **审计日期**: 2026-06-21
> **审计范围**: 全量 87 个 Java 源文件（3 个项目、61 个主要源文件 + 26 个测试文件）
> **审计人**: AI 代码审计系统

---

## 一、严重问题（P0 - 需立即修复）

### BUG-001: JWT 密钥硬编码在源码中
**文件**: `backend/common/src/main/java/com/ops/common/constant/SystemConstant.java:14`
**等级**: P0
**风险**: 高

**问题描述**:
```java
public static final String JWT_SECRET = "OpsPlatformSecretKey2024VeryLongSecretForJWT";
```
JWT 密钥以明文硬编码方式写入源码仓库。任何人拿到代码即可伪造任意用户 Token。

**影响范围**: 所有认证/鉴权功能
**根因**: 密钥应通过环境变量或配置中心注入，而非硬编码。
**修复建议**: 从环境变量 `JWT_SECRET_KEY` 读取，部署时使用 K8s Secrets 或 Vault。

---

### BUG-002: Agent 默认 Token 为固定值，无安全性
**文件**: `backend/agent/src/main/resources/application.yml:10`
**等级**: P0
**风险**: 高

**问题描述**:
```yaml
token: ${AGENT_TOKEN:default-agent-token-2024}
```
默认 Token 为 `default-agent-token-2024`。如果环境变量未设置，任何攻击者都可以伪装为合法 Agent 连接 Server。

**影响范围**: 所有 Agent 节点（AgentProxy、Shell 命令执行、文件传输）
**根因**: 缺省值应该拒绝而非使用弱默认值。

---

### BUG-003: Server 侧可任意执行 Agent Shell 命令（远程命令执行漏洞）
**文件**: `backend/agent/src/main/java/com/ops/agent/controller/ShellController.java`
**等级**: P0
**风险: 极高（完整服务器控制权）

**问题描述**:
Agent 的 `/shell/exec` 接口直接接收任意命令字符串，通过 `/bin/sh -c` 执行。
- 当前已绕过 Server 层的认证（通过 Agent Token 直接到达 Agent 端口 2123）
- 攻击者获取任意一个 Agent Token 即可在目标服务器上执行任意命令

```java
ProcessBuilder pb = new ProcessBuilder(cmdArray); // cmdArray 包含用户提供的 command
```

**影响范围**: 所有部署 Agent 节点
**根因**: ShellController 未做任何命令白名单校验。
**修复建议**: 命令白名单机制，禁止 `rm`, `chmod`, `su`, `curl` 等高危命令；或引入命令沙箱。

---

### BUG-004: 停止进程使用 Process.hashCode() 作为 PID，进程重启后会复用 PID
**文件**: `backend/agent/src/main/java/com/ops/agent/handler/StartCommander.java:66`
**等级**: P0
**风险: 高

**问题描述**:
```java
// Java 8 compatible - use hashCode as pseudo-PID
long pid = process.hashCode();
```
`Process.hashCode()` 不是真实 PID，可能重复。这意味着：
1. `AutoRestartDaemon` 检查的 "PID" 实际是随机哈希值
2. `StopCommander` 试图 `kill -9 <hash>` 会 kill 掉完全无关的进程
3. 进程重启后可能复用相同的 hashCode，导致错杀

**影响范围**: 进程管理所有操作
**修复建议**: 改用 `ProcessHandle.pid()` (Java 9+) 或使用 `jps` 命令。

---

### BUG-005: 数据库查询存在 SQL 注入风险（nodeIds LIKE 模糊匹配）
**文件**: `backend/server/src/main/resources/mapper/ProjectMapper.xml:18`, `NodeMapper.xml:44,62`
**等级**: P0
**风险**: 高

**问题描述**:
MyBatis XML 中使用 `LIKE '%' || #{nodeId} || '%'` 虽然使用参数化，但 `node_ids` 字段的存储格式为 `"1,2,3"` 逗号分隔字符串。

**关键问题**: 搜索 `nodeId=1` 会匹配 `node_ids='1,10,11,2'`（前缀匹配问题），导致**数据越权查询**。

**影响范围**: 所有基于 nodeId 筛选的项目/节点接口
**根因**: 逗号分隔存储 + LIKE 模糊匹配 = 错误匹配
**修复建议**: 使用 `FIND_IN_SET(#{nodeId}, node_ids)` 或改为关联表存储。

---

### BUG-006: RestTemplate 实例在循环中重复创建（内存泄漏风险）
**文件**: `backend/server/src/main/java/com/ops/server/controller/DeployController.java:42`
**等级**: P1
**风险**: 中

**问题描述**:
```java
private final RestTemplate restTemplate = new RestTemplate();
```
每个请求都使用同一个全局 `RestTemplate` 实例，但没有配置连接池。默认使用 JDK HTTP 连接，不复用连接。

**影响范围**: 高并发部署场景
**根因**: 应使用 `OkHttp3RestTemplateBuilder` 或配置 `PoolingHttpClientConnectionManager`。

---

### BUG-007: WebSocket 连接无会话超时清理机制
**文件**: `backend/server/src/main/java/com/ops/server/websocket/ConsoleHandler.java`, `DeployHandler.java`, `MonitorHandler.java`
**等级**: P1
**风险**: 中

**问题描述**:
三个 WebSocket Handler 使用 `ConcurrentHashMap` 存储会话，但**没有空闲超时清理**。断开的连接不会从 Map 中移除（仅 `afterConnectionClosed` 时移除），导致：
1. 内存泄漏（僵尸会话）
2. `push()` 方法向已断开会话发送消息时可能 NPE

**影响范围**: 所有 WebSocket 功能

---

## 二、高风险问题（P1）

### BUG-008: AuthInterceptor 存在权限绕过风险
**文件**: `backend/server/src/main/java/com/ops/server/interceptor/AuthInterceptor.java:62-82`
**等级**: P1
**风险**: 高

**问题描述**:
```java
if (uri.contains("/heartbeat") || uri.contains("/auth/login")) {
    return true;  // 直接放行，无认证
}
```
1. 使用 `contains()` 而非精确匹配，`/heartbeat/dangerous` 也会被放行
2. Agent Token 直接拿来用，**无角色/权限概念**——任何持有 Token 的 Agent 可调用所有接口
3. 没有 Token 过期机制（缓存只在 Map 中，无 TTL 清理）

**影响范围**: 所有需要认证的接口

---

### BUG-009: CORS 允许所有来源
**文件**: `backend/server/src/main/java/com/ops/server/config/WebConfig.java:34`
**等级**: P1
**风险**: 中

**问题描述**:
```java
.allowedOriginPatterns("*")
```
CORS 允许任何域名访问，结合 WebSocket 的 `setAllowedOrigins("*")`，任何网站可发起跨站请求。

---

### BUG-010: 密码明文存储
**文件**: `backend/common/src/main/java/com/ops/common/model/UserModel.java:19`
**等级**: P1
**风险**: 高

**问题描述**: `UserModel` 包含 `password` 字段，查看 UserMapper.xml，INSERT/UPDATE 直接写入明文。

**影响范围**: 用户认证系统

---

### BUG-011: FileController 配置保存路径无校验（任意文件写入）
**文件**: `backend/server/src/main/java/com/ops/server/controller/FileController.java:86`
**等级**: P1
**风险**: 高

**问题描述**:
```java
Files.write(Paths.get(configPath), content.getBytes("UTF-8"));
```
`configPath` 来自用户输入，没有路径遍历检查（如 `../../../etc/crontab`）。虽然检查了 `.yml` 后缀，但不阻止 `../../etc/passwd.yml` 等攻击。

**影响范围**: 配置文件编辑功能

---

### BUG-012: 项目删除无级联检查，可能造成数据不一致
**文件**: `backend/server/src/main/java/com/ops/server/controller/ProjectController.java:56`
**等级**: P1
**风险**: 高

**问题描述**:
```java
public Result<?> deleteProject(@PathVariable Long id) {
    projectService.deleteById(id);  // 直接删除，未检查关联的部署记录和节点绑定
    return Result.success();
}
```

**影响范围**: 项目管理

---

### BUG-013: 文件读取无大小限制，可能 OOM
**文件**: `backend/agent/src/main/java/com/ops/agent/handler/FileCommander.java:29`
**等级**: P1
**风险**: 中

**问题描述**:
```java
byte[] content = Files.readAllBytes(file.toPath());
```
大文件（如几个 GB 的日志）会直接读入内存导致 OOM。

---

### BUG-014: AI 分析接口支持外部 API Key 提交，可能导致密钥泄露/滥用
**文件**: `backend/server/src/main/java/com/ops/server/controller/AIAnalyzeController.java`
**等级**: P1
**风险**: 中

**问题描述**: 用户可提交自己的 `apiKey` 和 `endpoint` 调用 AI 接口，存在：
1. 密钥绕过审计
2. 任意外部 API 调用
3. 响应未经长度限制

---

### BUG-015: 定时任务无分布式锁，多实例启动会导致重复执行
**文件**: `backend/server/src/main/java/com/ops/server/scheduler/HeartbeatChecker.java:35`, `DeployScheduler.java:46`
**等级**: P1
**风险**: 高

**问题描述**: 使用 `@Scheduled(fixedRate = ...)` 但没有分布式锁。如果部署多个 Server 实例：
- HeartbeatChecker 会为同一离线节点重复插入告警记录
- DeployScheduler 会对同一待部署记录重复执行

---

### BUG-016: WebSocket Auth 仅检查协议头，极易绕过
**文件**: `backend/server/src/main/java/com/ops/server/interceptor/WebSocketAuthInterceptor.java:33`
**等级**: P1
**风险**: 高

**问题描述**:
```java
String token = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
```
从 `Sec-WebSocket-Protocol` 头部取 Token，但浏览器可以伪造这个头。此外即使 Token 正确也**无权限校验**。

---

## 三、中风险问题（P2）

| 编号 | 问题 | 文件 | 说明 |
|------|------|------|------|
| BUG-017 | `ConcurrentModificationException` 风险 | `ConsoleHandler.java:98-103` | 遍历时 `nodeMap.values().removeIf()` 但 `sessionGroups` 本身在并发读写 |
| BUG-018 | `Thread.sleep(3000)` × 5 = 15 秒健康检查阻塞 | `DeployController.java:184` | 同步阻塞线程，高并发时耗尽线程池 |
| BUG-019 | 事务注解在同一个类内部调用不生效 | `DeployController.java:48` | `publish()` 直接调用 Mapper，事务由 `DeployService` 的 `@Transactional` 控制，但 Controller 没经过 Service |
| BUG-020 | `nodeId` 类型转换缺少 NumberFormatException 处理 | `DeployController.java:53-56` | `Long.valueOf(request.get("nodeId").toString())` 对非数字值会抛异常 |
| BUG-021 | Server 部署路径硬编码为 Agent 路径 | `DeployController.java:109` | `agentFileDir = "/app/data/versions/..."` 只在 Agent 端有效，Server 端路径不一致 |
| BUG-022 | 日志文件无截断，LogCommander 全量扫描 | `backend/agent/src/main/java/com/ops/agent/handler/LogCommander.java:45` | `countLines()` 逐行扫描大日志文件，效率极低 |
| BUG-023 | 大文件上传配置 500MB 但无分片/断点续传 | `application.yml:17` | 网络中断后需从头重传 |
| BUG-024 | Process 未 wait/destroy，进程僵尸化 | `StartCommander.java:63` | `pb.start()` 后的 `Process` 对象没有异步读取 stdout/stderr，进程会缓冲满后挂起 |
| BUG-025 | H2 数据库 console 在生产环境可开启 | `application.yml:23` | `h2.console.enabled: true` 暴露数据库管理界面 |
| BUG-026 | SysConfigMapper 使用 MERGE 语法但 H2 不支持 | `SysConfigMapper.java:18` | `MERGE INTO` 是 Oracle 语法，H2 的 MySQL 模式下用 `MERGE` 会报错 |
| BUG-027 | 部署回滚无并发控制，多次回滚可能导致状态混乱 | `DeployController.java:266-450` | 回滚时未检查当前进程状态，可能回滚到其他部署的进程 |
| BUG-028 | `totalMemoryMb` 计算方式不准确 | `HeartbeatDaemon.java:78` | 使用 `Runtime.getRuntime().maxMemory()` 而非系统总内存，容器环境下不准确 |

---

## 四、低风险问题（P3）

| 编号 | 问题 | 文件 | 说明 |
|------|------|------|------|
| BUG-029 | `ServerApplication` 无 `@EnableScheduling` | `ServerApplication.java` | `HeartbeatChecker` 和 `DeployScheduler` 使用 `@Scheduled` 但启动类未启用 |
| BUG-030 | `AuthInterceptor` 缓存无清理策略 | `AuthInterceptor.java:26` | `userTokenCache` 只增不减，内存泄漏 |
| BUG-031 | WebSocket 连接无最大连接数限制 | 所有 Handler | 无上限连接可导致 DoS |
| BUG-032 | `deployRecordMapper.findScheduledReady()` 只取 20 条 | `DeployRecordMapper.xml:31` | 大量定时任务积压时部分永远不会执行 |
| BUG-033 | 没有 `@RequestBody` 校验注解，全用 `Map<String, Object>` | 所有 Controller | 类型安全为零，IDE 无提示 |
| BUG-034 | `getAllByToken` 方法名与实际功能不符 | `NodeMapper.java:14` | `getTokenByToken` 实际返回 token 自己，无意义 |
| BUG-035 | `Result` 类不可变字段用 `@Data` 生成 setter | `common/response/Result.java:13` | `@Data` 生成 setter 可被恶意修改响应内容 |
| BUG-036 | 全局异常处理中所有异常返回 "系统内部错误" | `GlobalExceptionHandler.java:53` | 运维无法从错误信息定位问题 |

---

## 五、统计汇总

| 等级 | 数量 | 类别 |
|------|------|------|
| **P0** | 6 | JWT 密钥硬编码、默认 Token、远程命令执行、错误 PID、SQL 注入、内存泄漏 |
| **P1** | 9 | 权限绕过、CORS 全开、密码明文、任意文件写入、级联删除、OOM、AI 密钥、分布式锁、WebSocket 弱认证 |
| **P2** | 12 | 并发问题、事务失效、类型转换、路径硬编码、效率问题、配置问题 |
| **P3** | 8 | 配置缺失、缓存泄漏、限制不足、编码风格 |
| **合计** | **35** | |
