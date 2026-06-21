# 测试方案

> **审计日期**: 2026-06-21
> **目标**: 每个模块覆盖率 >= 80%，关键业务模块 >= 90%

---

## 一、测试架构

### 1.1 技术栈

| 工具 | 版本 | 用途 |
|------|------|------|
| JUnit 5 (5.10+) | latest | 测试框架 |
| Mockito 5.x | latest | Mock 对象 |
| Spring Boot Test | matching | 集成测试 |
| MockMvc | Spring | HTTP 接口测试 |
| H2 (内存数据库) | latest | 数据库测试 |
| wiremock | latest | HTTP 客户端 Mock |

### 1.2 测试目录结构

```
backend/
├── server/src/test/java/com/ops/server/
│   ├── controller/        (已有)
│   │   ├── BaseControllerTest.java     (已有，基类)
│   │   ├── DeployControllerTest.java   (已有)
│   │   └── ... (其他 controller 测试已有)
│   ├── service/           (新建)
│   │   ├── DeployServiceTest.java
│   │   ├── ProjectServiceTest.java
│   │   ├── NodeServiceTest.java
│   │   ├── AlarmServiceTest.java
│   │   └── VersionServiceTest.java
│   ├── mapper/            (新建)
│   │   ├── DeployRecordMapperTest.java
│   │   ├── ProjectMapperTest.java
│   │   └── ... (其他 mapper 测试)
│   ├── scheduler/         (新建)
│   │   ├── HeartbeatCheckerTest.java
│   │   └── DeploySchedulerTest.java
│   ├── websocket/         (新建)
│   │   ├── ConsoleHandlerTest.java
│   │   ├── DeployHandlerTest.java
│   │   └── MonitorHandlerTest.java
│   ├── interceptor/       (新建)
│   │   ├── AuthInterceptorTest.java
│   │   └── WebSocketAuthInterceptorTest.java
│   └── integration/       (新建)
│       └── DeployFlowIntegrationTest.java
├── agent/src/test/java/com/ops/agent/
│   ├── handler/           (新建)
│   │   ├── FileCommanderTest.java
│   │   ├── StartCommanderTest.java
│   │   ├── StopCommanderTest.java
│   │   └── LogCommanderTest.java
│   ├── controller/        (新建)
│   │   ├── ShellControllerTest.java
│   │   ├── AgentFileControllerTest.java
│   │   └── ...
│   ├── daemon/            (新建)
│   │   ├── HeartbeatDaemonTest.java
│   │   └── AutoRestartDaemonTest.java
│   └── client/            (新建)
│       └── WebSocketClientTest.java
```

---

## 二、模块详细测试方案

### 2.1 Common 模块 (当前 31% → 目标 85%)

#### 2.1.1 Model 测试 (7个 Model，当前 0%)

每个 Model 需要：
- **单元测试**: Lombok getter/setter 验证
- **边界测试**: null 值处理
- **序列化测试**: Serializable 验证

```java
// 示例: ProjectModelTest.java
@Test
void testAllFields() {
    ProjectModel p = new ProjectModel();
    p.setId(1L);
    p.setName("test");
    p.setNodeIds("1,2");
    p.setStartScript("java -jar app.jar");
    // ... 所有字段
    assertEquals(1L, p.getId());
    assertEquals("test", p.getName());
}

@Test
void testNullFields() {
    ProjectModel p = new ProjectModel();
    assertNull(p.getNodeIds());
    assertNull(p.getJvmOpts());
}
```

#### 2.1.2 Result 测试补充 (补充当前测试)

```java
@Test
void error_serverError() {
    Result<Object> r = Result.serverError();
    assertEquals(500, r.getCode());
    assertEquals("Internal server error", r.getMessage());
}

@Test
void authError() {
    Result<Object> r = Result.authError();
    assertEquals(401, r.getCode());
}
```

---

### 2.2 Server 模块 (当前 ~32% → 目标 85%)

#### 2.2.1 Service 层测试 (5个 Service，当前 0%)

