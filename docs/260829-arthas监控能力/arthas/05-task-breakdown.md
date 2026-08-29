# Arthas 集成 — 详细任务清单

> 版本：v1.0 | 日期：2026-08-28 | 关联：01-architecture-design.md
> 总预估工时：15-20 人天 | 阶段数：4 | 任务数：42

---

## 阶段一：基础设施与 MVP（3-5 人天）

**目标**：能 attach、能执行基础命令、能看到 dashboard 和 memory

### Task 1.1：项目脚手架与配置
- **描述**：创建 Agent/Server 侧 arthas 包结构，新增配置项
- **依赖**：无
- **预估**：0.5 天
- **验收标准**：
  - [ ] Agent 侧 `com.ops.agent.arthas` 包创建
  - [ ] Server 侧 `com.ops.server.arthas` 包创建
  - [ ] application.yml 新增 arthas 配置段
  - [ ] 项目可正常编译启动

### Task 1.2：Arthas 完整包内置与释放
- **描述**：下载 Arthas 3.7.6 完整包，内置到 Agent jar，启动时自动释放
- **依赖**：Task 1.1
- **预估**：0.5 天
- **验收标准**：
  - [ ] pom.xml 新增 maven-dependency-plugin 下载 Arthas
  - [ ] Arthas 包打入 agent.jar 的 classpath:/arthas/
  - [ ] Agent 启动时检查并释放到 {agentDataPath}/arthas/
  - [ ] 版本校验：不一致则重新释放
  - [ ] async-profiler 原生库可执行权限设置

### Task 1.3：ArthasPortAllocator 端口分配器
- **描述**：实现随机端口分配与可用性检测
- **依赖**：Task 1.1
- **预估**：0.5 天
- **验收标准**：
  - [ ] 30000-60000 范围随机分配
  - [ ] 端口可用性检测（Socket 连接测试）
  - [ ] 已分配端口追踪与释放
  - [ ] 50 次尝试失败抛异常
  - [ ] 单元测试覆盖

### Task 1.4：ArthasHttpClient HTTP API 客户端
- **描述**：封装对 Arthas HTTP API 的调用，支持 exec/async_exec/pull/interrupt
- **依赖**：Task 1.1
- **预估**：0.5 天
- **验收标准**：
  - [ ] exec 同步执行命令，解析返回 JSON
  - [ ] init_session / join_session 会话管理
  - [ ] async_exec 异步执行
  - [ ] pull_results 长轮询拉取结果
  - [ ] interrupt_job 中断命令
  - [ ] 错误处理：连接失败、超时、命令失败
  - [ ] 单元测试：mock HTTP 响应

### Task 1.5：ArthasBootstrap 启动器
- **描述**：构建启动命令，启动 arthas-boot.jar，等待 HTTP API 就绪
- **依赖**：Task 1.2, Task 1.3
- **预估**：0.5 天
- **验收标准**：
  - [ ] 构建正确的启动命令（--attach-only --target-pid --http-port --http-ip 127.0.0.1）
  - [ ] 启动进程并管理 Process 对象
  - [ ] 就绪检测：轮询 version 命令，最多 15 秒
  - [ ] 启动失败回滚：kill 进程 + 释放端口
  - [ ] 日志记录启动过程

### Task 1.6：ArthasSessionManager 会话管理器
- **描述**：核心会话管理，attach/detach 生命周期，超时清理，并发控制
- **依赖**：Task 1.3, Task 1.4, Task 1.5
- **预估**：1 天
- **验收标准**：
  - [ ] ConcurrentHashMap<Long, ArthasSession> 管理会话
  - [ ] attach 流程：PID 存活检查 → 端口分配 → 启动 → 就绪 → 存会话
  - [ ] detach 流程：stop 命令 → 等待退出 → 强制 kill → 释放端口
  - [ ] 并发控制：Semaphore 最多 2 个同时诊断
  - [ ] 超时清理：@Scheduled 每 30 秒检查，10 分钟无活动自动 detach
  - [ ] 残留清理：@PostConstruct 扫描残留进程和端口
  - [ ] 全量 detach：@PreDestroy 关闭时清理
  - [ ] 单元测试：attach/detach 流程 mock

