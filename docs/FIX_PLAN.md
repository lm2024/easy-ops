# 修复方案

> **审计日期**: 2026-06-21

---

## 修复方案列表

### FIX-001: JWT 密钥硬编码
**等级**: P0
**问题描述**: JWT 密钥明文存储在 `SystemConstant.java`

**影响范围**: 所有认证/鉴权功能

**根因分析**: 密钥在编译期硬编码，版本控制历史中可追溯

**修复方案**:
```java
// SystemConstant.java 修改为:
@Value("${jwt.secret:#{null}}")
private String jwtSecret;

// 如果为空则拒绝启动:
@PostConstruct
public void validate() {
    if (jwtSecret == null || jwtSecret.isEmpty()) {
        throw new IllegalStateException("JWT_SECRET_KEY environment variable is required");
    }
}
```

**风险评估**: 低 — 需确保所有部署环境有正确环境变量

**回归测试建议**:
1. 无环境变量时应用应拒绝启动
2. 有正确环境变量时 Token 生成/验证正常

---

### FIX-002: Agent 默认 Token
**等级**: P0
**问题描述**: Agent 默认 Token 为 `default-agent-token-2024`

**影响范围**: 所有 Agent 节点

**根因分析**: YAML 配置中 `${AGENT_TOKEN:default-agent-token-2024}` 的默认值

**修复方案**:
```yaml
# application.yml 修改为:
agent:
  token: ${AGENT_TOKEN:}  # 去掉默认值
```

**Java 代码增加校验**:
```java
@Value("${agent.token}")
private String agentToken;

@PostConstruct
public void validateToken() {
    if (agentToken == null || agentToken.isEmpty()) {
        throw new IllegalStateException("AGENT_TOKEN environment variable is required");
    }
}
```

**风险评估**: 低 — 需确保所有 Agent 配置正确的 AGENT_TOKEN

**回归测试建议**: 部署前验证所有 Agent 的 token 配置

---

### FIX-003: Shell 命令白名单
**等级**: P0
**问题描述**: ShellController 允许执行任意命令

**影响范围**: 所有部署 Agent 节点

**根因分析**: 用户输入直接传入 `/bin/sh -c`

**修复方案**:
```java
// 命令白名单机制
private static final Set<String> ALLOWED_COMMANDS = Set.of(
    "ps", "top", "df", "free", "uname", "cat", "tail",
    "head", "ls", "pwd", "date", "uptime", "whoami"
);

private static final Set<String> BLOCKED_COMMANDS = Set.of(
    "rm", "rmdir", "chmod", "chown", "sudo", "su",
    "passwd", "kill", "curl", "wget", "nc", "bash", "sh"
);

private boolean isCommandAllowed(String command) {
    String firstWord = command.trim().split("\\s+")[0].toLowerCase();
    return ALLOWED_COMMANDS.contains(firstWord)
        && !BLOCKED_COMMANDS.stream().anyMatch(c -> command.toLowerCase().contains(" " + c + " ") || command.toLowerCase().startsWith(c + " "));
}
```

**风险评估**: 中 — 部分合法命令（如 `kill -0` 检查进程）不在白名单内，需酌情添加

**回归测试建议**: 白名单内命令正常执行，非白名单命令被拒绝

---

### FIX-004: 错误 PID 问题
**等级**: P0
**问题描述**: `Process.hashCode()` 不是真实 PID

**影响范围**: 进程管理所有操作

**根因分析**: 代码注释说明"Java 8 compatible"，使用 hashCode 作为伪 PID

**修复方案**:
```java
// 方案1: 升级到 Java 9+ 使用 ProcessHandle
long pid = process.pid();  // Java 9+

// 方案2: 如果是 Java 8，使用进程名匹配
String pidStr = readLine("pgrep -f " + jarName);  // Linux
// Windows: tasklist /FI "IMAGENAME eq " + jarName
```

**风险评估**: 高 — 需要评估 Agent 运行环境的 Java 版本

**回归测试建议**: 验证停止进程时操作的是正确的进程

---

### FIX-005: nodeIds LIKE 模糊匹配
**等级**: P0
**问题描述**: `LIKE '%' || #{nodeId} || '%'` 导致错误匹配

**影响范围**: 所有基于 nodeId 筛选的接口

