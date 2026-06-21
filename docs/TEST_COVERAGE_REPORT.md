# 测试覆盖率审计报告

> **审计日期**: 2026-06-21
> **审计范围**: 全量测试文件与源代码对比分析

---

## 一、项目模块概览

| 模块 | 包名 | 源文件数 | 测试文件数 | 有测试的源文件 | 无测试的源文件 |
|------|------|----------|-----------|---------------|---------------|
| **common** | `com.ops.common` | 16 | 5 | 5 | 11 |
| **server** | `com.ops.server` | 38 | 12 | 12 | 26 |
| **agent** | `com.ops.agent` | 13 | 0 | 0 | 13 |
| **demo** | `com.ops.demo` | 2 | 0 | 0 | 2 |
| **合计** | — | **69** | **17** | **17** | **52** |

---

## 二、各模块详细覆盖率

### 2.1 Common 模块 (覆盖率: 31%)

| 源文件 | 测试文件 | 覆盖率 | 说明 |
|--------|---------|--------|------|
| constant/ErrorCode.java | ErrorCodeTest.java | ✅ 100% | 常量测试 |
| constant/SystemConstant.java | ConstantTest.java | ✅ 部分 | 仅部分常量 |
| enums/DeployStatus.java | EnumTest.java | ✅ 部分 | 仅代码-描述映射 |
| enums/FileAction.java | (EnumTest.java 覆盖) | ✅ 部分 | 复用 EnumTest |
| enums/FileType.java | (EnumTest.java 覆盖) | ✅ 部分 | 复用 EnumTest |
| enums/NodeStatus.java | (EnumTest.java 覆盖) | ✅ 部分 | 包含 fromCode() |
| enums/UserRole.java | (EnumTest.java 覆盖) | ✅ 部分 | 复用 EnumTest |
| exception/BusinessException.java | ExceptionTest.java | ✅ 部分 | 仅 message 测试 |
| exception/SystemException.java | (ExceptionTest.java 覆盖) | ✅ 部分 | 复用 |
| model/AlarmModel.java | **无测试** | ❌ 0% | |
| model/DeployModel.java | **无测试** | ❌ 0% | |
| model/FileAccessLogModel.java | **无测试** | ❌ 0% | |
| model/NodeModel.java | **无测试** | ❌ 0% | |
| model/OperationLogModel.java | **无测试** | ❌ 0% | |
| model/ProjectModel.java | **无测试** | ❌ 0% | |
| model/UserModel.java | **无测试** | ❌ 0% | |
| model/VersionModel.java | **无测试** | ❌ 0% | |
| response/Result.java | ResultTest.java | ✅ 部分 | success/error 静态方法 |

**common 问题**: 所有 Model 类无任何测试（Lombok @Data 生成的 getter/setter 无需测试，但业务逻辑缺失）

---

### 2.2 Server 模块 (覆盖率: 32%)