### Task 1.7：ArthasController（Agent 侧）
- **描述**：Agent 侧 REST 接口，attach/detach/exec/status
- **依赖**：Task 1.6
- **预估**：0.5 天
- **验收标准**：
  - [ ] POST /api/arthas/attach
  - [ ] POST /api/arthas/detach
  - [ ] POST /api/arthas/exec（含白名单校验）
  - [ ] GET /api/arthas/status
  - [ ] 参数校验和异常处理
  - [ ] 统一 Result<T> 返回
  - [ ] Postman 测试通过

### Task 1.8：Server 侧数据模型与建表
- **描述**：创建诊断记录和结果的 Model、Mapper、XML、建表 SQL
- **依赖**：Task 1.1
- **预估**：0.5 天
- **验收标准**：
  - [ ] ArthasDiagnoseRecordModel 实体
  - [ ] ArthasDiagnoseResultModel 实体
  - [ ] 两个 Mapper 接口和 XML
  - [ ] 建表 SQL 加入 schema（或独立 arthas-schema.sql）
  - [ ] 索引创建（project_id, node_id, start_time, tenant_id）
  - [ ] 启动时自动建表验证

### Task 1.9：ArthasAgentProxy（Server 侧 Agent 代理）
- **描述**：封装 AgentClient，调用 Agent 侧 Arthas 接口
- **依赖**：Task 1.7, Task 1.8
- **预估**：0.5 天
- **验收标准**：
  - [ ] attach / detach / exec / getStatus 方法
  - [ ] 节点 IP/端口获取
  - [ ] 超时控制
  - [ ] 异常封装
  - [ ] 单元测试 mock

### Task 1.10：ArthasDiagnoseService（Server 侧核心服务）
- **描述**：诊断会话生命周期管理，命令透传与结果持久化
- **依赖**：Task 1.8, Task 1.9
- **预估**：1 天
- **验收标准**：
  - [ ] startDiagnose：权限校验 → 创建记录 → attach → 更新记录
  - [ ] stopDiagnose：detach → 生成摘要 → 更新记录
  - [ ] execCommand：白名单校验 → 调用 Agent → 持久化结果 → 返回
  - [ ] getStatus：查询会话状态
  - [ ] 结果大小判断：<100KB 存 JSON，≥100KB 存文件
  - [ ] 租户隔离
  - [ ] 异常处理和回滚

### Task 1.11：ArthasDiagnoseController（Server 侧）
- **描述**：Server 侧 REST 接口，start/stop/status/exec
- **依赖**：Task 1.10
- **预估**：0.5 天
- **验收标准**：
  - [ ] POST /api/arthas/diagnose/start
  - [ ] POST /api/arthas/diagnose/stop
  - [ ] GET /api/arthas/diagnose/status
  - [ ] POST /api/arthas/diagnose/exec
  - [ ] 权限校验（SecurityContext）
  - [ ] 统一异常处理
  - [ ] Postman 测试通过

### Task 1.12：前端 API 封装与类型定义
- **描述**：创建 api/arthas.ts 和 types/arthas.ts
- **依赖**：Task 1.11
- **预估**：0.5 天
- **验收标准**：
  - [ ] 所有 API 方法封装
  - [ ] TypeScript 类型定义完整
  - [ ] 复用现有 request 封装

### Task 1.13：前端诊断入口按钮
- **描述**：AppMonitorView.vue 增加「JVM诊断」按钮
- **依赖**：Task 1.12
- **预估**：0.5 天
- **验收标准**：
  - [ ] 表格操作列增加按钮
  - [ ] 仅 RUNNING 且有 PID 的行显示
  - [ ] 点击打开诊断抽屉
  - [ ] 传递 projectId/nodeId/pid/projectName/nodeName

### Task 1.14：前端诊断抽屉与概览 Tab
- **描述**：ArthasDiagnoseDrawer + OverviewTab，attach 流程 + dashboard/memory 展示
- **依赖**：Task 1.13
- **预估**：1 天
- **验收标准**：
  - [ ] 右侧抽屉（900px 宽，无遮罩）
  - [ ] 打开自动 attach，显示连接中状态
  - [ ] attach 成功显示状态栏（版本、运行时长）
  - [ ] attach 失败显示错误 + 重试
  - [ ] OverviewTab：采集 dashboard + memory
  - [ ] 线程统计卡片
  - [ ] 内存进度条列表
  - [ ] GC 统计卡片
  - [ ] 异常自动标红
  - [ ] 关闭抽屉提示（后台继续/结束诊断）

