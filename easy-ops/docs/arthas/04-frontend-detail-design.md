# Arthas 集成 — 前端详细设计

> 版本：v1.0 | 日期：2026-08-28 | 关联：01-architecture-design.md

---

## 1. 目录结构

```
frontend/src/
├── api/
│   └── arthas.ts                              # API 封装（新增）
├── types/
│   └── arthas.ts                              # 类型定义（新增）
├── views/
│   └── AppMonitorView.vue                     # 修改：增加「JVM诊断」按钮
└── components/
    └── arthas/                                # 新增目录
        ├── ArthasDiagnoseDrawer.vue           # 诊断面板主组件（右侧抽屉）
        ├── ArthasStatusBar.vue                # 顶部状态栏
        ├── FlameGraph.vue                      # 火焰图 SVG 渲染组件
        └── tabs/
            ├── OverviewTab.vue                 # 概览 Tab
            ├── MemoryTab.vue                   # 内存分析 Tab
            ├── ThreadTab.vue                   # 线程分析 Tab
            ├── FlameGraphTab.vue               # 火焰图 Tab
            ├── TraceTab.vue                    # 方法追踪 Tab
            ├── TerminalTab.vue                 # 终端 Tab
            └── HistoryTab.vue                  # 历史记录 Tab
```

---

## 2. 类型定义（types/arthas.ts）

```typescript
// 诊断会话
export interface ArthasSession {
  sessionId: string
  pid: number
  projectId: number
  nodeId: number
  status: 'ATTACHED' | 'DETACHED' | 'FAILED'
  arthasVersion: string
  attachTime: number
}

// 命令执行结果
export interface ArthasCommandResult {
  state: 'SUCCEEDED' | 'FAILED'
  results: ArthasResultItem[]
  commandType: string
  durationMs: number
}

export interface ArthasResultItem {
  type: string
  jobId: number
  [key: string]: any
}

// dashboard 数据
export interface DashboardData {
  thread: {
    total: number
    runnable: number
    timedWaiting: number
    waiting: number
    blocked: number
    deadlock: number
  }
  memory: {
    heapUsed: number
    heapMax: number
    oldGenUsed: number
    oldGenMax: number
    edenUsed: number
    edenMax: number
    survivorUsed: number
    survivorMax: number
    metaspaceUsed: number
    metaspaceMax: number
  }
  gc: {
    youngCount: number
    youngTimeMs: number
    fullCount: number
    fullTimeMs: number
  }
  runtime: {
    osName: string
    javaVersion: string
    processCpuPercent: number
    systemCpuPercent: number
    uptime: number
  }
}

// memory 命令数据
export interface MemoryData {
  heap: MemoryRegion
  nonHeap: MemoryRegion
  [key: string]: MemoryRegion
}

export interface MemoryRegion {
  used: number
  max: number
  committed: number
  init: number
}

// 线程数据
export interface ThreadData {
  id: number
  name: string
  state: string
  cpuPercent: number
  deltaTime: number
  stackTrace: StackTraceElement[]
}

export interface StackTraceElement {
  className: string
  methodName: string
  fileName: string
  lineNumber: number
  nativeMethod: boolean
}

// profiler 任务
export interface ProfilerTask {
  taskId: string
  status: 'SAMPLING' | 'COMPLETED' | 'FAILED'
  event: 'cpu' | 'alloc' | 'lock' | 'wall'
  duration: number
  startTime: number
  flamegraphUrl?: string
  topMethods?: TopMethod[]
}

export interface TopMethod {
  method: string
  percent: number
  samples: number
}

// heapdump 信息
export interface HeapdumpInfo {
  status: 'GENERATED' | 'FAILED'
  fileName: string
  fileSizeMb: number
  downloadUrl: string
}

// 诊断历史记录
export interface DiagnoseRecord {
  id: number
  sessionId: string
  projectId: number
  nodeId: number
  pid: number
  jarName: string
  status: string
  triggerBy: string
  arthasVersion: string
  startTime: number
  endTime?: number
  durationMs?: number
  summary?: DiagnoseSummary
}

export interface DiagnoseSummary {
  heapUsedMb: number
  heapMaxMb: number
  oldGenUsedPercent: number
  fgcCount: number
  threadCount: number
  deadlockCount: number
  topMemoryClass?: string
  topMemoryClassInstances?: number
  commandCount: number
}

// 诊断详情
export interface DiagnoseDetail {
  record: DiagnoseRecord
  results: DiagnoseResultItem[]
  flamegraphs: { taskId: string; event: string; fileUrl: string }[]
}

export interface DiagnoseResultItem {
  id: number
  command: string
  commandType: string
  resultJson?: string
  resultFile?: string
  execTime: number
  durationMs: number
  success: boolean
  errorMsg?: string
}
```

