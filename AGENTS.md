# AGENTS.md — EasyOps AI 提示词

## 身份与职责
用户称呼：叫我大佬 | 我负责：Docker Agent 打包发布（必须 docker 启动）| 大佬负责：本地Server、前端（重启告诉我）
问题解决：刨根问底找根因，架构设计解决，不还原代码或用偏方

## 项目架构
分布式运维平台：Server(9091) ↔ Agent(2123)，WebSocket 推送。代码根 `easy-ops/`（多一层嵌套）
Server/Agent: Java8, SpringBoot2.7, MyBatis, H2(MySQL模式), JWT, Quartz | Agent: RestTemplate, WS Client, OSHI | Common: 共享model/enum/Result/异常 | 前端: Vue3+Vite5+TS+AntD4+Pinia+ECharts+xterm+Monaco
构建：`cd easy-ops/backend && mvn package -DskipTests` | 默认账号：`admin / Admin123!`

## 部署规则（三机不共享）
每台只拷自己的 jar+脚本，只改 `easyops.env`，**YAML 不动**
Server→`backend/server/` 改 `JWT_SECRET`+`AGENT_DATA_PATH` | Agent→`backend/agent/` 改 `AGENT_SERVER_URL`+`AGENT_NODE_NAME`+`AGENT_TOKEN` | 前端→`frontend/scripts/` 改 `SERVER_API_URL`+`SERVE_MODE=nginx`
demo-test-app / nginx-demo 测试服务部署在 Agent Docker 节点，方便监控 | 启动：`./start.sh` / `./stop.sh`

### 前端部署契约（2026-08-18）
zip 文件名=解压目录名，禁止自定义 | 上传 `xxx.zip`→解压到 `{deployDir}/xxx/`，存档到 `{deployDir}/versions/`
重部署/清理只处理同名文件夹+本次 zip，其他一律保留 | 安全校验：文件名单段、非系统目录、不在 versions 内；zip 未解压出文件直接报错

### Agent 自升级
Docker：换 jar→`exit`→容器 restart | 裸机：外置脚本等旧进程退出→`setsid` 拉新→失败回滚

## 关键配置
敏感项走环境变量：`JWT_SECRET` `AI_API_KEY` `AGENT_TOKEN` | `server.path`=Server数据根 | `agent.data-path`=Agent数据根 | `ops.global.agent-data-path`=Server算Agent路径 | `AGENT_TOKEN` 留空自动生成

## API 映射
Server：`/auth /nodes /projects /versions /deploy /files /logs /log-mgmt /process /agent /system /db /monitor /alarms /ai /self-heal /notifications /config /kb /arthas`
Agent：`/file /process /shell /sys /system /arthas` | WS：`/ws/console /ws/deploy /ws/monitor /ws/notification /ws/kb-collab`

### 自动化登录（白名单免验证码）
`export AUTO_LOGIN_ENABLED=true AUTO_LOGIN_WHITELIST=ops-auto-login-2026` | Token：`POST /api/auth/auto-login {"key":"ops-auto-login-2026"}`

## 数据库
H2（36+ 表），核心：`node_info project_info version_package deploy_record sys_user operation_log file_access_log`

## 编码规范
1. 单文件 ≤400 行（DeployController 容错 500+）| 模块隔离，禁止跨模块依赖
2. Controller 异常走 `GlobalExceptionHandler`，统一 `Result<T>` | common←server/agent，禁止反向
3. 前端全 TS，类型在 `frontend/src/types/index.ts` | 敏感配置走环境变量
4. 保持 JDK 8 语法，不用 Java 9+ API | 代码极简，能 3 行不写 10 行 | 优先性能

## 核心原则
端口冲突：结束冲突端口，不能改代码 | Docker Agent 我负责部署验证，重启自己来；Server/前端大佬负责
兜底方案：任何功能都要有 | 功能测试：开发完必须测，纯后端测 API，前后端无头浏览器
奇葩场景：模拟各种输入，防空指针/宕机 | 测试数据：默认有数据，脚本可重置

## 已知坑（关键）
H2 路径跟 cwd 走，必须用 `scripts/start.sh` 注入绝对路径 | 路径对齐：`AGENT_DATA_PATH`=`agent.data-path`
Docker 心跳加 `-Djava.net.preferIPv4Stack=true` | 健康检查每项目独立配置 | Docker Agent 缺 ps：Dockerfile 加 `RUN apt-get install -y procps` | 僵尸进程用 `tini` 作 PID 1
H2 膨胀：定时重启 `./stop.sh && ./start.sh` | H2 URL `jdbc:h2:file:.../ops`→磁盘 `ops.mv.db` | YAML map 不用 `@Value`，用 `@ConfigurationProperties` | bash 变量后跟全角字符用 `${var}`

