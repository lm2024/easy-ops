<template>
  <div class="overview-tab">
    <div class="toolbar">
      <a-button type="primary" size="small" @click="collectOverview" :loading="loading">
        <reload-outlined /> 采集概览
      </a-button>
      <span v-if="lastCollectTime" class="last-collect">最后更新: {{ formatTime(lastCollectTime) }}</span>
    </div>

    <a-spin :spinning="loading">
      <!-- 线程统计 -->
      <div class="section">
        <div class="section-title">线程统计</div>
        <a-row :gutter="16">
          <a-col :span="4"><a-statistic title="总线程" :value="thread.total" /></a-col>
          <a-col :span="4"><a-statistic title="RUNNABLE" :value="thread.runnable" :value-style="{ color: '#52c41a' }" /></a-col>
          <a-col :span="4"><a-statistic title="WAITING" :value="thread.waiting" /></a-col>
          <a-col :span="4"><a-statistic title="TIMED_WAIT" :value="thread.timedWaiting" /></a-col>
          <a-col :span="4"><a-statistic title="BLOCKED" :value="thread.blocked" :value-style="{ color: thread.blocked > 0 ? '#ff4d4f' : 'inherit' }" /></a-col>
          <a-col :span="4"><a-statistic title="死锁" :value="thread.deadlock" :value-style="{ color: thread.deadlock > 0 ? '#ff4d4f' : '#52c41a' }" /></a-col>
        </a-row>
      </div>

      <!-- 内存使用 -->
      <div class="section">
        <div class="section-title">内存使用</div>
        <div class="memory-list">
          <div class="memory-item" v-for="item in memoryItems" :key="item.label">
            <div class="memory-label">{{ item.label }}</div>
            <a-progress
              :percent="item.percent"
              :show-info="false"
              :stroke-color="item.percent > 80 ? '#ff4d4f' : item.percent > 60 ? '#faad14' : '#52c41a'"
              :size="'small'"
            />
            <div class="memory-value">{{ item.used }} / {{ item.max }} MB ({{ item.percent }}%)</div>
          </div>
        </div>
      </div>

      <!-- GC 统计 -->
      <div class="section">
        <div class="section-title">GC 统计</div>
        <a-row :gutter="16">
          <a-col :span="6"><a-statistic title="YGC 次数" :value="gc.youngCount" /></a-col>
          <a-col :span="6"><a-statistic title="YGC 耗时(ms)" :value="gc.youngTimeMs" /></a-col>
          <a-col :span="6"><a-statistic title="FGC 次数" :value="gc.fullCount" :value-style="{ color: gc.fullCount > 10 ? '#faad14' : 'inherit' }" /></a-col>
          <a-col :span="6"><a-statistic title="FGC 耗时(ms)" :value="gc.fullTimeMs" /></a-col>
        </a-row>
      </div>

      <!-- 异常告警 -->
      <div v-if="alerts.length > 0" class="section">
        <div class="section-title">异常告警</div>
        <a-alert
          v-for="(alert, idx) in alerts"
          :key="idx"
          :message="alert"
          type="warning"
          show-icon
          style="margin-bottom: 8px"
        />
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { execArthasCommand } from '@/api/arthas'
import { parseMemory, parseGc, pickByType } from '@/utils/arthasParse'
import type { DashboardData } from '@/types/arthas'
import { friendlyMessage } from '@/utils/arthasError'

const onArthasError = inject('onArthasError', (_e: any) => {})

const props = defineProps<{
  sessionId: string
}>()

const loading = ref(false)
const lastCollectTime = ref<number>(0)
const dashboard = ref<DashboardData>({
  thread: { total: 0, runnable: 0, timedWaiting: 0, waiting: 0, blocked: 0, deadlock: 0 },
  memory: { heapUsed: 0, heapMax: 0, oldGenUsed: 0, oldGenMax: 0, edenUsed: 0, edenMax: 0, survivorUsed: 0, survivorMax: 0, metaspaceUsed: 0, metaspaceMax: 0 },
  gc: { youngCount: 0, youngTimeMs: 0, fullCount: 0, fullTimeMs: 0 },
  runtime: { osName: '', javaVersion: '', processCpuPercent: 0, systemCpuPercent: 0, uptime: 0 }
})

const thread = computed(() => dashboard.value.thread)
const gc = computed(() => dashboard.value.gc)

const memoryItems = computed(() => {
  const m = dashboard.value.memory
  return [
    { label: '堆 (Heap)', used: m.heapUsed, max: m.heapMax, percent: calcPercent(m.heapUsed, m.heapMax) },
    { label: '老年代 (Old Gen)', used: m.oldGenUsed, max: m.oldGenMax, percent: calcPercent(m.oldGenUsed, m.oldGenMax) },
    { label: 'Eden', used: m.edenUsed, max: m.edenMax, percent: calcPercent(m.edenUsed, m.edenMax) },
    { label: 'Survivor', used: m.survivorUsed, max: m.survivorMax, percent: calcPercent(m.survivorUsed, m.survivorMax) },
    { label: '元空间 (Metaspace)', used: m.metaspaceUsed, max: m.metaspaceMax, percent: calcPercent(m.metaspaceUsed, m.metaspaceMax) }
  ]
})

