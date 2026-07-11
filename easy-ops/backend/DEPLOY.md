# EasyOps 内网私有化部署指南

目标：**JDK 8**、**无公网**、**Server / Agent / 前端各一台**（三台互不共享配置）。

> 运行只需 fat jar；改代码重打包需 `local-repo/`（~149MB）+ Maven。详见下文「离线构建」。

---

## 一、三机部署（推荐）

每台机器：**jar + `scripts/` 目录**，只改本机 `scripts/easyops.env`，**不改 YAML**（路径由 `start.sh` 注入）。

### Server 机

```
/data/easy-ops-server/
  ops-platform-server.jar          # 即 target 产物，可改名
  scripts/
    easyops.env   start.sh  stop.sh  restart.sh
  data/                            # 自动创建（H2、版本包、日志）
```

`easyops.env` 必改：`JWT_SECRET`、`AGENT_DATA_PATH`（= **Agent 机** `{INSTALL_DIR}/data`）

```bash
cd /data/easy-ops-server/scripts && ./start.sh
```

### Agent 机

```
/data/easy-ops-agent/
  easy-ops-agent.jar
  scripts/
    easyops.env   start.sh  stop.sh  restart.sh
  data/
```

`easyops.env` 必改：`AGENT_SERVER_URL=http://<ServerIP>:8081/api`、`AGENT_NODE_NAME`  
`AGENT_TOKEN` 可留空（启动时自动生成，见日志）

```bash
cd /data/easy-ops-agent/scripts && ./start.sh
```

裸机自升级：`AGENT_RESTART_MODE=shell`（env 默认已设），重启走本机 `scripts/start.sh`。

### 前端机

```
/data/easy-ops-frontend/
  dist/                            # build.sh 产物
  scripts/
    easyops.env   start.sh  stop.sh  restart.sh  build.sh
```

1. **构建机**（有 Node）：`frontend/scripts/build.sh`  
2. 拷 `dist/` + `scripts/` 到前端机  
3. `easyops.env` 必改：`SERVER_API_URL=http://<ServerIP>:8081`、`SERVE_MODE=nginx`  
4. 前端机只需 **Nginx + dist**，无需 Node、无需联网

```bash
cd /data/easy-ops-frontend/scripts && ./start.sh
```

### 路径对齐（最重要）

| Server 配置 | 必须等于 |
|-------------|----------|
| `AGENT_DATA_PATH` | Agent 机 `data/` 绝对路径 |

Agent 版本包目录：`{data}/versions/{projectId}/{version}/`

启动后日志搜 **`启动路径`** 核对。

---

## 二、拷贝清单

### 最小运行（单组件）

| 组件 | 拷贝内容 | 依赖 |
|------|----------|------|
| Server | jar + `server/scripts/` + **已有 `data/`**（H2，必带） | JDK 8 |
| Agent | jar + `agent/scripts/` | JDK 8 |
| 前端 | `dist/` + `scripts/` | Nginx |

### 可改码重打包

整个 `backend/`（含 `local-repo/`、源码）→ 内网 `mvn -o -Dmaven.repo.local=local-repo package -DskipTests`  
或：`./build-offline.sh`

---

## 三、开发环境

```bash
# 编译
cd backend && mvn package -DskipTests

# 各组件脚本（自动用 target 产物）
backend/server/scripts/start.sh
backend/agent/scripts/start.sh
frontend/scripts/start.sh          # SERVE_MODE=dev

# Docker 多 Agent（仅开发）
cd backend && docker-compose up -d
# 坑：容器内需 JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true
```

| 脚本 | 用途 |
|------|------|
| `backend/start.sh` | macOS 开发（写死 JDK 路径） |
| `backend/start-prod.sh` | 旧版 Linux 启动，**推荐改用 `server/scripts/`** |
| `*/scripts/start\|stop\|restart.sh` | 私有化标准入口 |

---

## 四、Agent 自升级（裸机）

| 环境 | 拉起方式 |
|------|----------|
| Docker（开发） | 换 jar → `exit` → 容器 restart |
| 裸机（生产） | 换 jar → 脚本等旧进程退出 → `setsid` 拉新进程 → 失败自动回滚 jar |

**排障专用日志**：`{data}/logs/upgrade-restart.log`（与 agent.log 分离，记录每一步 FAIL 原因）

生产务必：`AGENT_RESTART_MODE=shell`，且 `agent.restart-script` 指向本机 `scripts/start.sh`（`start.sh` 启动时已注入 `-Dagent.restart-script`）。

流程：备份 jar → 替换 → 后台脚本 → 旧进程 2 秒后 exit 释放端口 → 脚本拉起新进程并验证 → 失败则用备份 jar 回滚。

---

## 五、部署与健康检查

流程：停旧 → 传 Jar → 启动 → 健康检查 →（可回滚）

**无健康端口的项目**：在项目管理关闭「部署后健康检查」，或改端口/路径/关键字。  
默认：`8080` / `/hello` / 响应含 `Hello` 或 `DEPLOYED`。

---

## 六、已知坑

| 问题 | 原因 / 处理 |
|------|-------------|
| H2 数据丢失 | 从不同 cwd 启动，`server.path` 相对路径不一致 → **必须用 `scripts/start.sh`** |
| 部署路径错 | Server `AGENT_DATA_PATH` 与 Agent 机 `data/` 不一致 |
| 部署成功但应用没起 | 部署目录无 `logs/`，`start.sh` 重定向失败 → 已修：`ProcessController` 先 `mkdir -p logs` + `setsid` |
| 健康检查全失败 | 应用未监听 8080 / 无 `/hello` → 关健康检查或改配置 |
| Agent 心跳离线（Docker） | IPv6 连 `host.docker.internal` → 加 `preferIPv4Stack` |
| 裸机升级后 Agent 没起来 | 未用 `setsid` 脱离进程组 → 用 `scripts/start.sh` |
| Token 冲突 | 两台 Agent 共用 Token → 心跳互相覆盖 |
| JDK 8 编译失败 | 测试代码用了 `Map.of`/`Path.of` → 改用 Java 8 API |
| `start.sh` 在 Linux 失败 | 写死 macOS JDK → 用 `server/scripts/start.sh` |

---

## 七、Jar 与端口

| 组件 | Jar | 端口 |
|------|-----|------|
| Server | `ops-platform-server-1.0.0-SNAPSHOT.jar` | 8081 `/api` |
| Agent | `easy-ops-agent-1.0.0-SNAPSHOT.jar` | 2123 `/api` |
| 前端 | dist（Nginx） | 80/3000 |

默认账号：`admin / admin123`

---

## 八、配置原则

- **私有化**：只改各机 `scripts/easyops.env`
- **端口**：改 env 或 `application.yml` 二选一
- **路径**：由启动脚本 `-Dserver.path` / `-Dagent.data-path` 注入，**不要手改 YAML**
- **敏感项**：`JWT_SECRET`、`AI_API_KEY`、`AGENT_TOKEN` 走环境变量

更完整的 AI 开发提示见仓库根目录 `AGENTS.md`。