```java
@SpringBootTest
@AutoConfigureMockMvc
class DeployServiceTest {

    @MockBean private DeployRecordMapper mapper;
    @Autowired private DeployService service;

    @Test
    void findById() {
        when(mapper.findById(1L)).thenReturn(new DeployModel());
        assertNotNull(service.findById(1L));
    }

    @Test
    void findByProjectId_pagination() {
        when(mapper.findByProjectId(1L, 1, 20)).thenReturn(List.of());
        assertTrue(service.findByProjectId(1L, 1, 20).isEmpty());
        assertEquals(0L, service.countByProjectId(1L));
    }
}
```

#### 2.2.2 Scheduler 测试 (2个 Scheduler，当前 0%)

```java
@SpringBootTest
class HeartbeatCheckerTest {

    @MockBean private NodeMapper nodeMapper;
    @MockBean private AlarmRecordMapper alarmRecordMapper;
    @Autowired private HeartbeatChecker checker;

    @Test
    void checkOffline_noCandidates() {
        when(nodeMapper.getOfflineCandidates(anyLong())).thenReturn(List.of());
        checker.checkOffline(); // 无异常
    }

    @Test
    void checkOffline_markOffline() {
        NodeModel node = new NodeModel();
        node.setId(1L);
        node.setName("test-node");
        node.setIp("10.0.0.1");
        when(nodeMapper.getOfflineCandidates(anyLong())).thenReturn(List.of(node));

        checker.checkOffline();

        verify(nodeMapper).updateStatusOffline(1L);
        verify(alarmRecordMapper).insert(any(AlarmModel.class));
    }
}
```

#### 2.2.3 WebSocket 层测试 (3个 Handler，当前 0%)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsoleHandlerTest {

    @Autowired private ConsoleHandler handler;
    @MockBean private NodeMapper nodeMapper;

    @Test
    void subscribeAndPush() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("test-session");

        handler.subscribe("proj1", "node1", session);
        handler.push("proj1", "node1", "message");

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void push_toClosedSession() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);

        handler.subscribe("proj1", "node1", session);
        handler.push("proj1", "node1", "message"); // 不抛异常
    }
}
```

#### 2.2.4 Interceptor 层测试 (2个 Interceptor，当前 0%)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthInterceptorTest {

    @Autowired private AuthInterceptor interceptor;
    @MockBean private NodeMapper nodeMapper;
    @MockBean private UserMapper userMapper;

    @Test
    void preHandle_noToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    @Test
    void preHandle_userToken_valid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        request.addHeader("Authorization", "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(userMapper.findByUsername("test-user")).thenReturn(new UserModel());

        assertTrue(interceptor.preHandle(request, response, null));
    }
}
```

---

### 2.3 Agent 模块 (当前 0% → 目标 80%)

#### 2.3.1 Handler 测试

```java
class FileCommanderTest {

    @TempDir Path tempDir;
    private FileCommander commander;

    @BeforeEach
    void setup() {
        commander = new FileCommander(tempDir.toString());
    }

    @Test
    void readFile_existing() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content");

        Map<String, Object> result = commander.readFile("test.txt");
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("content", result.get("content"));
    }

    @Test
    void readFile_notExist() {
        Map<String, Object> result = commander.readFile("nonexistent.txt");
        assertEquals("FAILED", result.get("status"));
    }

    @Test
    void verifyFile_validSha256() throws IOException {
        // 文件包含 "test content"
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test content");

        String sha256 = "9 fc97f2..."; // 计算好的 SHA-256
        Map<String, Object> result = commander.verifyFile("test.txt", sha256);
        assertTrue((Boolean) result.get("valid"));
    }

    @Test
    void verifyFile_invalidSha256() {
        Map<String, Object> result = commander.verifyFile("test.txt", "invalid_hash");
        assertFalse((Boolean) result.get("valid"));
    }
}
```

#### 2.3.2 Controller 测试 (MockMvc)

```java
@SpringBootTest
@AutoConfigureMockMvc
class ShellControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void exec_emptyCommand() throws Exception {
        mockMvc.perform(post("/shell/exec")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"command\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void exec_nullCommand() throws Exception {
        mockMvc.perform(post("/shell/exec")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void exec_validCommand() throws Exception {
        mockMvc.perform(post("/shell/exec")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"command\":\"echo hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
```

