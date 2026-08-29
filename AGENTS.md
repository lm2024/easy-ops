# AGENTS.md — EasyOps AI 提示词

## 身份与职责
用户称呼：叫我大佬 | 我负责：Docker Agent 打包发布,agent服务必须用 docker 启动 | 大佬负责：本地Server、前端（需要重启告诉我）
问题解决：刨根问底找根因，架构设计解决，不能还原代码或用偏方

## 项目架构
分布式运维平台：**Server ( 9091) ↔ Agent (2123)**，WebSocket 推送。代码根 `easy-ops/`（多一层嵌套）。
| 层 | 技术栈 |
|----|--------|
| Server/Agent | Java 8, Spring Boot 2.7, MyBatis, H2 (MySQL模式), JWT, Quartz |
| Agent | RestTemplate, WebSocket Client, OSHI |
| Common | 共享 model/enum/Result/异常 |
| 前端 | Vue 3 + Vite 5 + TS + Ant Design Vue 4 + Pinia + ECharts + xterm + Monaco |

构建：`cd easy-ops/backend && mvn package -DskipTests` | 默认账号：`admin / Admin123!`

## 部署规则（三机不共享）
每台只拷自己的 jar + 脚本，只改 `easyops.env`，**YAML 不动**。
| 机器 | 脚本目录 | 必改项 |
|------|----------|--------|
| Server | `backend/server/` | `JWT_SECRET`, `AGENT_DATA_PATH` |
| Agent | `backend/agent/` | `AGENT_SERVER_URL`, `AGENT_NODE_NAME`, `AGENT_TOKEN` |
| 前端 | `frontend/scripts/` | `SERVER_API_URL`, `SERVE_MODE=nginx` |
demo-test-app是测试的应用服务,需要部署Docker agent 服务中;方便 agent 监控demo-test-app应用
nginx-demo如果涉及到测试调试修改 NGINX 监控功能的时候,必须用 demo 服务去在 agent Docker 节点进行部署,方便监控 NGINX
启动：`./start.sh` / `./stop.sh`

### 前端部署契约（2026-08-18）
- zip 文件名 = 解压目录名，禁止自定义 | 上传 `xxx.zip` → 解压到 `{deployDir}/xxx/`，zip 存档到 `{deployDir}/versions/`
- 重部署/清理**只处理**同名文件夹和本次 zip，其他文件一律保留
- 安全校验：文件名须单段、非系统目录、不在 versions 内 | zip 未解压出文件直接报错

### Agent 自升级
Docker：换 jar → `exit` → 容器 restart | 裸机：外置脚本等旧进程退出 → `setsid` 拉新 → 失败回滚

## 关键配置
敏感项走环境变量：`JWT_SECRET`, `AI_API_KEY`, `AGENT_TOKEN`
| 配置项 | 说明 |
|--------|------|
| `server.path` | Server 数据根 |
| `agent.data-path` | Agent 数据根 |
| `ops.global.agent-data-path` | Server 计算 Agent 路径用 |
| `AGENT_TOKEN` | 留空自动生成 |

## API 映射
Server：`/auth` `/nodes` `/projects` `/versions` `/deploy` `/files` `/logs` `/log-mgmt` `/process` `/agent` `/system` `/db` `/monitor` `/alarms` `/ai` `/self-heal` `/notifications` `/config` `/kb`
Agent：`/file` `/process` `/shell` `/sys` `/system` | WebSocket：`/ws/console` `/ws/deploy` `/ws/monitor` `/ws/notification` `/ws/kb-collab`

### 自动化登录（白名单免验证码）
```bash
export AUTO_LOGIN_ENABLED=true AUTO_LOGIN_WHITELIST=ops-auto-login-2026
```
Token：`POST /api/auth/auto-login {"key":"ops-auto-login-2026"}`

## 数据库
H2（36 表），核心：`node_info` `project_info` `version_package` `deploy_record` `sys_user` `operation_log` `file_access_log`