### Task 1.15：MVP 端到端测试
- **描述**：启动一个测试 Java 进程，完整测试 attach → 执行命令 → 查看结果 → detach
- **依赖**：Task 1.14
- **预估**：0.5 天
- **验收标准**：
  - [ ] 启动 demo-test-app（项目已有）
  - [ ] 页面点击 JVM诊断，attach 成功
  - [ ] 执行 dashboard，数据正确展示
  - [ ] 执行 memory，数据正确展示
  - [ ] 结束诊断，detach 成功
  - [ ] 10 分钟无活动自动 detach 验证
  - [ ] 日志无异常

---

## 阶段二：核心诊断能力（5-7 人天）

**目标**：能定位 FullGC 根因（内存分析 + 线程分析 + 火焰图）

### Task 2.1：MemoryTab 内存分析 Tab
- **描述**：内存区域明细 + 对象直方图 + heapdump + 强制GC对比
- **依赖**：Task 1.15
- **预估**：1 天
- **验收标准**：
  - [ ] 内存区域明细表（堆/老年代/Eden/Survivor/元空间）
  - [ ] 采集内存快照按钮
  - [ ] 对象直方图：执行 sc -d，展示类名/实例数/大小
  - [ ] Top 占用类排序
  - [ ] 生成 Heapdump 按钮 + 进度 + 下载链接
  - [ ] 强制 GC 对比：GC 前 → forceGc → GC 后 → 对比展示
  - [ ] heapdump 文件安全校验（路径穿越防护）

### Task 2.2：Agent 侧 heapdump 下载接口
- **描述**：流式下载 heapdump 文件，安全校验
- **依赖**：Task 1.7
- **预估**：0.5 天
- **验收标准**：
  - [ ] GET /api/arthas/heapdump/download
  - [ ] 文件名安全校验（禁止 ../）
  - [ ] 流式输出，不占内存
  - [ ] Content-Disposition 正确设置
  - [ ] 文件不存在返回 404

### Task 2.3：Server 侧 heapdump 代理
- **描述**：Server 代理下载 Agent 上的 heapdump 文件
- **依赖**：Task 2.2
- **预估**：0.5 天
- **验收标准**：
  - [ ] GET /api/arthas/diagnose/heapdump/download
  - [ ] 流式代理，不缓存到 Server 磁盘
  - [ ] 权限校验
  - [ ] 大文件下载测试（512MB+）

### Task 2.4：ThreadTab 线程分析 Tab
- **描述**：CPU 热点线程 + 死锁检测 + 线程堆栈 + 状态饼图
- **依赖**：Task 1.15
- **预估**：1 天
- **验收标准**：
  - [ ] CPU Top 5：执行 thread -n 5，展示线程名/状态/CPU%/堆栈摘要
  - [ ] 死锁检测：执行 thread -b，有死锁红色告警
  - [ ] 全部线程：执行 thread，状态统计 + 列表
  - [ ] 线程状态饼图（ECharts）
  - [ ] 点击线程展开完整堆栈
  - [ ] 堆栈可复制

### Task 2.5：Agent 侧 async_exec / pull_results 支持
- **描述**：支持异步命令执行（profiler 需要分步执行）
- **依赖**：Task 1.4, Task 1.6
- **预估**：0.5 天
- **验收标准**：
  - [ ] POST /api/arthas/async-exec
  - [ ] GET /api/arthas/pull
  - [ ] POST /api/arthas/interrupt
  - [ ] 会话内 consumerId 管理
  - [ ] 结果拉取超时处理

### Task 2.6：Server 侧 ArthasProfilerTaskManager
- **描述**：profiler 异步采样任务管理，定时自动停止
- **依赖**：Task 2.5, Task 1.10
- **预估**：1 天
- **验收标准**：
  - [ ] startProfiler：调用 Agent 执行 profiler start
  - [ ] ScheduledExecutorService 定时自动停止
  - [ ] stopProfiler：执行 profiler stop --format svg，下载 SVG
  - [ ] SVG 文件存储到 {serverDataPath}/arthas/{recordId}/
  - [ ] Top 方法列表解析
  - [ ] 任务状态管理（SAMPLING/COMPLETED/FAILED）
  - [ ] 任务超时和异常处理

### Task 2.7：Server 侧 profiler API 与火焰图文件服务
- **描述**：profiler start/stop API，火焰图 SVG 读取服务
- **依赖**：Task 2.6
- **预估**：0.5 天
- **验收标准**：
  - [ ] POST /api/arthas/diagnose/profiler/start
  - [ ] POST /api/arthas/diagnose/profiler/stop
  - [ ] GET /api/arthas/diagnose/flamegraph（返回 SVG）
  - [ ] Content-Type: image/svg+xml
  - [ ] 文件不存在返回 404