---

## 3. API 封装（api/arthas.ts）

```typescript
import request from '@/utils/request' // 复用现有请求封装

// 启动诊断会话
export function startDiagnose(data: { projectId: number; nodeId: number; pid: number }) {
  return request.post('/arthas/diagnose/start', data)
}

// 结束诊断会话
export function stopDiagnose(data: { sessionId: string }) {
  return request.post('/arthas/diagnose/stop', data)
}

// 查询会话状态
export function getDiagnoseStatus(sessionId: string) {
  return request.get('/arthas/diagnose/status', { params: { sessionId } })
}

// 执行命令
export function execCommand(data: { sessionId: string; command: string; timeoutMs?: number }) {
  return request.post('/arthas/diagnose/exec', data)
}

// 启动 profiler
export function startProfiler(data: { sessionId: string; event: string; duration: number }) {
  return request.post('/arthas/diagnose/profiler/start', data)
}

// 停止 profiler
export function stopProfiler(data: { sessionId: string; taskId: string }) {
  return request.post('/arthas/diagnose/profiler/stop', data)
}

// 触发 heapdump
export function triggerHeapdump(sessionId: string, live = true) {
  return request.get('/arthas/diagnose/heapdump', { params: { sessionId, live } })
}

// 诊断历史列表
export function getDiagnoseHistory(params: { projectId: number; page: number; pageSize: number }) {
  return request.get('/arthas/diagnose/history', { params })
}

// 诊断详情
export function getDiagnoseDetail(id: number) {
  return request.get('/arthas/diagnose/detail', { params: { id } })
}

// 火焰图 URL
export function getFlamegraphUrl(recordId: number, taskId: string) {
  return `/api/arthas/diagnose/flamegraph?recordId=${recordId}&taskId=${taskId}`
}
```

---

## 4. 入口设计（AppMonitorView.vue 修改）

### 4.1 表格操作列增加按钮

在应用监控列表的表格操作列，对 `processStatus === 'RUNNING'` 且有 `processPid` 的行，增加「JVM诊断」按钮。

```vue
<template #bodyCell="{ column, record }">
  <template v-if="column.key === 'action'">
    <a-space>
      <!-- 现有按钮：启动/停止/重启/详情 -->
      ...
      <!-- 新增：JVM诊断按钮 -->
      <a-button
        v-if="record.processStatus === 'RUNNING' && record.processPid > 0"
        type="link"
        size="small"
        @click="openArthasDiagnose(record)"
      >
        <bug-outlined /> JVM诊断
      </a-button>
    </a-space>
  </template>
</template>
```

### 4.2 打开诊断面板

```typescript
const arthasDrawerVisible = ref(false)
const currentDiagnoseTarget = ref<{ projectId: number; nodeId: number; pid: number; projectName: string; nodeName: string } | null>(null)

function openArthasDiagnose(record: any) {
  currentDiagnoseTarget.value = {
    projectId: record.projectId,
    nodeId: record.nodeId,
    pid: record.processPid,
    projectName: record.projectName,
    nodeName: record.nodeName
  }
  arthasDrawerVisible.value = true
}
```

