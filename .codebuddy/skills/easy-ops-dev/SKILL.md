---
name: easy-ops-dev
description: This skill should be used when developing new features, modifying existing code, or adding APIs in the EasyOps project — a Java 8 + Spring Boot 2.7 Server-Agent distributed operations platform with Vue 3 frontend. Use this skill for any code changes to the backend (Server/Agent/Common modules) or frontend.
---

# EasyOps 开发指南

## Overview

Server-Agent 架构的分布式运维平台。Java 8 硬性约束，Spring Boot 2.7.18，H2 嵌入式数据库，Vue 3 + Ant Design Vue 前端。

## 代码路径速查

| 要改什么 | 去哪里 |
|---------|--------|
| API 接口 | `backend/server/.../controller/` |
| 业务逻辑 | `backend/server/.../service/` |
| 数据库操作 | `backend/server/.../mapper/` → XML 在 `resources/mapper/` |
| 共享枚举/模型 | `backend/common/.../enums/` `model/` |
| Agent 执行逻辑 | `backend/agent/.../controller/` `handler/` |
| 前端页面 | `frontend/src/views/` |
| 前端 API 调用 | `frontend/src/api/` |
| TypeScript 类型 | `frontend/src/types/index.ts` |

## 后端开发规范

### 硬性约束

- **Java 8 语法**：禁止使用 var、lambda 中的 var、文本块、record 等 Java 9+ 特性
- **单类不超过 500 行**：超过则拆分为多个类
- **Controller 薄，Service 厚**：Controller 只做参数校验和调用 Service，业务逻辑全在 Service
- **统一响应**：所有接口返回 `Result<T>`（`com.ops.common.response.Result`）
- **异常不吞**：通过 `GlobalExceptionHandler` 统一兜底

### 新增 API 的标准流程

1. 在 `common` 模块定义请求/响应模型（如需要）
2. 在 Controller 中新增接口方法，添加 `/api` 文档注释
3. 在 Service 接口中定义方法，Impl 中实现
4. 在 Mapper 接口中定义 SQL 方法，XML 中写 SQL
5. 如涉及权限，检查 `UserRole` 和 `user_project_relation` 表
6. 前端在 `api/` 下新增请求方法，类型定义写入 `types/index.ts`

### Server-Agent 通信

- Server → Agent：通过 `AgentProxyController` 代理转发，或通过 `HttpClient` 直接调用
- Agent → Server：心跳通过 `HeartbeatDaemon`，实时数据通过 `WebSocketClient`
- Agent 接口路径规则：`/api/file/...`, `/api/process/...`, `/api/shell/...`, `/api/system-info/...`

### 数据库操作

- 表结构定义在 `backend/server/src/main/resources/db/schema.sql`
- H2 MySQL 兼容模式，MyBatis 开启下划线转驼峰
- 新增表需在 `schema.sql` 中追加 CREATE TABLE 语句
- 查询方法写在对应的 Mapper 接口 + XML 中

### 安全

- JWT Token 24 小时过期，存储在 `AuthInterceptor` 中校验
- 密码使用 BCrypt 哈希，禁止明文存储
- 用户-项目关系通过 `user_project_relation` 表控制权限
- 操作审计自动记录到 `operation_log` 表

## 前端开发规范

### 技术栈

Vue 3 Composition API + `<script setup lang="ts">` + Ant Design Vue 4

### 新增页面的标准流程

1. 在 `views/` 下创建 `.vue` 文件
2. 在 `router/index.ts` 中注册路由
3. 在 `api/` 对应模块文件中添加 API 方法
4. 在 `types/index.ts` 中添加接口类型定义
5. 在 `MainLayout.vue` 侧边栏菜单中添加菜单项（如需）

### 组件使用

- 表格：优先使用 Ant Design Vue `<a-table>`，配合 `request` 属性自动请求
- 表单：`<a-form>` + `<a-form-item>`，用 `v-model` 绑定
- 弹窗：`<a-modal>` 包裹表单组件
- 消息提示：`message.success()`, `message.error()` 来自 `ant-design-vue`
- 状态管理：认证状态用 `stores/auth.ts`，全局状态用 `stores/app.ts`

### 请求封装

- Axios 实例在 `utils/request.ts`，自动注入 Token、处理 `code !== 200` 统一弹错误
- 文件上传用 `multipart/form-data`，下载用 `responseType: 'blob'`

## 编码约束清单

- [ ] 所有文件 UTF-8 编码
- [ ] Java 类不超过 500 行
- [ ] 文档不超过 400 行
- [ ] Controller 不写业务逻辑
- [ ] 所有 API 返回 `Result<T>`
- [ ] 异常不捕获后吞掉（除明确需要兜底外）
- [ ] 敏感信息（Token、密码、API Key）通过环境变量注入，不硬编码
- [ ] 前端所有变量和函数有 TypeScript 类型
- [ ] 新增表结构同步更新 `schema.sql`
