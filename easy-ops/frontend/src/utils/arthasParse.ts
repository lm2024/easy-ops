/**
 * Arthas 4.x 命令结果解析工具
 *
 * 背景：Arthas 4.x 的 HTTP API 返回结构化 JSON，但各命令的包装层级并不统一：
 *   - memory     -> results[0].memoryInfo.{heap,nonheap,buffer_pool}   （值都是数组）
 *   - dashboard  -> results[0].{memoryInfo, gcInfos, runtimeInfo, threads}
 *   - thread     -> results[0].{threadStats:[], threadStateCount:{}}
 *   - thread -n  -> results[0].busyThreads[]
 *   - trace      -> results[] 中 type='trace' 的那一项（'enhancer' 项在它前面）
 *   - profiler   -> results[] 中 type='profiler' 的那一项
 *   - jvm        -> results[0].jvmInfo
 *
 * 早期实现是各组件直接按 3.x 的结构取字段， Arthas 升级到 4.x 后全部取空。
 * 这里把解析收敛到一处，组件只消费规范化后的数据，避免多处各写一套猜测逻辑。
 */

/** 按 type 字段定位结果条目 */
export function pickByType(results: any[], type: string): any | null {
  if (!Array.isArray(results)) return null
  return results.find((r) => r && r.type === type) || null
}

/** 按结构特征定位结果条目（type 缺失时的兜底） */
export function pickByShape(results: any[], predicate: (item: any) => boolean): any | null {
  if (!Array.isArray(results)) return null
  return results.find((r) => r && predicate(r)) || null
}

/** 从结果中取出 memoryInfo（兼容直接传入 memoryInfo 的情况） */
function extractMemoryInfo(source: any): any | null {
  if (!source) return null
  if (source.memoryInfo) return source.memoryInfo
  // 结果条目本身就是 memoryInfo（含 heap 数组）
  if (Array.isArray(source.heap)) return source
  return null
}

export interface MemoryPool {
  name: string
  type: string
  /** MB（粗粒度，保留给概览类展示） */
  used: number
  max: number | string
  usedPercent: number
  /** 原始已用字节数 */
  usedBytes: number
  /**
   * 原始最大字节数。
   * <= 0 表示该池没有可用上限：非堆里的 metaspace 返回 -1，
   * 直接内存更是直接返回 Long.MIN_VALUE（JVM 不上报 MaxDirectMemorySize）。
   * 这两种情况都不能当成 0 展示，否则页面上会显示成"最大 0MB"。
   */
  maxBytes: number
}

export interface ParsedMemory {
  /** 堆总量（MB） */
  heapUsed: number
  heapMax: number
  heapPercent: number
  /** 非堆（MB） */
  nonheapUsed: number
  nonheapMax: number
  nonheapPercent: number
  /** 堆/非堆上限是否为 JVM 未上报（此时分母退化成 total） */
  heapMaxUnlimited: boolean
  nonheapMaxUnlimited: boolean
  /** 各内存池明细（MB） */
  pools: MemoryPool[]
  raw: any
}

const MB = 1024 * 1024

function toMb(bytes: number): number {
  return bytes > 0 ? Math.round(bytes / MB) : 0
}

/**
 * 自适应单位格式化字节数。
 *
 * 直接内存（buffer_pool）这类池子常常只有几十 KB，
 * 固定按 MB 取整会一律显示成 0，看着像"没采集到数据"。
 */
export function formatBytes(bytes: number): { value: string; unit: string } {
  const KB = 1024
  const GB = 1024 * 1024 * 1024
  if (!bytes || bytes <= 0) return { value: '0', unit: 'B' }
  if (bytes >= GB) return { value: (bytes / GB).toFixed(2), unit: 'GB' }
  if (bytes >= MB) return { value: (bytes / MB).toFixed(1), unit: 'MB' }
  if (bytes >= KB) return { value: (bytes / KB).toFixed(1), unit: 'KB' }
  return { value: String(bytes), unit: 'B' }
}