**根因方案**:
```xml
<!-- 方案1: 使用 FIND_IN_SET (MySQL 模式) -->
AND FIND_IN_SET(#{nodeId}, node_ids) > 0

<!-- 方案2: 改为关联表存储 -->
<!-- 新建 project_node 表: project_id, node_id -->

<!-- 方案3: 使用精确匹配 + 分隔符 -->
AND (',' || node_ids || ',' LIKE '%' || #{nodeId} || '%')
```

**风险评估**: 低 — `FIND_IN_SET` 仅改变查询语义，不影响数据结构

**回归测试建议**: 验证 nodeId=1 不再匹配 node_ids='1,10,11'

---

### FIX-006: RestTemplate 连接池
**等级**: P1
**问题描述**: RestTemplate 默认无连接池

**影响范围**: 高并发部署场景

**修复方案**:
```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        HttpClient httpClient = HttpClients.custom()
            .setMaxConnTotal(200)
            .setMaxConnPerRoute(20)
            .build();
        CloseableHttpClient apacheClient = HttpClients.custom()
            .setConnectionManager(newPoolingConnectionManager(httpClient))
            .build();
        return new RestTemplateBuilder()
            .rootUri(serverUrl)
            .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(apacheClient))
            .build();
    }
}
```

**风险评估**: 低

**回归测试建议**: 并发部署多个节点时连接不超时

---

### FIX-007: WebSocket 会话清理
**等级**: P1
**问题描述**: WebSocket 会话 Map 无超时清理

**影响范围**: 所有 WebSocket 功能

**修复方案**:
```java
// 增加定时清理
@Scheduled(fixedRate = 60000)
public void cleanupClosedSessions() {
    sessionGroups.values().forEach(nodeMap -> {
        nodeMap.entrySet().removeIf(entry ->
            !entry.getValue().isOpen()
        );
    });
}
```

**风险评估**: 低

**回归测试建议**: 断开的 WebSocket 连接在 1 分钟内从 Map 中移除

---

### FIX-008: 数据库密码明文存储
**等级**: P0
**问题描述**: UserModel.password 字段存明文密码

**影响范围**: 所有用户认证

**修复方案**:
```java
// 使用 BCrypt 哈希
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hashed = encoder.encode(plainPassword);
boolean matches = encoder.matches(plainPassword, hashed);
```

**风险评估**: 中 — 现有用户密码需迁移

**回归测试建议**: 注册/登录正常，旧密码迁移后正常

---

### FIX-009: CORS 配置
**等级**: P1
**问题描述**: `allowedOriginPatterns("*")` 允许所有来源

**影响范围**: 所有 HTTP 接口

**修复方案**:
```java
registry.addMapping("/**")
    .allowedOriginPatterns("https://*.example.com")  // 限制域名
    .allowCredentials(true);
```

**风险评估**: 低

**回归测试建议**: 非授权域名的请求被拒绝

---

### FIX-010: AI API Key 外部传入
**等级**: P1
**问题描述**: AIAnalyzeController 允许前端传入任意 API Key

**影响范围**: AI 分析功能

**根因分析**: `apiKey` 参数从前端传入，可能导致 Key 泄露到客户端

**修复方案**:
```java
// 移除前端传入 apiKey 的能力，从配置读取
@Value("${ai.api-key}")
private String apiKey;

// 移除 AIAnalyzeController 中 apiKey 参数
public Result<?> analyze(@RequestParam String question) {
    // 使用配置的 apiKey
}
```

**风险评估**: 低

**回归测试建议**: AI 分析功能正常，前端不再传 apiKey

---

## 二、修复优先级

| 优先级 | 修复项 | 影响 | 难度 | 建议顺序 |
|--------|--------|------|------|---------|
| **P0-1** | FIX-001 (JWT) | 高 | 低 | 第1 |
| **P0-2** | FIX-002 (Agent Token) | 高 | 低 | 第2 |
| **P0-3** | FIX-003 (Shell 白名单) | 极高 | 中 | 第3 |
| **P0-4** | FIX-004 (PID) | 高 | 中 | 第4 |
| **P0-5** | FIX-005 (nodeIds) | 高 | 低 | 第5 |
| **P0-8** | FIX-008 (密码哈希) | 高 | 中 | 第6 |
| **P1-6** | FIX-006 (连接池) | 中 | 中 | 第7 |
| **P1-7** | FIX-007 (Session清理) | 中 | 低 | 第8 |
| **P1-9** | FIX-009 (CORS) | 中 | 低 | 第9 |
| **P1-10** | FIX-010 (AI Key) | 中 | 低 | 第10 |
