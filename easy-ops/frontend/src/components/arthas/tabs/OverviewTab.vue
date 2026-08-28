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
import type { DashboardData } from '@/types/arthas'

const onArthasError = inject('onArthasError', (e: any) => {})

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
      parseMemory(memRes.data.results)
    }
    lastCollectTime.value = Date.now()
    message.success('概览采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error('采集失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function parseDashboard(results: any[]) {
  for (const r of results) {
    if (r.type === 'dashboard') {
      // 解析 thread 部分
      if (r.thread) {
        dashboard.value.thread.total = r.thread.total || 0
        dashboard.value.thread.runnable = r.thread.runnable || 0
        dashboard.value.thread.timedWaiting = r.thread['timed-waiting'] || 0
        dashboard.value.thread.waiting = r.thread.waiting || 0
        dashboard.value.thread.blocked = r.thread.blocked || 0
        dashboard.value.thread.deadlock = r.thread.deadlock || 0
      }
      // 解析 memory 部分
      if (r.memory) {
        const heap = r.memory.heap || {}
        dashboard.value.memory.heapUsed = Math.round((heap.used || 0) / 1024 / 1024)
        dashboard.value.memory.heapMax = Math.round((heap.max || 0) / 1024 / 1024)
      }
      // 解析 gc 部分
      if (r.gc) {
        dashboard.value.gc.youngCount = r.gc['gc.ps_scavenge.count'] || r.gc['gc.g1_young_generation.count'] || 0
        dashboard.value.gc.youngTimeMs = Math.round((r.gc['gc.ps_scavenge.time'] || r.gc['gc.g1_young_generation.time'] || 0) * 1000)
        dashboard.value.gc.fullCount = r.gc['gc.ps_marksweep.count'] || r.gc['gc.g1_old_generation.count'] || 0
        dashboard.value.gc.fullTimeMs = Math.round((r.gc['gc.ps_marksweep.time'] || r.gc['gc.g1_old_generation.time'] || 0) * 1000)
      }
    }
  }
}

function parseMemory(results: any[]) {
  for (const r of results) {
    if (r.type === 'memory') {
      const heap = r.heap || {}
      const nonheap = r['non-heap'] || {}
      // 老年代
      if (heap['ps_old_gen'] || heap['g1_old_gen']) {
        const old = heap['ps_old_gen'] || heap['g1_old_gen']
        dashboard.value.memory.oldGenUsed = Math.round((old.used || 0) / 1024 / 1024)
        dashboard.value.memory.oldGenMax = Math.round((old.max || 0) / 1024 / 1024)
      }
      // Eden
      if (heap['ps_eden_space'] || heap['g1_eden_space']) {
        const eden = heap['ps_eden_space'] || heap['g1_eden_space']
        dashboard.value.memory.edenUsed = Math.round((eden.used || 0) / 1024 / 1024)
        dashboard.value.memory.edenMax = Math.round((eden.max || 0) / 1024 / 1024)
      }
      // Survivor
      if (heap['ps_survivor_space'] || heap['g1_survivor_space']) {
        const surv = heap['ps_survivor_space'] || heap['g1_survivor_space']
        dashboard.value.memory.survivorUsed = Math.round((surv.used || 0) / 1024 / 1024)
        dashboard.value.memory.survivorMax = Math.round((surv.max || 0) / 1024 / 1024)
      }
      // Metaspace
      if (nonheap.metaspace) {
        dashboard.value.memory.metaspaceUsed = Math.round((nonheap.metaspace.used || 0) / 1024 / 1024)
        dashboard.value.memory.metaspaceMax = Math.round((nonheap.metaspace.max || 0) / 1024 / 1024)
      }
    }
  }
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