/** formatBytes 的字符串版，直接塞进表格/统计卡 */
export function formatBytesText(bytes: number): string {
  const f = formatBytes(bytes)
  return `${f.value} ${f.unit}`
}

/**
 * 解析内存信息。
 *
 * Arthas 把"总计"和"各分区"混在同一个数组里，靠 name 区分：
 * heap[0].name === 'heap' 是整堆汇总，其余（ps_eden_space 等）是分区。
 */
export function parseMemory(results: any[], preferredType = 'memory'): ParsedMemory | null {
  const item = pickByType(results, preferredType) || pickByShape(results, (r) => !!r.memoryInfo)
  const info = extractMemoryInfo(item) || extractMemoryInfo(results?.[0])
  if (!info) return null

  const heapArr: any[] = Array.isArray(info.heap) ? info.heap : []
  const nonheapArr: any[] = Array.isArray(info.nonheap) ? info.nonheap : []
  const bufferArr: any[] = Array.isArray(info.buffer_pool) ? info.buffer_pool : []

  const heapTotal = heapArr.find((p) => p && p.name === 'heap')
  const nonheapTotal = nonheapArr.find((p) => p && p.name === 'nonheap')

  // JVM 对堆一般会上报 max，但非堆（metaspace 未设上限时）返回 -1。
  // 上限缺失时退化用 total（已提交容量）当分母，否则进度条会恒为 0%。
  const pickMaxBytes = (p: any): { maxBytes: number; unlimited: boolean } => {
    const max = Number(p?.max) || 0
    if (max > 0) return { maxBytes: max, unlimited: false }
    return { maxBytes: Number(p?.total) || 0, unlimited: true }
  }

  const heapUsedBytes = Number(heapTotal?.used) || 0
  const heapCap = pickMaxBytes(heapTotal)
  const nonheapUsedBytes = Number(nonheapTotal?.used) || 0
  const nonheapCap = pickMaxBytes(nonheapTotal)

  const heapUsed = toMb(heapUsedBytes)
  const heapMax = toMb(heapCap.maxBytes)
  const nonheapUsed = toMb(nonheapUsedBytes)
  const nonheapMax = toMb(nonheapCap.maxBytes)

  const pools: MemoryPool[] = []
  const pushPools = (arr: any[], fallbackType: string) => {
    for (const p of arr) {
      if (!p || !p.name) continue
      // 'heap' / 'nonheap' 是汇总行，不作为分区展示
      if (p.name === 'heap' || p.name === 'nonheap') continue
      const usedBytes = Number(p.used) || 0
      const maxBytes = Number(p.max) || 0
      const hasMax = maxBytes > 0
      pools.push({
        name: p.name,
        type: p.type || fallbackType,
        used: toMb(usedBytes),
        max: hasMax ? toMb(maxBytes) : '-',
        // 用字节算百分比：先转 MB 再相除会让几十 KB 的小池子精度全丢
        usedPercent: hasMax ? Math.round((usedBytes / maxBytes) * 100) : 0,
        usedBytes,
        maxBytes: hasMax ? maxBytes : -1
      })
    }
  }
  pushPools(heapArr, 'heap')
  pushPools(nonheapArr, 'nonheap')
  pushPools(bufferArr, 'buffer_pool')

  return {
    heapUsed,
    heapMax,
    // 百分比一律用字节算：先转 MB 再相除，小池子的精度会被抹掉
    heapPercent: heapCap.maxBytes > 0 ? Math.round((heapUsedBytes / heapCap.maxBytes) * 100) : 0,
    nonheapUsed,
    nonheapMax,
    nonheapPercent: nonheapCap.maxBytes > 0 ? Math.round((nonheapUsedBytes / nonheapCap.maxBytes) * 100) : 0,
    heapMaxUnlimited: heapCap.unlimited,
    nonheapMaxUnlimited: nonheapCap.unlimited,
    pools,
    raw: info
  }
}