### Task 2.8：前端 FlameGraph 火焰图组件
- **描述**：SVG 火焰图渲染组件，支持悬停/点击缩放/搜索
- **依赖**：Task 2.7
- **预估**：1 天
- **验收标准**：
  - [ ] 接收 SVG 字符串或 URL，渲染
  - [ ] 鼠标悬停 tooltip（方法名/采样数/占比）
  - [ ] 点击方法块缩放进入子调用
  - [ ] 双击空白恢复全量
  - [ ] 搜索框高亮匹配方法
  - [ ] 颜色方案（暖色/冷色）
  - [ ] 大 SVG 性能优化（>10000 节点）

### Task 2.9：前端 FlameGraphTab 火焰图 Tab
- **描述**：事件类型选择 + 采样时长 + 采样进度 + 火焰图展示 + Top方法表
- **依赖**：Task 2.8
- **预估**：1 天
- **验收标准**：
  - [ ] 事件类型选择（cpu/alloc/lock/wall）
  - [ ] 采样时长选择（30/60/120/自定义）
  - [ ] 开始采样按钮
  - [ ] 采样中倒计时进度条
  - [ ] 手动停止按钮
  - [ ] 火焰图渲染
  - [ ] Top 热点方法表
  - [ ] alloc 事件说明（"内存分配热点，定位谁在疯狂 new 对象"）
  - [ ] 采样中 CPU 开销提示

### Task 2.10：一键体检功能
- **描述**：自动执行 dashboard + memory + thread -n 5，生成体检报告
- **依赖**：Task 2.1, Task 2.4
- **预估**：0.5 天
- **验收标准**：
  - [ ] 状态栏「一键体检」按钮
  - [ ] 并行执行三个命令
  - [ ] 自动分析异常项（老年代>80%、FGC频率高、死锁、CPU热点）
  - [ ] 异常项红色告警展示
  - [ ] 体检结果可保存为诊断摘要

### Task 2.11：核心能力端到端测试
- **描述**：模拟 FullGC 场景，完整测试内存分析 + 线程分析 + 火焰图定位
- **依赖**：Task 2.1 - Task 2.10
- **预估**：0.5 天
- **验收标准**：
  - [ ] 启动测试应用，构造内存泄漏场景（静态 List 不断 add）
  - [ ] 一键体检发现老年代高 + FGC 频繁
  - [ ] 内存分析 Tab 看到 Top 占用类
  - [ ] 火焰图 alloc 采样定位到分配热点方法
  - [ ] 线程分析看到 GC 线程高 CPU
  - [ ] heapdump 生成和下载
  - [ ] 强制 GC 对比验证泄漏
  - [ ] 所有功能无异常

---

## 阶段三：高级功能（3-5 人天）

**目标**：方法级诊断 + 终端 + 诊断历史 + 报告

### Task 3.1：TraceTab 方法追踪 Tab
- **描述**：trace/watch/monitor/stack 四种命令，类名方法名输入，结果展示
- **依赖**：Task 2.5
- **预估**：1.5 天
- **验收标准**：
  - [ ] 类名/方法名输入（支持通配符）
  - [ ] 命令类型切换（trace/watch/monitor/stack）
  - [ ] 执行次数设置
  - [ ] trace 结果：调用树展示，每步耗时，慢步骤标红
  - [ ] watch 结果：入参/返回值/异常 JSON 展示
  - [ ] monitor 结果：调用统计表格
  - [ ] stack 结果：调用堆栈展示
  - [ ] 已增强类/方法列表
  - [ ] reset 全部增强按钮
  - [ ] 从 MemoryTab 跳转预填类名

### Task 3.2：TerminalTab 终端 Tab
- **描述**：xterm 终端，自由输入 Arthas 命令
- **依赖**：Task 1.12
- **预估**：1 天
- **验收标准**：
  - [ ] xterm 组件集成
  - [ ] 命令输入和执行
  - [ ] 结果输出（文本格式）
  - [ ] 命令历史（上下键）
  - [ ] Tab 补全（内置 Arthas 命令列表）
  - [ ] 高级模式警告提示
  - [ ] 白名单外命令拦截

