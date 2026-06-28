---
name: easy-ops-debug
description: This skill should be used when diagnosing and fixing bugs, investigating errors, or troubleshooting issues in the EasyOps distributed operations platform. Covers log locations, common failure patterns, debugging workflows, and diagnostic techniques for Server, Agent, and Frontend components.
---

# EasyOps 调试与问题排查

## Overview

快速定位 EasyOps 系统的运行问题：Server 端异常、Agent 通信故障、前端报错、部署失败等场景的排查方法。

## 日志文件位置

| 组件 | 日志路径 | 说明 |
|------|---------|------|
| Server | `backend/server/data/logs/server.log` | Spring Boot 应用日志 |
| Server | `backend/server/backend.log` | 启动脚本日志 |
| Agent | Agent 所在服务器的 `data/logs/` | Agent 运行日志 |
| 前端 | 浏览器 DevTools Console | Vue 错误 + 网络请求 |
| H2 数据库 | `backend/server/data/ops.mv.db` | 数据库文件（可查看 .trace.db） |

## 快速诊断命令

### Server 端

```bash
# 查看最新 100 行日志
tail -100 backend/server/backend.log

# 查看 Server 端口是否在监听
lsof -i:8081

# 检查 H2 数据库是否能连接
# 访问 http://localhost:8081/h2-console
# JDBC URL: jdbc:h2:./data/ops;MODE=MySQL
```

### Agent 端

```bash
# 查看 Agent 端口
lsof -i:2123

# 检查 Agent 心跳（Server 端查节点状态 API）
curl http://localhost:8081/api/nodes
```

### Docker Agent

```bash
# 查看 Agent 容器状态
docker ps -a | grep ops-agent

# 进入容器内部
docker exec -it ops-agent-1 /bin/bash

# 容器内测试
docker exec ops-agent-1 curl http://127.0.0.1:8080/hello
```

## 常见问题识别与解决

### 1. 页面加载 404 / 路由跳转 404

**症状**：点击菜单后页面显示 404，URL 路径正确。
**根因**：前端路由守卫 Token 过期或未登录。
**排查**：
1. 打开浏览器 DevTools → Network，看 API 请求是否返回 401
2. 检查 `localStorage` 中是否有 Token
**解决**：重新登录获取 Token。

### 2. 节点显示离线 / 心跳为空

**症状**：节点列表最后心跳时间为空或很久不更新。
**根因**：Agent 未启动、Token 不匹配、网络不通。
**排查**：
1. 确认 Agent 进程在运行：`lsof -i:2123`
2. 确认 Agent 启动时 Token 与 Server 端节点注册的 Token 一致
3. 检查 `backend/server/.../scheduler/HeartbeatChecker.java` 的 `offline-second` 配置（默认 20 秒）

### 3. 部署失败

**症状**：点击部署后状态变为失败。
**根因**：可能是停止旧进程失败、文件传输失败、启动新进程失败、健康检查失败中的某一步。
**排查步骤**（部署流程：停旧→传Jar→启新→健康检查）：
1. 查看部署记录详情中的日志字段
2. 进入 Agent 所在服务器，手动检查进程是否真的停了/起了
3. 检查 Agent 的 `data/` 目录下 Jar 包是否收到
4. 手动 curl 健康检查地址确认服务是否正常

### 4. 定时部署无法取消

**症状**：创建了定时部署，列表中没有取消按钮。
**排查**：
1. 检查 `deploy_record` 表中 `status` 字段值
2. 确认 `DeployController` 中有 `cancel` 接口
3. 检查 `DeployScheduler` 中定时任务的取消逻辑

### 5. 控制台连接不上

**症状**：在控制台页面输入命令后没有响应或连接失败。
**根因**：WebSocket 连接问题。
**排查**：
1. 浏览器 Network → WS 标签，看 WebSocket 连接状态
2. 确认 `vite.config.ts` 中 WebSocket 代理配置正确（`ws: true`）
3. 确认 Server 端 WebSocket Handler 正常：`ConsoleHandler`, `DeployHandler`, `MonitorHandler`

### 6. 前端 API 调用报错

**症状**：页面操作后弹 `[网络错误]` 或 `[请求失败]`。
**排查**：
1. 打开浏览器 Network，找到对应 API 请求
2. 查看 Request Header 中是否有 `X-Token`
3. 查看 Response 中的 `code` 和 `message` 字段
4. 如果是 500 错误，去看 Server 日志

### 7. 编译报错

**症状**：`mvn compile` 失败。
**根因**：依赖缺失或 Java 版本问题。
**排查**：
1. 确认 `JAVA_HOME` 指向 JDK 1.8
2. 确认 Maven 本地仓库有依赖（内网可能需要私有仓库）
3. 常见缺失依赖：Hutool、Fastjson2、OSHI、MyBatis、Quartz

## 调试工作流

```
发现问题
  │
  ├─ 前端问题 → DevTools Console + Network → 找 API 响应
  │     └─ 401/403 → Token 问题
  │     └─ 500 → 看 Server 日志
  │     └─ 404 → 检查路径/路由配置
  │
  ├─ 后端 Server 问题 → tail server.log → 找 Exception 堆栈
  │     └─ SQL 异常 → H2 控制台查数据
  │     └─ NPE → 检查 Service 层空值处理
  │     └─ 连接超时 → 检查 Agent 是否在线
  │
  └─ Agent 通信问题 → 确认 Agent 进程 → 检查 Token → 检查网络
        └─ Docker → docker logs ops-agent-1
```

## 关键代码位置

| 功能 | 文件 |
|------|------|
| 全局异常处理 | `backend/server/.../exception/GlobalExceptionHandler.java` |
| 认证拦截器 | `backend/server/.../interceptor/AuthInterceptor.java` |
| 心跳检测 | `backend/server/.../scheduler/HeartbeatChecker.java` |
| 部署流程 | `backend/server/.../service/DeployService.java` （查找 `publish` 方法） |
| Agent 心跳 | `backend/agent/.../daemon/HeartbeatDaemon.java` |
| 前端请求 | `frontend/src/utils/request.ts` |
| 前端路由守卫 | `frontend/src/router/index.ts` |
| WebSocket 代理 | `frontend/vite.config.ts` |
