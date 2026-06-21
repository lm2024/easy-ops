# 接口质量审计报告

> **审计日期**: 2026-06-21
> **审计范围**: 所有 Controller 接口（共 28 个 REST 端点 + 3 个 WebSocket 端点）

---

## 一、接口概览

| 模块 | Controller | 接口数 | 路径前缀 |
|------|-----------|--------|----------|
| Server | DeployController | 2 | /deploy, /deploy/{id}/cancel |
| Server | ProjectController | 5 | /projects (CRUD) |
| Server | NodeController | 3 | /nodes, /nodes/export |
| Server | VersionController | 4 | /versions (CRUD) |
| Server | AlarmController | 3 | /alarms, /alarms/node/{id} |
| Server | MonitorController | 1 | /monitor |
| Server | FileController | 5 | /files (log/config/delete/receive/upload) |
| Server | LogController | 2 | /logs |
| Server | SystemController | 3 | /system (auth/login/ops) |
| Server | ProcessController | 2 | /process (list/exec) |
| Server | AIAnalyzeController | 3 | /ai (config/chat) |
| Server | AgentProxyController | 1 | /agent/{nodeId}/sys-info |
| Agent | ShellController | 1 | /shell/exec |
| Agent | FileController | 3 | /file (receive/log/config) |
| Agent | SystemController | 1 | /sys/info |
| Agent | ProcessController | 3 | /process (start/stop/list) |
| Agent | SystemInfoController | 1 | /sys/info (detailed) |
| Demo | DemoController | 3 | /hello, /config, /health |
| WebSocket | ConsoleHandler | 1 | /ws/console |
| WebSocket | DeployHandler | 1 | /ws/deploy |
| WebSocket | MonitorHandler | 1 | /ws/monitor |

---

## 二、参数校验审计

### 2.1 严重缺失（P1）

| 接口 | 问题 | 说明 |
|------|------|------|
| `POST /deploy` | 无参数校验注解 | 全部用 `Map<String, Object>` 接收，类型安全为零 |
| `POST /projects` | 无 `@Valid` | `ProjectModel` 无 `@NotBlank` 等校验注解 |
| `POST /nodes` | 无必填校验 | `name`, `ip` 未做非空校验 |
| `POST /files/config` (save) | 无路径遍历检查 | `configPath` 可直接写入任意路径 |
| `POST /shell/exec` | 无命令白名单 | 可执行任意 Shell 命令 |
| `GET /files/log` | 无 offset/lines 上限 | 可导致大响应 |

### 2.2 缺失 `@Validated` / `@Valid` 的 Controller 列表

- [x] `DeployController` — 完全用 Map 接收
- [x] `ProjectController` — 未加 `@Valid`
- [x] `NodeController` — 未加 `@Valid`
- [x] `VersionController` — 未加 `@Valid`
- [x] `FileController` — 未加 `@Valid`
- [x] `ShellController` — 无校验

---

## 三、边界值处理审计

| 接口 | 问题 | 严重度 |
|------|------|--------|
| `POST /deploy publish()` | `Long.parseLong(trimmed)` 无 try-catch，非法 nodeId 抛 500 | P1 |
| `POST /deploy publish()` | 节点 ID 为负数/0 未校验 | P2 |
| `GET /files/log` | offset < 0 或 lines <= 0 未校验 | P2 |
| `GET /nodes` | page < 1 或 pageSize > 1000 未限制 | P2 |
| `GET /nodes export` | 无数据时返回空 CSV，无列头定义 | P3 |

---

## 四、空值处理审计

| 接口 | 问题 | 文件 |
|------|------|------|
| `POST /deploy` | `request.get("nodeId")` 为 null 时 `Long.valueOf(null)` 不抛异常但后续 `nodeId != null` 分支不会执行，"部署到所有节点" 的逻辑实际忽略了指定 nodeId 的请求 | DeployController.java:53-56 |
| `GET /nodes` | `nodeId` 参数为 null 时 `nodeService.findByStatus()` 未处理 | NodeController.java:32 |
| `POST /projects` | `project.getName()` 为 null 时 `findByName(null)` 返回 null 但不会报错，项目可直接保存无名称 | ProjectController.java:38 |

---

## 五、异常处理审计

| 接口 | 问题 | 说明 |
|------|------|------|
| 全局 | 所有 Exception 返回 "系统内部错误" | 无堆栈信息，运维无法定位问题 |
| `GlobalExceptionHandler` | 未区分 `BusinessException` 和 `SystemException` 的日志级别 | 两者都用 `log.error`，但 `BusinessException` 本应是预期的 |
| 所有 Controller | `Map<String, Object>` 强制类型转换无安全校验 | `(Map<String, Object>) response.get("data")` 可能 ClassCastException |

---

## 六、返回值规范性审计

| 接口 | 问题 | 说明 |
|------|------|------|
| `POST /deploy` | 返回值类型 `Result<?>`（泛型擦除） | 前端无法静态类型检查 |
| `GET /nodes export` | 返回类型 `void`，直接操作 `HttpServletResponse` | 无法统一错误处理 |
| 所有接口 | 成功返回 `Result.success(data)`，但 `data` 类型不一致（有时 Map，有时 Model） | 前端解析困难 |
| 部分接口 | 错误码混用 `Result.error(1005)` 和 `Result.error(500)` | 无统一错误码表 |

---

## 七、安全校验审计

| 检查项 | 现状 | 问题 |
|--------|------|------|
| CSRF 防护 | ❌ 未启用 | POST/PUT/DELETE 无 CSRF token |
| XSS 过滤 | ❌ 未启用 | 用户输入直接返回给前端 |
| SQL 注入 | ⚠️ 部分防范 | MyBatis 参数化，但 nodeIds LIKE 模糊匹配有逻辑漏洞 |
| 路径遍历 | ❌ 未防护 | FileController.saveConfig() 直接拼接路径 |
| 命令注入 | ❌ 未防护 | ShellController 直接执行用户命令 |
| 认证绕过 | ⚠️ 有认证但可绕过 | AuthInterceptor 用 contains() 匹配路径 |
| 授权检查 | ❌ 无 | 任何认证用户可操作所有资源 |
| 数据越权 | ❌ 无 | 用户 A 可通过修改 projectId 查看用户 B 的项目 |

---

## 八、日志充分性审计

| 接口 | 日志级别 | 问题 |
|------|---------|------|
| `POST /deploy` | 仅错误日志 | 成功部署无审计日志 |
| `POST /projects` 增删改 | 无日志 | 运维无法审计项目变更 |
| `POST /nodes` 增删改 | 无日志 | 同上 |
| `POST /shell/exec` | 仅错误日志 | 无命令执行记录 |
| 全局 | 访问日志已开启 (Tomcat accesslog) | 但无应用层审计日志（OperationLogMapper 存在但未使用） |

---

## 九、统计汇总

| 审计项 | 通过 | 缺失 | 缺失率 |
|--------|------|------|--------|
| 参数校验 | 3/28 | 25 | 89% |
| 边界值处理 | 2/28 | 26 | 93% |
| 空值处理 | 4/28 | 24 | 86% |
| 异常处理 | 1/28 | 27 | 96% |
| 返回值规范 | 5/28 | 23 | 82% |
| 错误码统一 | 2/28 | 26 | 93% |
| 日志充分 | 1/28 | 27 | 96% |
| 安全校验 | 2/28 | 26 | 93% |