### Task 3.3：诊断历史功能（Server 侧）
- **描述**：历史列表查询、详情查询、结果关联查询
- **依赖**：Task 1.8
- **预估**：0.5 天
- **验收标准**：
  - [ ] GET /api/arthas/diagnose/history（分页+项目筛选+时间范围）
  - [ ] GET /api/arthas/diagnose/detail（记录+结果+火焰图列表）
  - [ ] 租户隔离
  - [ ] 结果 JSON 大小限制处理

### Task 3.4：HistoryTab 历史记录 Tab（前端）
- **描述**：历史列表、筛选、详情回看
- **依赖**：Task 3.3
- **预估**：1 天
- **验收标准**：
  - [ ] 历史列表（时间/项目/节点/PID/状态/时长）
  - [ ] 时间范围筛选
  - [ ] 状态筛选
  - [ ] 分页
  - [ ] 点击查看详情
  - [ ] 详情展示：摘要 + 命令结果列表 + 火焰图
  - [ ] 导出诊断报告（JSON）

### Task 3.5：诊断报告生成
- **描述**：自动生成诊断报告，包含异常指标、Top 类、热点方法、证据链
- **依赖**：Task 3.3
- **预估**：0.5 天
- **验收标准**：
  - [ ] 「生成报告」按钮
  - [ ] 自动汇总已执行的命令结果
  - [ ] 提取关键指标（堆使用率/FGC/Top类/热点方法）
  - [ ] 生成结构化报告 JSON
  - [ ] 报告持久化
  - [ ] 报告可查看和导出

### Task 3.6：ArthasCleanupScheduler 定时清理
- **描述**：清理过期诊断记录、结果、文件
- **依赖**：Task 1.8
- **预估**：0.5 天
- **验收标准**：
  - [ ] @Scheduled 定时任务（每天凌晨3点）
  - [ ] 清理 90 天前的记录和结果
  - [ ] 清理 30 天前的火焰图文件
  - [ ] 清理前记录日志
  - [ ] 可配置保留天数
  - [ ] 清理后 H2 压缩（可选）

### Task 3.7：操作审计日志
- **描述**：诊断操作记录到 operation_log
- **依赖**：Task 1.11
- **预估**：0.5 天
- **验收标准**：
  - [ ] start/stop/exec/profiler/heapdump 均记录
  - [ ] 记录操作人、时间、项目、节点、PID、命令
  - [ ] 操作审计页可查询

### Task 3.8：高级功能端到端测试
- **描述**：测试方法追踪、终端、历史报告
- **依赖**：Task 3.1 - Task 3.7
- **预估**：0.5 天
- **验收标准**：
  - [ ] trace 追踪测试应用的方法，调用树正确
  - [ ] watch 查看入参返回值
  - [ ] 终端执行自由命令
  - [ ] 诊断历史可查询
  - [ ] 报告生成正确
  - [ ] 定时清理触发验证

---

## 阶段四：优化与打磨（2-3 人天）

### Task 4.1：Docker 环境兼容性
- **描述**：Docker Agent 容器中 attach 权限验证，Dockerfile 更新
- **依赖**：Task 1.15
- **预估**：0.5 天
- **验收标准**：
  - [ ] Dockerfile 确认使用 JDK 不是 JRE
  - [ ] --cap-add=SYS_PTRACE 验证
  - [ ] Docker 内 attach 测试
  - [ ] Docker 内 profiler 测试（async-profiler 兼容性）
  - [ ] 文档更新部署说明

### Task 4.2：并发与压力测试
- **描述**：多实例同时诊断，Agent 并发控制验证
- **依赖**：Task 2.11
- **预估**：0.5 天
- **验收标准**：
  - [ ] 同时 attach 2 个进程成功
  - [ ] 第 3 个 attach 返回 429
  - [ ] 一个会话结束后第 3 个可 attach
  - [ ] 频繁 attach/detach 无端口泄漏
  - [ ] Agent 内存占用稳定

### Task 4.3：异常场景测试
- **描述**：各种异常场景的容错验证
- **依赖**：Task 2.11
- **预估**：0.5 天
- **验收标准**：
  - [ ] attach 不存在的 PID → 友好错误
  - [ ] attach 非 Java 进程 → 友好错误
  - [ ] 诊断中目标进程退出 → 自动清理会话
  - [ ] Agent 重启 → 残留清理
  - [ ] 命令执行超时 → 中断并提示
  - [ ] heapdump 磁盘满 → 错误处理
  - [ ] 网络中断 → 重连或错误提示