## Git 提交大小红线（2026-08-29）
仓库目标 **≤1GB**（超了 GitHub 警告），强上限 **5GB**；当前 `.git`≈8MB
单文件 **≤50MB**（>50MB 告警，>100MB 直接拒 push，网页上传 ≤25MB）
铁律：`*.jar` `*.log` `*.zip` `local-repo` `data` IDE·AI配置 永不提交（`.gitignore` 已配，提交前 `git status`）
自查：`du -sh .git`>50MB 即排查历史大文件，用 `git filter-repo` 清理（先备份 bundle）

## Nginx 流量监控
预聚合表 `nginx_minute_stat` 按 `(source_id, bucket_time, client_ip, uri, method)` 唯一
新维度=改存储列+Agent 聚合 key+SQL（三步成对）| 白名单 `nginx_source_whitelist` 查询侧过滤
趋势跳转：点分钟桶→设 `customRange`→切排名分析 | 状态码/地区/运营商为后续专项

## H2 数据库维护
启动压缩 `compactBeforeStart()` 超阈值压缩 | 应急 `scripts/rescue-h2.sh <data目录>` | 清理 `DataCleanupScheduler` 管 21 张表，cron/保留天数在 `application.yml`

## 租户隔离（CRUD 必读）
admin 兜底 `isSuperAdmin()` 放行 | tenantId=null 平台视图跳过租户过滤
数据列表只按 `tenantId` 过滤 | 节点管理/升级/导出：`tenantId OR defaultTenantId`（含池节点）| 创建资源自动设 `tenantId`，平台视图归默认租户

## 应用监控要点
进程发现：`jps -lm`+`ps aux|grep java`，不可用回退 ps | 状态一致：先进程后健康
WS 只推实时指标，状态走 HTTP/DB | 前端 `mergeDashboard` 保留 WS 实时值 | Docker jar 更新：`docker exec cp /app/agent.jar /app/data/agent.jar && restart`
日志：中文、补异常栈、高频 DEBUG/关键 INFO/可恢复 WARN/严重 ERROR

## JVM 诊断功能（2026-08-29）
一键体检/内存分析/线程分析/火焰图/方法追踪/一键诊断（jmap+profiler 出 TOP 类+调用链 TOP 方法）
生产限制：只能平台操作，禁外部工具（如 MAT），火焰图需 Arthas attach
内存分析：`jmap -histo`+`profiler --event alloc` → TOP10 类(内存MB/KB)+调用链 TOP10(占比)，可展开完整路径
线程：概览+CPU TOP5+阻塞列表 | GC：Young/Full 次数耗时+优化建议
火焰图：事件(CPU/分配/锁/Wall)+采样 5-120s+复现操作，绿色=业务代码，宽度=占比

## 交互规则（重要）
### 部署流程
Agent 改→我打包构建 Docker 重启 | Server 改→重启 Server | 前端改→Vite 热重载自动生效
每次改动后告知：改了哪个服务(Agent/Server/前端)、改几文件、是否重启、重启哪些

### demo-test-app 管理
运行在 Agent Docker 容器，容器重启后需手动重启：`docker exec -d ops-agent-X java -Xms128m -Xmx256m -jar /app/data/apps/demo-test-app/demo-test-app.jar` | 检查 `docker exec ops-agent-X jps -l | grep demo`

### 日志查看
`docker logs ops-agent-X --tail 100` | 搜索 `docker logs ops-agent-X | grep "关键词"` | 心跳 `grep -i "heartbeat"`

### 功能开发原则
核心是"定位问题"非"展示数据" | 数据必须有意义，不展示原始 JSON | 同时展示类级+方法级 | 完整调用链（业务代码→JVM 内部）| 内存用 MB/KB 非采样数 | 提供优化建议

## 人工补充
文档写 docs 目录，按日期/需求建文件夹，简单清晰 | e2e 测试看 e2e 目录
监控区分：监控部署的应用 vs agent 自身监控，别混淆 | ws 消息发送时明确谁监控谁
bug 一次没改好，优先补日志快速定位
