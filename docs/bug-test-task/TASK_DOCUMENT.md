# Easy-Ops 安全修复任务文档

> **创建日期**: 2026-06-21

---

## 任务列表

| # | 任务 | 优先级 | 状态 | 涉及文件 |
|---|------|--------|------|---------|
| 1 | ✅ JWT 密钥外部化 | P0 | ✅ 已完成 | SecurityConfig.java, SystemConstant.java, application.yml |
| 2 | ✅ Agent 默认 Token 强制化 | P0 | ✅ 已完成 | Agent application.yml, HeartbeatDaemon.java, AutoRestartDaemon.java |
| 3 | ✅ Shell 命令白名单 | P0 | ✅ 已完成 | ShellController.java |
| 4 | ✅ 错误 PID 修复 | P0 | ✅ 已完成 | StartCommander.java, AutoRestartDaemon.java |
| 5 | ✅ nodeIds 模糊匹配修复 | P0 | ✅ 已完成 | ProjectMapper.xml, NodeMapper.xml |
| 6 | ✅ 密码哈希存储 | P0 | ✅ 已完成 | UserMapper.java, UserMapper.xml, SystemController.java |
| 7 | ✅ RestTemplate 连接池 | P1 | ✅ 已完成 | RestTemplateConfig.java |
| 8 | ✅ WebSocket 会话清理 | P1 | ✅ 已完成 | ConsoleHandler.java, DeployHandler.java, MonitorHandler.java |
| 9 | ✅ CORS 配置收紧 | P1 | ✅ 已完成 | WebConfig.java, application.yml |
| 10 | ✅ AI API Key 内化 | P1 | ✅ 已完成 | AIAnalyzeController.java, application.yml |

---

## 已完成任务详情

### Task 5: nodeIds 模糊匹配修复 ✅
- `ProjectMapper.xml`: `find` 中 `LIKE '%' || #{nodeId} || '%'` 改为 `FIND_IN_SET(#{nodeId}, node_ids) > 0 OR node_ids = #{nodeId}`
- `NodeMapper.xml`: `countByNodeId` 和 `getProjectNamesByNodeId` 同样修复
- 修复后 `nodeId=1` 不再错误匹配 `node_ids='1,10,11'`

### Task 6: 密码哈希存储 ✅
- `SystemController.java` 已使用 `BCrypt.hashpw()` 存储密码、`BCrypt.checkpw()` 验证登录
- `pom.xml` 已引入 `org.mindrot:jbcrypt` 依赖
- 新增用户时密码自动 BCrypt 哈希（salt rounds=10），修改密码时重新哈希
- 登录接口 `POST /api/auth/login` 通过 `BCrypt.checkpw()` 对比存储的哈希值

### Task 7: RestTemplate 连接池 ✅
- 新建 `RestTemplateConfig.java`：使用 `PoolingHttpClientConnectionManager` 管理连接池
  - 最大连接数: 200
  - 每路由最大连接: 50
  - 连接超时: 10s，读取超时: 30s
- `ConsoleHandler.java`: `new RestTemplate()` 改为 `@Autowired RestTemplate` 注入
- `AIAnalyzeController.java`: `new RestTemplate()` 改为 `@Autowired RestTemplate` 注入

### Task 8: WebSocket 会话清理 ✅
- `ConsoleHandler.java`: `handleTransportError` 增加异常后会话清理
- `DeployHandler.java`:
  - `afterConnectionClosed` 中遍历 `deploySessions` 按 session 查找并移除
  - `handleTransportError` 增加异常后清理
  - `push()` 发送失败时移除失效会话
- `MonitorHandler.java`:
  - `afterConnectionClosed` 中遍历 `monitorSessions` 按 session 查找并移除
  - `handleTransportError` 增加异常后清理
  - `push()` 发送失败时移除失效会话

### Task 9: CORS 配置收紧 ✅
- `WebConfig.java`: `allowedOriginPatterns("*")` 改为从 `cors.allowed-origins` 读取
  - 默认值: `http://localhost:3000,http://localhost:5173`
  - `/api/**` 路径: 仅允许配置的域名，允许 credentials
  - `/**` 静态资源: 仅允许 GET 方法
  - 暴露 `Authorization` 响应头
- `application.yml`: 新增 `cors.allowed-origins` 配置项

### Task 10: AI API Key 内化 ✅
- `AIAnalyzeController.java`:
  - 使用 `@Value("${ai.apiKey:#{null}}")` 优先从环境变量 `AI_API_KEY` 读取
  - 未配置时回退到 `sys_config` 表（兼容旧数据）
  - `saveConfig` 接口阻止通过页面提交 API Key
  - `getConfig` 接口返回 `"(已配置)"` 而非真实 Key
  - `RestTemplate` 改为 Spring 注入，消除 `new ObjectMapper()` 每请求创建
- `application.yml`: 新增 `ai.apiKey: ${AI_API_KEY:}` 配置项