export interface ParsedThreads {
  /** 线程状态计数，如 { RUNNABLE: 14, WAITING: 3 } */
  stateCount: Record<string, number>
  /** 线程列表 */
  threads: any[]
  raw: any
}

/**
 * 解析 `thread` 命令结果。
 * 完整线程列表在 threadStats 字段里；threadStateCount 是各状态的汇总计数。
 */
export function parseThreads(results: any[]): ParsedThreads | null {
  const item =
    pickByShape(results, (r) => Array.isArray(r.threadStats)) ||
    pickByType(results, 'thread') ||
    results?.[0]
  if (!item) return null

  const list: any[] = Array.isArray(item.threadStats)
    ? item.threadStats
    : Array.isArray(item.threads)
      ? item.threads
      : []

  return {
    stateCount: item.threadStateCount || {},
    threads: list.map((t) => ({
      id: t.id ?? t.threadId,
      name: t.name || t.threadName || '-',
      state: t.state || t.threadState || '-',
      cpu: t.cpu != null ? Number(t.cpu).toFixed(1) : '-',
      daemon: !!t.daemon,
      group: t.group || '-',
      priority: t.priority,
      stackTrace: t.stackTrace || []
    })),
    raw: item
  }
}

/**
 * 解析 `thread -n N`（最忙线程）。
 * 结果在 busyThreads 字段，且每个线程自带 stackTrace。
 */
export function parseBusyThreads(results: any[]): any[] {
  const item =
    pickByShape(results, (r) => Array.isArray(r.busyThreads)) ||
    pickByType(results, 'thread') ||
    results?.[0]
  if (!item) return []
  const list: any[] = Array.isArray(item.busyThreads) ? item.busyThreads : []
  return list.map((t) => ({
    id: t.id ?? t.threadId,
    name: t.name || t.threadName || '-',
    state: t.state || '-',
    cpu: t.cpu != null ? Number(t.cpu).toFixed(2) : '-',
    daemon: !!t.daemon,
    stackTrace: t.stackTrace || []
  }))
}

/**
 * 解析 `thread -b`（死锁检测）。
 *
 * 无死锁时 Arthas 返回的唯一输出是一条 status 消息：
 *   { type:'status', statusCode:1, message:'No most blocking thread found!' }
 * 后端已放宽过滤，会保留这条信息；这里统一成文本输出。
 */
export function parseDeadlock(results: any[]): { text: string; hasDeadlock: boolean } {
  if (!Array.isArray(results) || results.length === 0) {
    return { text: '', hasDeadlock: false }
  }
  const parts: string[] = []
  for (const r of results) {
    if (!r) continue
    if (typeof r === 'string') {
      parts.push(r)
    } else if (r.message) {
      parts.push(String(r.message))
    } else if (r.value) {
      parts.push(String(r.value))
    } else {
      parts.push(JSON.stringify(r, null, 2))
    }
  }
  const text = parts.join('\n').trim()
  const noDeadlock =
    text.includes('No most blocking thread') ||
    text.includes('No deadlock') ||
    text.includes('没有死锁') ||
    text.includes('未发现')
  return { text: noDeadlock ? '' : text, hasDeadlock: !noDeadlock }
}

export interface ParsedGc {
  youngCount: number
  youngTimeMs: number
  fullCount: number
  fullTimeMs: number
}

const YOUNG_GC_KEYS = ['scavenge', 'young', 'copy', 'parnew', 'g1 young']
const FULL_GC_KEYS = ['marksweep', 'mark-sweep', 'old', 'concurrentmarksweep', 'g1 old', 'full']

function matchKeys(name: string, keys: string[]): boolean {
  const lower = name.toLowerCase()
  return keys.some((k) => lower.includes(k))
}

/**
 * 解析 GC 统计。
 * 优先用 dashboard 的 gcInfos（结构扁平），缺失时回退到 jvm 命令的 GARBAGE-COLLECTORS。
 */