### Task 4.4：前端性能优化
- **描述**：大结果渲染、懒加载、防抖
- **依赖**：Task 3.8
- **预估**：0.5 天
- **验收标准**：
  - [ ] 命令结果 >1000 行分页
  - [ ] 火焰图 >10000 节点流畅
  - [ ] Tab 切换取消未完成请求
  - [ ] 线程列表虚拟滚动
  - [ ] Lighthouse 性能评分 >80

### Task 4.5：文档与使用手册
- **描述**：用户使用文档、部署文档、FAQ
- **依赖**：Task 4.4
- **预估**：0.5 天
- **验收标准**：
  - [ ] 用户使用手册（如何启动诊断、如何看结果、FullGC排查流程）
  - [ ] 部署文档（Docker 权限、裸机要求）
  - [ ] FAQ（常见问题和解决方案）
  - [ ] 命令白名单说明
  - [ ] 与现有监控的协同说明

### Task 4.6：最终验收测试
- **描述**：全功能回归测试
- **依赖**：Task 4.1 - Task 4.5
- **预估**：0.5 天
- **验收标准**：
  - [ ] 所有 Task 验收标准通过
  - [ ] 完整 FullGC 排查流程演示
  - [ ] 无 P0/P1 缺陷
  - [ ] 代码符合项目规范（单文件≤400行、异常走GlobalExceptionHandler）
  - [ ] 构建通过（mvn package -DskipTests）
  - [ ] 前端构建通过（npm run build）

---

## 任务依赖关系图

```
阶段一（MVP）:
Task 1.1 ─┬─→ Task 1.2 ─→ Task 1.5 ─┐
           ├─→ Task 1.3 ──────────────┤
           └─→ Task 1.4 ──────────────┴─→ Task 1.6 ─→ Task 1.7 ─┐
Task 1.1 ─→ Task 1.8 ────────────────────────────────────────────────┼─→ Task 1.9 ─→ Task 1.10 ─→ Task 1.11
                                                                       │
Task 1.11 ─→ Task 1.12 ─→ Task 1.13 ─→ Task 1.14 ─→ Task 1.15 ◄──┘

阶段二（核心能力）:
Task 1.15 ─┬─→ Task 2.1 ─┐
            ├─→ Task 2.4 ─┤
Task 1.7 ───→ Task 2.2 ─→ Task 2.3 ─┤
Task 1.4+1.6 → Task 2.5 ─→ Task 2.6 ─→ Task 2.7 ─→ Task 2.8 ─→ Task 2.9
Task 2.1+2.4 → Task 2.10
所有 → Task 2.11

阶段三（高级功能）:
Task 2.5 ─→ Task 3.1
Task 1.12 ─→ Task 3.2
Task 1.8 ─→ Task 3.3 ─→ Task 3.4
Task 3.3 ─→ Task 3.5
Task 1.8 ─→ Task 3.6
Task 1.11 ─→ Task 3.7
所有 → Task 3.8

阶段四（优化）:
Task 1.15 ─→ Task 4.1
Task 2.11 ─→ Task 4.2, Task 4.3
Task 3.8 ─→ Task 4.4
Task 4.4 ─→ Task 4.5
所有 → Task 4.6
```

---

## 里程碑

| 里程碑 | 内容 | 预估完成 | 交付物 |
|--------|------|---------|--------|
| M1 | MVP 完成 | 第 5 天 | 可 attach + dashboard/memory 展示 |
| M2 | 核心能力完成 | 第 12 天 | 内存/线程/火焰图可定位 FullGC |
| M3 | 高级功能完成 | 第 17 天 | 方法追踪 + 终端 + 历史报告 |
| M4 | 优化打磨完成 | 第 20 天 | 全功能稳定 + 文档 |

---

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Arthas HTTP API 响应格式与文档不一致 | 中 | 开发前先写 spike 验证实际返回格式 |
| async-profiler 在某些内核不工作 | 中 | 提供 itimer 备选方案；Docker 验证 |
| 火焰图 SVG 解析复杂 | 低 | 直接用 Arthas 返回的 SVG，不做二次解析 |
| 大 heapdump 下载超时 | 中 | 流式下载 + 分片 + 超时配置调大 |
| 前端 xterm 与 Arthas 输出格式兼容 | 低 | 先做纯文本输出，后续优化着色 |