### 4.3 引入诊断抽屉

```vue
<ArthasDiagnoseDrawer
  v-model:visible="arthasDrawerVisible"
  :target="currentDiagnoseTarget"
/>
```

---

## 5. ArthasDiagnoseDrawer（诊断面板主组件）

### 5.1 布局

```
┌─────────────────────────────────────────────────────────┐
│ 🔍 JVM 诊断 — order-service (node-01)  PID: 12345  [×] │
├─────────────────────────────────────────────────────────┤
│ ArthasStatusBar                                           │
│ ● 已连接  v3.7.6  运行 02:35  [一键体检] [生成报告] [结束]│
├─────────────────────────────────────────────────────────┤
│ [实时诊断] [历史记录]                                     │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Tab 内容区（OverviewTab / MemoryTab / ...）            │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 5.2 核心状态

```typescript
const activeTab = ref<'realtime' | 'history'>('realtime')
const session = ref<ArthasSession | null>(null)
const attaching = ref(false)
const elapsedSeconds = ref(0)
let elapsedTimer: number | null = null
```

### 5.3 生命周期

```
onMounted:
  1. 调用 startDiagnose(target)
  2. attach 成功 → 保存 session，启动计时器
  3. attach 失败 → 显示错误，允许重试

onBeforeUnmount / 关闭抽屉:
  1. 如果会话存在且用户选择"结束诊断"→ 调用 stopDiagnose
  2. 如果用户只是关闭抽屉 → 提示"会话将在后台继续，10分钟无活动自动结束"
  3. 清除计时器
```

### 5.4 抽屉配置

```vue
<a-drawer
  v-model:visible="visible"
  title="JVM 诊断"
  placement="right"
  :width="900"
  :mask="false"
  :destroy-on-close="true"
>
  <!-- 内容 -->
