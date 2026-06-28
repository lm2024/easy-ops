---
name: easy-ops-setup
description: This skill should be used when the user needs to set up, start, or verify the EasyOps development environment — including compiling the backend (Maven + Java 8), starting Server/Agent, running the frontend (Vue 3 + Vite), and troubleshooting common startup errors.
---

# EasyOps 环境搭建与启动

## Overview

Mac 本地开发环境下，从零编译并启动 EasyOps 的 Server、Agent、前端三个组件。

## 先决条件

| 依赖    | 版本        | 检查命令 |
|---------|------------|---------|
| JDK     | 1.8 (Corretto) | `java -version` |
| Maven   | 3.6+       | `mvn -version` |
| Node.js | 18+        | `node -v` |

默认 JAVA_HOME（Mac）:
```bash
export JAVA_HOME=/Users/lm/Library/Java/JavaVirtualMachines/corretto-1.8.0_462/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

## 启动流程

### Step 1: 编译后端

```bash
cd backend
mvn -pl server -am dependency:build-classpath -Dmdep.outputFile=/tmp/classpath.txt -DincludeScope=runtime
mvn -pl server -am compile -DskipTests
```

关键文件：
- `backend/pom.xml` — 父 POM，管理 server/agent/common 三模块
- `backend/common/` — 共享枚举/模型/异常/响应类
- `backend/server/` — Server 端（端口 8081，上下文 /api）
- `backend/agent/` — Agent 端（端口 2123，上下文 /api）

### Step 2: 启动 Server

```bash
CLASSPATH="server/target/classes:server/src/main/resources:$(cat /tmp/classpath.txt)"
cd backend/server
java -cp "$CLASSPATH" com.ops.server.ServerApplication
```

Server 启动后：
- API 地址: `http://localhost:8081/api/`
- H2 控制台: `http://localhost:8081/h2-console`（JDBC URL: `jdbc:h2:./data/ops`，密码空）
- 默认管理员: `admin / admin123`
- 数据库文件: `backend/server/data/ops.mv.db`

### Step 3: 启动 Agent

```bash
CLASSPATH="../agent/target/classes:../agent/src/main/resources:$(cat /tmp/classpath.txt)"
cd backend
java -cp "$CLASSPATH" com.ops.agent.AgentApplication \
  --agent.token=agent-token-1 \
  --agent.node-name=node-1
```

Agent 必须配置 `AGENT_TOKEN`，否则启动失败。

### Step 4: 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 地址: `http://localhost:3000`
- Vite 代理: `/api` → `http://localhost:8081`（含 WebSocket）

## 常见启动问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `java` 命令找不到 | JAVA_HOME 未设置 | 设置 Corretto 路径 |
| 前端 `npm install` 失败 | 内网环境无 registry | 配置内网 npm registry |
| Agent 启动报 Token 错误 | `agent.token` 未传或无效 | 确认 Server 端已注册该 Token |
| Server 端口被占用 | 已有进程在 8081 | `lsof -i:8081` 杀掉旧进程 |
| 页面 404 | 路由守卫 Token 过期 | 重新登录获取新 Token |
| H2 数据库文件找不到 | `server.path` 配置不对 | 检查 `data/ops.mv.db` 位置 |

## 架构速查

```
前端 (Vue 3, :3000)
  │  HTTP/WS Proxy
  ▼
Server (Spring Boot, :8081/api)
  │  HTTP + Token
  ▼
Agent (Spring Boot, :2123/api)
  └─ WebSocket Client → Server
```

## 关键配置位置

| 组件 | 配置文件 |
|------|---------|
| Server | `backend/server/src/main/resources/application.yml` |
| Agent | `backend/agent/src/main/resources/application.yml` |
| 前端 | `frontend/vite.config.ts` |
| 数据库 | `backend/server/src/main/resources/db/schema.sql` |