export function parseGc(results: any[]): ParsedGc | null {
  // 形态一：dashboard -> gcInfos: [{name:'ps_scavenge', collectionCount, collectionTime}]
  const dash = pickByShape(results, (r) => Array.isArray(r.gcInfos))
  if (dash && Array.isArray(dash.gcInfos)) {
    const gc: ParsedGc = { youngCount: 0, youngTimeMs: 0, fullCount: 0, fullTimeMs: 0 }
    for (const g of dash.gcInfos) {
      const name = String(g?.name || '')
      if (matchKeys(name, YOUNG_GC_KEYS)) {
        gc.youngCount += g.collectionCount || 0
        gc.youngTimeMs += g.collectionTime || 0
      } else if (matchKeys(name, FULL_GC_KEYS)) {
        gc.fullCount += g.collectionCount || 0
        gc.fullTimeMs += g.collectionTime || 0
      }
    }
    return gc
  }

  // 形态二：jvm -> jvmInfo['GARBAGE-COLLECTORS']: [{name, value:{collectionCount, collectionTime}}]
  const jvmItem = pickByType(results, 'jvm') || pickByShape(results, (r) => !!r.jvmInfo)
  const collectors = jvmItem?.jvmInfo?.['GARBAGE-COLLECTORS']
  if (!Array.isArray(collectors)) return null

  const gc: ParsedGc = { youngCount: 0, youngTimeMs: 0, fullCount: 0, fullTimeMs: 0 }
  for (const c of collectors) {
    const name = String(c?.name || '')
    const val = c?.value || {}
    if (matchKeys(name, YOUNG_GC_KEYS)) {
      gc.youngCount += val.collectionCount || 0
      gc.youngTimeMs += val.collectionTime || 0
    } else if (matchKeys(name, FULL_GC_KEYS)) {
      gc.fullCount += val.collectionCount || 0
      gc.fullTimeMs += val.collectionTime || 0
    }
  }
  return gc
}

/**
 * 解析 trace 调用树。
 *
 * 两处需要注意：
 * 1. 结果数组里第一项通常是 type='enhancer' 的增强回执，调用树在 type='trace' 那一项；
 * 2. root 是一个包装节点，自身不带 className/methodName，
 *    真正的调用树根是它的第一个子节点。直接用 root 渲染会显示成 unknown。
 */
export function parseTraceTree(results: any[]): any | null {
  const item = pickByType(results, 'trace') || pickByShape(results, (r) => !!r.root)
  if (!item) return null
  let node = item.root || item
  if (node && !node.className && !node.methodName && Array.isArray(node.children) && node.children.length === 1) {
    node = node.children[0]
  }
  return node
}

/**
 * 把 Arthas 的 stackTrace 数组渲染成类 Java 的堆栈文本。
 * Arthas 返回的是结构化帧数组，直接 JSON.stringify 给用户看可读性很差。
 */
export function formatStackTrace(stack: any[]): string {
  if (!Array.isArray(stack) || stack.length === 0) return ''
  return stack
    .map((f) => {
      const cls = f?.className || ''
      const method = f?.methodName || ''
      const line = f?.lineNumber != null && f.lineNumber > 0 ? f.lineNumber : null
      const file = f?.fileName || ''
      const location = file ? (line ? `${file}:${line}` : file) : line ? `Unknown Source:${line}` : ''
      return location ? `    at ${cls}.${method}(${location})` : `    at ${cls}.${method}(Native Method)`
    })
    .join('\n')
}

/**
 * 解析 profiler stop 结果，取出火焰图文件路径。
 */
export function parseProfilerStop(results: any[]): { outputFile: string; executeResult: string } | null {
  const item = pickByType(results, 'profiler')
  if (!item) return null
  return {
    outputFile: item.outputFile ? String(item.outputFile) : '',
    executeResult: item.executeResult ? String(item.executeResult) : ''
  }
}