const alerts = computed(() => {
  const list: string[] = []
  if (calcPercent(dashboard.value.memory.oldGenUsed, dashboard.value.memory.oldGenMax) > 80) {
    list.push(`老年代使用率 ${calcPercent(dashboard.value.memory.oldGenUsed, dashboard.value.memory.oldGenMax)}%，建议检查内存泄漏`)
  }
  if (dashboard.value.gc.fullCount > 10) {
    list.push(`FGC 次数 ${dashboard.value.gc.fullCount}，频率过高`)
  }
  if (dashboard.value.thread.deadlock > 0) {
    list.push(`检测到 ${dashboard.value.thread.deadlock} 个死锁线程！`)
  }
  if (dashboard.value.thread.blocked > 5) {
    list.push(`BLOCKED 线程 ${dashboard.value.thread.blocked} 个，可能存在锁竞争`)
  }
  return list
})

function calcPercent(used: number, max: number): number {
  if (max <= 0) return 0
  return Math.round((used / max) * 100)
}

async function collectOverview() {
  loading.value = true
  try {
    // 执行 dashboard 命令（取一次快照）
    const res = await execArthasCommand({ sessionId: props.sessionId, command: 'dashboard -n 1', timeoutMs: 10000 })
    if (res.data && res.data.success && res.data.results) {
      parseDashboard(res.data.results)
    }
    // 执行 memory 命令补充内存详情
    const memRes = await execArthasCommand({ sessionId: props.sessionId, command: 'memory', timeoutMs: 5000 })
    if (memRes.data && memRes.data.success && memRes.data.results) {
      applyMemory(memRes.data.results)
    }
    lastCollectTime.value = Date.now()
    message.success('概览采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error(friendlyMessage('采集失败', e))
  } finally {
    loading.value = false
  }
}

function parseDashboard(results: any[]) {
  const item = pickByType(results, 'dashboard')
  if (!item) {
    return
  }

  // 线程统计：Arthas 4.x 的 dashboard 给出的是完整线程数组（threads），
  // 不像 3.x 那样预先聚合好各状态数量，需要自己归类。
  const list: any[] = Array.isArray(item.threads) ? item.threads : []
  const stat = { total: list.length, runnable: 0, timedWaiting: 0, waiting: 0, blocked: 0, deadlock: 0 }
  for (const th of list) {
    const s = String(th?.state || '')
    if (s.includes('RUNNABLE')) stat.runnable++
    else if (s.includes('TIMED_WAIT')) stat.timedWaiting++
    else if (s.includes('WAITING')) stat.waiting++
    else if (s.includes('BLOCKED')) stat.blocked++
  }
  dashboard.value.thread = stat

  // 内存与 GC：dashboard 本身已带 memoryInfo 和 gcInfos，直接复用统一解析
  applyMemory(results)
  const parsedGc = parseGc(results)
  if (parsedGc) {
    dashboard.value.gc = parsedGc
  }
}

/**
 * 提取堆与各内存分区，回填到概览指标。
 * 分区名随垃圾收集器不同而变化（Parallel 是 ps_*，G1 是 g1_*），这里都兼容。
 */
function applyMemory(results: any[]) {
  const mem = parseMemory(results)
  if (!mem) return
  const m = dashboard.value.memory
  m.heapUsed = mem.heapUsed
  m.heapMax = mem.heapMax

  const pick = (names: string[]) => (mem.pools || []).find((p: any) => names.includes(p.name))
  const maxOf = (pool: any) => (pool && typeof pool.max === 'number' ? pool.max : 0)

  const oldGen = pick(['ps_old_gen', 'g1_old_gen'])
  m.oldGenUsed = oldGen ? oldGen.used : 0
  m.oldGenMax = maxOf(oldGen)

  const eden = pick(['ps_eden_space', 'g1_eden_space'])
  m.edenUsed = eden ? eden.used : 0
  m.edenMax = maxOf(eden)

  const survivor = pick(['ps_survivor_space', 'g1_survivor_space'])
  m.survivorUsed = survivor ? survivor.used : 0
  m.survivorMax = maxOf(survivor)

  const metaspace = pick(['metaspace'])
  m.metaspaceUsed = metaspace ? metaspace.used : 0
  m.metaspaceMax = maxOf(metaspace)
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

defineExpose({ collectOverview })
</script>

<style scoped>
.overview-tab {
  padding: 0 4px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.last-collect {
  color: #8c8c8c;
  font-size: 12px;
}
.section {
  margin-bottom: 24px;
}
.section-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #262626;
  border-left: 3px solid #1890ff;
  padding-left: 8px;
}
.memory-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.memory-item {
  display: grid;
  grid-template-columns: 140px 1fr 200px;
  align-items: center;
  gap: 12px;
}
.memory-label {
  font-size: 13px;
  color: #595959;
}
.memory-value {
  font-size: 12px;
  color: #8c8c8c;
  text-align: right;
}
</style>