---

## 三、各类测试设计

### 3.1 单元测试 (单元测试)
- 每个 Service 方法
- 每个 Handler 业务逻辑
- 工具方法（ErrorCode, Result, 工具类）

### 3.2 Mock 测试
- 数据库操作 → Mock MyBatis Mapper
- HTTP 调用 → Mock RestTemplate / RestTemplate.exchange
- 外部服务 → WireMock

### 3.3 边界测试
- 空列表/空字符串
- 超大文件 (> 500MB)
- 超长 SQL (nodeIds LIKE 'xxx')
- 并发部署 (同一项目多个节点)
- 负数/0 的 ID
- 特殊字符的文件名/路径

### 3.4 异常测试
- Mapper 抛异常 → 验证异常处理
- Agent 不可达 → 验证降级处理
- 参数缺失 → 验证错误码
- 事务回滚 → 验证数据一致性

### 3.5 并发测试
- 同时部署同一项目的多个节点
- 同一 Agent 多连接
- 心跳超时并发处理

### 3.6 参数校验测试
- 缺少必填参数
- 类型不匹配
- 超出范围的值

### 3.7 数据库 Mock 测试
- 使用 H2 内存数据库
- MyBatis XML 正确映射
- 分页边界
- LIKE 模糊匹配语义

### 3.8 Redis Mock 测试
- **当前项目无 Redis** — 暂不需要
- 后续如有缓存需求，使用 embedded-redis

### 3.9 MQ Mock 测试
- **当前项目无 MQ** — 暂不需要
- 后续如有消息队列，使用 embedded-rabbitmq/kafka

---

## 四、测试覆盖率目标

| 模块 | 当前覆盖率 | 目标覆盖率 | 优先级 |
|------|-----------|-----------|--------|
| common/model | ~0% | 85% | P1 |
| common/constant | ~80% | 95% | P2 |
| common/enums | ~50% | 90% | P2 |
| common/exception | ~30% | 85% | P2 |
| common/response | ~70% | 90% | P2 |
| server/controller | ~35% | 85% | P0 |
| server/service | ~0% | 90% | P0 |
| server/mapper | ~0% | 80% | P1 |
| server/scheduler | ~0% | 85% | P1 |
| server/websocket | ~0% | 80% | P1 |
| server/interceptor | ~0% | 85% | P0 |
| server/exception | ~30% | 90% | P1 |
| agent/handler | ~0% | 85% | P0 |
| agent/controller | ~0% | 80% | P0 |
| agent/daemon | ~0% | 85% | P0 |
| agent/client | ~0% | 80% | P1 |

---

## 五、执行计划

### Phase 1: 紧急（本周）
- [ ] 所有 Service 层测试
- [ ] 安全相关 Interceptor 测试
- [ ] Agent Handler 单元测试

### Phase 2: 重要（两周内）
- [ ] Scheduler 测试
- [ ] WebSocket Handler 测试
- [ ] Agent Controller 测试

### Phase 3: 一般（一月内）
- [ ] Mapper 集成测试
- [ ] 边界/异常测试补充
- [ ] 并发测试

---

## 六、统计汇总

| 测试类型 | 已覆盖 | 待覆盖 | 测试数 |
|----------|--------|--------|--------|
| Controller 层 | 12/12 个 | 0 | ~100 测试用例 |
| Service 层 | 0/5 个 | 5 | ~30 测试用例 |
| Mapper 层 | 0/9 个 | 9 | ~50 测试用例 |
| Scheduler | 0/2 个 | 2 | ~15 测试用例 |
| WebSocket | 0/3 个 | 3 | ~20 测试用例 |
| Interceptor | 0/2 个 | 2 | ~15 测试用例 |
| Agent Handler | 0/4 个 | 4 | ~25 测试用例 |
| Agent Controller | 0/5 个 | 5 | ~20 测试用例 |
| Agent Daemon | 0/2 个 | 2 | ~15 测试用例 |
| Agent Client | 0/1 个 | 1 | ~10 测试用例 |
| **合计** | **17** | **52** | **~315 测试用例** |