</a-drawer>
```

宽度 900px，足够展示火焰图和表格。`:mask="false"` 不遮罩背景，方便同时看监控列表。

---

## 6. ArthasStatusBar（顶部状态栏）

### 6.1 展示内容

| 元素 | 说明 |
|------|------|
| 连接状态指示灯 | 绿色=已连接，红色=未连接，黄色=连接中 |
| Arthas 版本 | v3.7.6 |
| 运行时长 | 02:35（mm:ss，每秒更新） |
| 一键体检按钮 | 自动执行 dashboard + memory + thread -n 5 |
| 生成报告按钮 | 保存当前诊断快照 |
| 结束诊断按钮 | detach 并关闭面板 |

### 6.2 一键体检逻辑

```typescript
async function quickCheckup() {
  // 并行执行三个命令
  const [dashboard, memory, thread] = await Promise.all([
    execCommand({ sessionId: session.value.sessionId, command: 'dashboard -n 1' }),
    execCommand({ sessionId: session.value.sessionId, command: 'memory' }),
    execCommand({ sessionId: session.value.sessionId, command: 'thread -n 5' })
  ])
  // 解析结果，自动标红异常项
  // 老年代 >80% → 红色告警
  // FGC 频率 >1次/分钟 → 黄色告警
  // 死锁数 >0 → 红色告警
}
```

---

## 7. OverviewTab（概览 Tab）

### 7.1 布局

```
┌─────────────────────────────────────────────────┐
│ [采集概览]  最后更新: 2026-08-28 15:30:00       │
├─────────────────────────────────────────────────┤
│ 线程统计（4个统计卡片）                            │
│ 总线程  RUNNABLE  WAITING  BLOCKED/DEADLOCK      │
├─────────────────────────────────────────────────┤
│ 内存使用（进度条列表）                             │
│ 堆:       ████████████░░░░ 850/1024 MB (83%)   │
│ 老年代:   ██████████████░░ 720/768 MB (94%) 🔴  │
│ Eden:     █████░░░░░░░░░░░ 100/256 MB (39%)     │
│ Survivor: ██████░░░░░░░░░░ 30/64 MB (47%)       │
│ 元空间:   ██████░░░░░░░░░░ 120/256 MB (47%)     │
├─────────────────────────────────────────────────┤
│ GC 统计（4个统计卡片）                            │
│ YGC次数  YGC耗时  FGC次数  FGC耗时                │
├─────────────────────────────────────────────────┤
│ 系统信息（列表）                                  │
│ OS / Java版本 / 进程CPU / 系统CPU / 运行时长      │
├─────────────────────────────────────────────────┤
│ 异常告警（自动标红）                              │
│ ⚠️ 老年代使用率 94%，建议检查内存泄漏             │
│ ⚠️ FGC 频率过高（每分钟3次）                      │
└─────────────────────────────────────────────────┘
```

### 7.2 数据来源
- `dashboard -n 1` 命令（取一次快照，不持续刷新）
- `memory` 命令
- 异常告警由前端根据阈值自动判断

---

## 8. MemoryTab（内存分析 Tab）

### 8.1 布局

```
┌─────────────────────────────────────────────────┐
│ [采集内存快照] [对象直方图] [生成Heapdump] [强制GC对比] │
├─────────────────────────────────────────────────┤
│ 内存区域明细表                                    │
│ 区域 | 已用(MB) | 最大(MB) | 使用率 | 趋势      │
├─────────────────────────────────────────────────┤
│ Top 占用类列表（来自 sc -d 或 profiler alloc）   │
│ 排名 | 类名 | 实例数 | 总大小(MB) | 占比 | 操作  │
│                                              [查看实例][trace创建] │
├─────────────────────────────────────────────────┤
│ Heapdump 区域                                    │
│ 文件名 | 大小 | 生成时间 | [下载] [用MAT分析指引]  │
├─────────────────────────────────────────────────┤
│ GC 对比区域（强制GC前后对比）                     │
│ GC前堆使用: 850MB → GC后: 820MB（仅释放30MB，疑似泄漏）│
└─────────────────────────────────────────────────┘
```

### 8.2 核心交互

**对象直方图**：
- 执行 `sc -d *` 获取所有类的详细信息
- 或执行 `vmtool --action getInstances --className {类名} --limit 10` 查看具体实例
- 表格支持按实例数/大小排序

**trace 创建**：
- 点击 Top 类的「trace创建」按钮
- 自动跳转到 TraceTab，预填类名和方法名
- 执行 `trace {类名} *` 追踪该类所有方法的调用链

**Heapdump**：
- 点击「生成Heapdump」→ 调用 `heapdump --live {路径}`
- 显示生成进度（大文件可能需要几秒到几十秒）
- 生成后提供下载链接
- 提示「建议使用 Eclipse MAT 或 JProfiler 分析 .hprof 文件」

**强制 GC 对比**：
- 先采集一次 memory（GC前）
- 执行 `vmtool --action forceGc`
- 等待 2 秒后再采集一次 memory（GC后）
- 对比显示各区域变化，释放少说明可能有内存泄漏

---

## 9. ThreadTab（线程分析 Tab）

### 9.1 布局

```
┌─────────────────────────────────────────────────┐
│ [CPU Top 5] [死锁检测] [全部线程]                 │
├─────────────────────────────────────────────────┤
│ 线程状态饼图（ECharts）                           │
│ RUNNABLE / WAITING / TIMED_WAITING / BLOCKED     │
├─────────────────────────────────────────────────┤
│ CPU 热点线程表                                    │
│ 排名 | 线程名 | 状态 | CPU% | 堆栈摘要 | [展开]   │
├─────────────────────────────────────────────────┤
│ 死锁检测结果                                      │
│ ✅ 未检测到死锁  /  ❌ 检测到死锁（详情展开）      │
├─────────────────────────────────────────────────┤
│ 线程堆栈详情（可折叠）                             │
│ "http-nio-8080-exec-12" cpu=45%                  │
│   at com.example.OrderService.process(OrderService.java:128) │
│   at com.example.OrderController.create(OrderController.java:45) │
│   ...                                              │
└─────────────────────────────────────────────────┘
```

### 9.2 核心交互

- `thread -n 5`：CPU 最高的 5 个线程
- `thread -b`：一键检测死锁
- `thread`：全部线程列表和状态统计
- 点击线程行展开完整堆栈，支持复制

---

## 10. FlameGraphTab（火焰图 Tab）

### 10.1 布局

```
┌─────────────────────────────────────────────────┐
│ 事件类型: ( ) CPU (●) 内存分配 ( ) 锁竞争 ( ) 墙钟 │
│ 采样时长: [30s▼]  [开始采样]                     │
├─────────────────────────────────────────────────┤
│ 采样状态: 🔴 采样中... 剩余 25s  [停止并生成]     │
├─────────────────────────────────────────────────┤
│ 火焰图 SVG（FlameGraph 组件渲染）                 │
│ ┌─────────────────────────────────────────────┐ │
│ │             火焰图可视化区域                   │ │
│ │  (鼠标悬停显示方法名和占比，点击缩放)          │ │
│ └─────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────┤
│ Top 热点方法表                                    │
│ 排名 | 方法 | 占比 | 类型                         │
│ 1 | com.example.OrderService.process | 38.5% | 内存分配 │
└─────────────────────────────────────────────────┘
```

### 10.2 FlameGraph 组件

- 接收 SVG 字符串或 URL，渲染为可交互火焰图
- 鼠标悬停：显示方法名、采样数、占比 tooltip
- 点击：缩放进入该方法的子调用
- 双击空白：恢复全量视图
- 支持搜索高亮（输入方法名高亮匹配的块）

### 10.3 采样流程

```
开始采样:
  1. 选择事件类型（cpu/alloc/lock/wall）
  2. 选择时长（30/60/120/自定义）
  3. 调用 startProfiler API
  4. 显示倒计时进度条
  5. 时间到自动调用 stopProfiler，或用户手动点击停止
  6. 获取火焰图 SVG 和 Top 方法列表
  7. 渲染火焰图