## 编码规范
1. 单文件 ≤400 行（DeployController 容错 500+）| 模块隔离，禁止跨模块依赖
2. Controller 异常走 `GlobalExceptionHandler`，统一 `Result<T>` | common ← server/agent，禁止反向
3. 前端全 TS，类型在 `frontend/src/types/index.ts` | 敏感配置走环境变量
4. 保持 JDK 8 语法，不用 Java 9+ API | 代码极简，能 3 行不写 10 行 | 优先性能（DB 操作、并发）

## 核心原则
- 端口冲突：结束冲突端口，不能改代码
- Docker Agent：我负责部署验证，需要重启自己来 | Server/前端：大佬负责
- 兜底方案：任何功能都要有 | 功能测试：开发完必须测，纯后端测 API，前后端用无头浏览器
- 奇葩场景：模拟各种输入，防空指针/宕机 | 测试数据：系统默认有数据，做成脚本可重置

## 已知坑（关键）
- H2 路径跟 cwd 走，必须用 `scripts/start.sh` 注入绝对路径
- 路径对齐：`AGENT_DATA_PATH`(Server) = `agent.data-path`(Agent)
- Docker 心跳：加 `-Djava.net.preferIPv4Stack=true` | 健康检查：每项目独立配置
- Docker Agent 缺 ps：Dockerfile 加 `RUN apt-get install -y procps` | 僵尸进程：用 `tini` 作 PID 1
- H2 膨胀：定时重启 `./stop.sh && ./start.sh` | H2 URL：`jdbc:h2:file:.../ops` → 磁盘 `ops.mv.db`
- YAML map 不能用 `@Value`，用 `@ConfigurationProperties` | bash 变量后跟全角字符用 `${var}`

## Nginx 流量监控
预聚合表 `nginx_minute_stat` 按 `(source_id, bucket_time, client_ip, uri, method)` 唯一
新维度 = 改存储列 + Agent 聚合 key + SQL（三步成对）| 白名单 `nginx_source_whitelist` 查询侧过滤
趋势跳转：点击某分钟桶 → 设 `customRange` → 切排名分析 | 状态码/地区/运营商为后续专项

## H2 数据库维护
启动压缩：`compactBeforeStart()` 超阈值压缩 | 应急：`scripts/rescue-h2.sh <data目录>`
清理：`DataCleanupScheduler` 管 21 张表，cron/保留天数在 `application.yml` 配置

## 租户隔离（CRUD 必读）
- admin 终极兜底：`isSuperAdmin()` 放行 | tenantId=null 平台视图：跳过租户过滤
- 数据列表：只按 `tenantId` 过滤 | 节点管理/升级/导出：`tenantId OR defaultTenantId`（含池节点）
- 创建资源：自动设置 `tenantId`，平台视图归默认租户

## 应用监控要点
进程发现：`jps -lm` + `ps aux | grep java`，jps 不可用回退 ps | 状态一致：先进程后健康（STOPPED→DOWN，RUNNING→看资源）
WS 推送：只推实时指标，状态走 HTTP/DB | 前端刷新：`mergeDashboard` 保留 WS 实时值
Docker jar 更新：`docker exec cp /app/agent.jar /app/data/agent.jar && restart`
日志：中文、补异常栈、高频 DEBUG/关键 INFO/可恢复 WARN/严重 ERROR


人工补充:
文档写在docs 目录中,在该目录下根据日期,需求来创建文件夹,在文件夹内编写文档,文档要简单清晰
如果后续开发需要e2e端到端测试,那么你就看看该文件夹e2e,曾经的测试在这里写的
必须强调下监控功能,主要是要监控部署的应用,agent 自身监控,这两个要区分清楚,不要混淆
尤其是 ws 消息发送的时候,监控的是谁,谁是被监控的要搞清楚在改代码
每次有任何改动, 都告诉我改了哪个服务, 多少个文件是否需要重启服务,重新部署哪些服务等