| 源文件 | 测试文件 | 覆盖率 | 说明 |
|--------|---------|--------|------|
| ServerApplication.java | **无测试** | ❌ 0% | 启动类无需测试 |
| config/WebConfig.java | **无测试** | ❌ 0% | 配置类 |
| config/WebSocketConfig.java | **无测试** | ❌ 0% | 配置类 |
| controller/DeployController.java | DeployControllerTest.java | ⚠️ 部分 | 仅 publish_success 路径 |
| controller/ProjectController.java | ProjectControllerTest.java | �️ 低 | listProjects 等基础场景 |
| controller/NodeController.java | NodeControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/VersionController.java | VersionControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/AlarmController.java | AlarmControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/MonitorController.java | MonitorControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/FileController.java | FileControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/LogController.java | LogControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/SystemController.java | SystemControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/ProcessController.java | ProcessControllerTest.java | ⚠️ 部分 | 基础 CRUD |
| controller/AIAnalyzeController.java | **无测试** | ❌ 0% | AI 调用逻辑 |
| controller/AgentProxyController.java | **无测试** | ❌ 0% | 代理逻辑 |
| service/* (5个) | **无测试** | ❌ 0% | Service 层完全缺失测试 |
| mapper/* (9个) | **无测试** | ❌ 0% | Mapper 层完全缺失测试 |
| scheduler/HeartbeatChecker.java | **无测试** | ❌ 0% | 定时任务 |
| scheduler/DeployScheduler.java | **无测试** | ❌ 0% | 定时部署 |
| websocket/* (3个) | **无测试** | ❌ 0% | WebSocket 处理器 |
| interceptor/* (2个) | **无测试** | ❌ 0% | 认证拦截器 |
| exception/GlobalExceptionHandler.java | GlobalExceptionHandlerTest.java | ✅ 部分 | 异常处理 |

---

### 2.3 Agent 模块 (覆盖率: 0%)

| 源文件 | 测试文件 | 覆盖率 |
|--------|---------|--------|
| AgentApplication.java | **无测试** | ❌ 0% |
| client/WebSocketClient.java | **无测试** | ❌ 0% |
| daemon/HeartbeatDaemon.java | **无测试** | ❌ 0% |
| daemon/AutoRestartDaemon.java | **无测试** | ❌ 0% |
| controller/* (5个) | **无测试** | ❌ 0% |
| handler/* (4个) | **无测试** | ❌ 0% |

**Agent 模块：0% 覆盖率，完全无测试。**

---

### 2.4 Demo 模块 (覆盖率: 0%)

| 源文件 | 测试文件 | 覆盖率 |
|--------|---------|--------|
| DemoApplication.java | **无测试** | ❌ 0% |
| controller/DemoController.java | **无测试** | ❌ 0% |

---

## 三、缺失测试统计

### 3.1 完全没有测试类的模块

| 模块 | 源文件数 | 问题 |
|------|---------|------|
| **agent** | 13 | 整个 Agent 端零测试 |
| **demo** | 2 | Demo 应用零测试 |

### 3.2 有测试但覆盖率低于 80% 的模块

| 模块 | 当前覆盖率 | 目标覆盖率 | 缺失测试数 |
|------|-----------|-----------|-----------|
| common | ~31% | 80% | ~11 个文件 |
| server | ~32% | 80% | ~26 个文件 |
| agent | 0% | 80% | 13 个文件 |
| demo | 0% | 80% | 2 个文件 |

### 3.3 完全没有 Service 层测试

所有 5 个 Service 类（DeployService, ProjectService, NodeService, AlarmService, VersionService）**无任何测试**。

### 3.4 完全没有 Mapper 层测试

所有 9 个 Mapper 接口（含 XML）测试需要使用 `@MybatisTest` 或 `MockBean`，但全部缺失。

### 3.5 Mock 不完整的测试

| 测试类 | 问题 |
|--------|------|
| DeployControllerTest | RestTemplate 调用未 Mock，实际发 HTTP 请求 |
| 所有 ControllerTest | 未 Mock `@Value("${server.path}")` 配置 |

---

## 四、统计汇总表

| 模块 | 源文件 | 测试文件 | 当前覆盖率 | 目标覆盖率 | 缺失测试数 | 缺失率 |
|------|--------|---------|-----------|-----------|-----------|--------|
| **common** | 16 | 5 | 31% | 80% | 11 | 69% |
| **server** | 38 | 12 | 32% | 80% | 26 | 68% |
| **agent** | 13 | 0 | 0% | 80% | 13 | 100% |
| **demo** | 2 | 0 | 0% | 80% | 2 | 100% |
| **合计** | **69** | **17** | **~32%** | **80%** | **52** | **75%** |

---

## 五、分类覆盖率

| 层级 | 源文件数 | 有测试 | 覆盖率 | 状态 |
|------|---------|--------|--------|------|
| Controller | 18 | 12 | 67% | ⚠️ 部分 |
| Service | 5 | 0 | 0% | ❌ 缺失 |
| Mapper | 9 | 0 | 0% | ❌ 缺失 |
| Scheduler | 2 | 0 | 0% | ❌ 缺失 |
| WebSocket | 3 | 0 | 0% | ❌ 缺失 |
| Interceptor | 2 | 0 | 0% | ❌ 缺失 |
| Model | 8 | 0 | 0% | ❌ 缺失 |
| Enum | 5 | 部分 | 40% | ⚠️ 部分 |
| Exception | 2 | 1 | 50% | ⚠️ 部分 |
| Config | 2 | 0 | 0% | ❌ 缺失 |
| Agent Handler | 4 | 0 | 0% | ❌ 缺失 |
| Agent Daemon | 2 | 0 | 0% | ❌ 缺失 |
| Agent Client | 1 | 0 | 0% | ❌ 缺失 |