```

---

## 11. TraceTab（方法追踪 Tab）

### 11.1 布局

```
┌─────────────────────────────────────────────────┐
│ 类名: [*OrderService________]  方法: [*process*____] │
│ 命令: (●) trace调用链 ( ) watch入参/返回 ( ) monitor统计 ( ) stack调用栈 │
│ 执行次数: [5▼]  [执行] [重置全部增强]              │
├─────────────────────────────────────────────────┤
│ 已增强的类/方法列表                               │
│ com.example.OrderService.* [reset]               │
├─────────────────────────────────────────────────┤
│ trace 结果（调用树）                               │
│ `--- OrderService.process() [850ms]               │
│     +--- CacheManager.get() [50ms, 5%]           │
│     +--- OrderMapper.select() [200ms, 23%]       │
│     +--- OrderConverter.convert() [500ms, 59%] 🔴│
│     `--- NotificationService.send() [100ms, 12%] │
├─────────────────────────────────────────────────┤
│ watch 结果（JSON 展示）                           │
│ 入参: [12345]                                     │
│ 返回: { "id": 12345, "status": "CREATED" }       │
│ 异常: null                                         │
└─────────────────────────────────────────────────┘
```

### 11.2 核心交互

- **trace**：`trace {类名} {方法名} -n {次数}`，展示调用树和每步耗时，慢步骤标红
- **watch**：`watch {类名} {方法名} '{params, returnObj, throwExp}' -n {次数} -x 2`，展示入参/返回值/异常
- **monitor**：`monitor {类名} {方法名} -c {周期}`，统计调用次数/成功/失败/平均耗时
- **stack**：`stack {类名} {方法名} -n {次数}`，展示方法被调用的堆栈
- **重置增强**：执行 `reset` 命令，清除所有字节码增强
- 已增强列表实时显示，防止忘记 reset

---

## 12. TerminalTab（终端 Tab）

### 12.1 布局

```
┌─────────────────────────────────────────────────┐
│ ⚠️ 高级模式：命令直接透传到 Arthas，请谨慎操作       │
│ 白名单外命令需要管理员权限                          │
├─────────────────────────────────────────────────┤
│ xterm 终端区域                                    │
│ $ memory                                          │
│ heap                                               │
│   used: 850M, max: 1024M                         │
│ ...                                                │
│ $                                                  │
├─────────────────────────────────────────────────┤
│ 命令历史: [memory] [thread -n 5] [dashboard]     │
└─────────────────────────────────────────────────┘
```

### 12.2 实现

- 复用项目已有的 xterm 组件
- 命令输入后调用 execCommand API
- 输出 Arthas 返回的文本结果
- 支持命令历史（上下键）
- 支持 Tab 补全（调用 Agent shell/complete 或内置 Arthas 命令列表）

---

## 13. HistoryTab（历史记录 Tab）

### 13.1 布局

```
┌─────────────────────────────────────────────────┐
│ 筛选: 时间范围 [最近7天▼]  状态 [全部▼]  [查询]    │
├─────────────────────────────────────────────────┤
│ 诊断历史列表                                      │
│ 时间 | 项目 | 节点 | PID | 状态 | 时长 | 操作     │
│ 15:30 | order-svc | node-01 | 12345 | 已完成 | 5m | [查看] │
│ 14:20 | order-svc | node-02 | 23456 | 已完成 | 12m| [查看] │
├─────────────────────────────────────────────────┤
│ 分页: [1] 2 3 ... 上一页 下一页                   │
└─────────────────────────────────────────────────┘
```

### 13.2 查看历史详情

点击「查看」→ 展示该次诊断的完整结果：
- 诊断摘要（关键指标快照）
- 已执行的命令列表和结果
- 火焰图（如果有）
- 支持导出为 JSON 报告

---

## 14. 路由和菜单

**不新增路由，不新增菜单项。**

- 入口：应用监控列表 → 每行「JVM诊断」按钮
- 展示：右侧抽屉（Drawer），不跳转页面
- 优势：上下文清晰，不增加菜单复杂度，用户不用记新入口

---

## 15. 状态管理

使用组件内 `ref` + `reactive`，不引入 Pinia store（诊断是临时会话，不需要全局状态）。

如果需要跨组件共享（如状态栏显示运行时长），通过 props + emit 或 provide/inject 传递。

---

## 16. 错误处理

| 场景 | 处理 |
|------|------|
| attach 失败 | 显示错误原因 + 「重试」按钮 |
| 命令执行超时 | 显示「命令超时，已中断」+ 建议缩短超时或简化命令 |
| 会话已结束 | 提示「诊断会话已结束（10分钟无活动自动结束）」+ 「重新开始」按钮 |
| Agent 不可达 | 提示「Agent 不可达，请检查节点状态」 |
| 命令不在白名单 | 提示「命令被禁用，如需使用请联系管理员」 |
| 火焰图生成失败 | 显示错误 + 建议降低采样时长或换事件类型 |

---

## 17. 性能优化

- 命令结果缓存：相同命令短时间内不重复执行
- 大结果分页：命令结果超过 1000 行时分页展示
- 火焰图懒加载：切换到 FlameGraphTab 时才加载 SVG
- 防抖：快速切换 Tab 时取消未完成的请求
- 虚拟滚动：线程列表/历史列表超过 100 条时使用虚拟滚动

---

## 18. 与现有组件的复用

| 现有组件/库 | 复用方式 |
|------------|---------|
| Ant Design Vue | Drawer、Table、Tabs、Statistic、Progress、Tag、Button |
| ECharts | 线程状态饼图、内存趋势图 |
| xterm.js | 终端 Tab |
| request 封装 | API 调用 |
| AppMonitorView.vue | 增加诊断入口按钮 |
| 时间格式化工具 | 展示时间戳 |
