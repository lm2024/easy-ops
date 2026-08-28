<template>
  <div class="memory-tab">
    <div class="toolbar">
      <a-space>
        <a-button type="primary" size="small" @click="collectAll" :loading="loading">
          <reload-outlined /> 采集全部
        </a-button>
        <a-button size="small" @click="collectMemory" :loading="memoryLoading">内存</a-button>
        <a-button size="small" @click="collectGc" :loading="gcLoading">GC</a-button>
        <a-button size="small" @click="collectClassloader" :loading="classloaderLoading">类加载</a-button>
        <a-button size="small" danger @click="runGc" :loading="gcRunLoading">
          <thunderbolt-outlined /> 触发 GC
        </a-button>
      </a-space>
      <span v-if="lastCollectTime" class="last-collect">最后更新: {{ formatTime(lastCollectTime) }}</span>
    </div>

    <a-spin :spinning="loading">
      <!-- 内存概览 -->
      <div class="section">
        <div class="section-title">内存概览</div>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-card size="small" title="堆内存 (Heap)">
              <a-progress
                type="dashboard"
                :percent="heapPercent"
                :format="() => `${heapUsed} / ${heapMax} MB`"
                :stroke-color="heapPercent > 80 ? '#ff4d4f' : heapPercent > 60 ? '#faad14' : '#52c41a'"
                :width="120"
              />
            </a-card>
          </a-col>
          <a-col :span="8">
            <a-card size="small" title="非堆 (Non-Heap)">
              <a-progress
                type="dashboard"
                :percent="nonheapPercent"
                :format="() => `${nonheapUsed} / ${nonheapMax} MB`"
                :stroke-color="nonheapPercent > 80 ? '#ff4d4f' : '#52c41a'"
                :width="120"
              />
            </a-card>
          </a-col>
          <a-col :span="8">
            <a-card size="small" title="直接内存">
              <a-statistic title="已使用" :value="directUsed" suffix="MB" />
              <a-statistic title="最大" :value="directMax" suffix="MB" :value-style="{ fontSize: '14px' }" />
            </a-card>
          </a-col>
        </a-row>
      </div>

      <!-- 各代详情 -->
      <div class="section" v-if="memoryPools.length > 0">
        <div class="section-title">各内存代详情</div>
        <a-table :columns="poolColumns" :data-source="memoryPools" :pagination="false" size="small" row-key="name">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'usedPercent'">
              <a-progress :percent="record.usedPercent" :size="'small'"
                :stroke-color="record.usedPercent > 80 ? '#ff4d4f' : record.usedPercent > 60 ? '#faad14' : '#52c41a'" />
            </template>
          </template>
        </a-table>
      </div>

      <!-- GC 统计 -->
      <div class="section" v-if="gcStats">
        <div class="section-title">GC 统计</div>
        <a-row :gutter="16">
          <a-col :span="6"><a-statistic title="Young GC 次数" :value="gcStats.youngCount" /></a-col>
          <a-col :span="6"><a-statistic title="Young GC 耗时" :value="gcStats.youngTimeMs" suffix="ms" /></a-col>
          <a-col :span="6"><a-statistic title="Full GC 次数" :value="gcStats.fullCount"
            :value-style="{ color: gcStats.fullCount > 10 ? '#faad14' : 'inherit' }" /></a-col>
          <a-col :span="6"><a-statistic title="Full GC 耗时" :value="gcStats.fullTimeMs" suffix="ms" /></a-col>
        </a-row>
      </div>

      <!-- 类加载器 -->
      <div class="section" v-if="classloaders.length > 0">
        <div class="section-title">类加载器统计</div>
        <a-table :columns="classloaderColumns" :data-source="classloaders" :pagination="false" size="small" row-key="name" />
      </div>

      <!-- 原始数据 -->
      <div class="section">
        <a-collapse>
          <a-collapse-panel key="raw" header="查看原始 JSON 数据">
            <pre class="raw-json">{{ rawJson }}</pre>
          </a-collapse-panel>
        </a-collapse>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { execArthasCommand } from '@/api/arthas'

const onArthasError = inject('onArthasError', (e: any) => {})

const props = defineProps<{ sessionId: string }>()

const loading = ref(false)
const memoryLoading = ref(false)
const gcLoading = ref(false)
const classloaderLoading = ref(false)
const gcRunLoading = ref(false)
const lastCollectTime = ref(0)
const rawJson = ref('')

const memoryData = ref<any>(null)
const gcStats = ref<any>(null)
const classloaders = ref<any[]>([])

const heapUsed = computed(() => memoryData.value?.heap?.used ? Math.round(memoryData.value.heap.used / 1024 / 1024) : 0)
const heapMax = computed(() => memoryData.value?.heap?.max ? Math.round(memoryData.value.heap.max / 1024 / 1024) : 0)
const heapPercent = computed(() => heapMax.value > 0 ? Math.round((heapUsed.value / heapMax.value) * 100) : 0)

const nonheapUsed = computed(() => memoryData.value?.['non-heap']?.used ? Math.round(memoryData.value['non-heap'].used / 1024 / 1024) : 0)
const nonheapMax = computed(() => memoryData.value?.['non-heap']?.max ? Math.round(memoryData.value['non-heap'].max / 1024 / 1024) : 0)
const nonheapPercent = computed(() => nonheapMax.value > 0 ? Math.round((nonheapUsed.value / nonheapMax.value) * 100) : 0)

const directUsed = computed(() => memoryData.value?.direct?.used ? Math.round(memoryData.value.direct.used / 1024 / 1024) : 0)
const directMax = computed(() => memoryData.value?.direct?.max ? Math.round(memoryData.value.direct.max / 1024 / 1024) : 0)

const memoryPools = computed(() => {
  if (!memoryData.value) return []
  const pools: any[] = []
  const heap = memoryData.value.heap || {}
  for (const key of Object.keys(heap)) {
    if (key !== 'used' && key !== 'max' && key !== 'committed' && key !== 'init') {
      const pool = heap[key]
      if (pool.used != null) {
        pools.push({
          name: key,
          type: 'heap',
          used: Math.round(pool.used / 1024 / 1024),
          max: pool.max > 0 ? Math.round(pool.max / 1024 / 1024) : '-',
          usedPercent: pool.max > 0 ? Math.round((pool.used / pool.max) * 100) : 0
        })
      }
    }
  }
  const nonheap = memoryData.value['non-heap'] || {}
  for (const key of Object.keys(nonheap)) {
    if (key !== 'used' && key !== 'max' && key !== 'committed' && key !== 'init') {
      const pool = nonheap[key]
      if (pool.used != null) {
        pools.push({
          name: key,
          type: 'non-heap',
          used: Math.round(pool.used / 1024 / 1024),
          max: pool.max > 0 ? Math.round(pool.max / 1024 / 1024) : '-',
          usedPercent: pool.max > 0 ? Math.round((pool.used / pool.max) * 100) : 0
        })
      }
    }
  }
  return pools
})

const poolColumns = [
  { title: '内存池', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '已用(MB)', dataIndex: 'used', key: 'used', width: 100 },
  { title: '最大(MB)', dataIndex: 'max', key: 'max', width: 100 },
  { title: '使用率', dataIndex: 'usedPercent', key: 'usedPercent', width: 200 }
]

const classloaderColumns = [
  { title: '加载器', dataIndex: 'name', key: 'name' },
  { title: '已加载类数', dataIndex: 'loadedCount', key: 'loadedCount', width: 120 },
  { title: '实例数', dataIndex: 'instanceCount', key: 'instanceCount', width: 120 }
]

async function execCommand(command: string, timeoutMs = 5000) {
  const res = await execArthasCommand({ sessionId: props.sessionId, command, timeoutMs })
  if (res.data && res.data.success && res.data.results) {
    return res.data.results
  }
  throw new Error(res.data?.errorMsg || '命令执行失败')
}

async function collectMemory() {
  memoryLoading.value = true
  try {
    const results = await execCommand('memory', 5000)
    if (results.length > 0) {
      memoryData.value = results[0]
      rawJson.value = JSON.stringify(results[0], null, 2)
    }
    lastCollectTime.value = Date.now()
    message.success('内存数据采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error('采集失败: ' + e.message)
  } finally {
    memoryLoading.value = false
  }
}

async function collectGc() {
  gcLoading.value = true
  try {
    const results = await execCommand('jvm', 5000)
    if (results.length > 0) {
      const jvmData = results[0]
      const collectors = jvmData?.jvmInfo?.['GARBAGE-COLLECTORS'] || []
      let youngCount = 0, youngTimeMs = 0, fullCount = 0, fullTimeMs = 0
      for (const c of collectors) {
        const name = c.name || ''
        const val = c.value || {}
        const count = val.collectionCount || 0
        const time = val.collectionTime || 0
        if (name.includes('Scavenge') || name.includes('Young') || name.includes('Copy')) {
          youngCount = count
          youngTimeMs = time
        } else if (name.includes('MarkSweep') || name.includes('Old') || name.includes('ConcurrentMarkSweep')) {
          fullCount = count
          fullTimeMs = time
        }
      }
      gcStats.value = { youngCount, youngTimeMs, fullCount, fullTimeMs }
    }
    message.success('GC 统计采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error('采集失败: ' + e.message)
  } finally {
    gcLoading.value = false
  }
}

async function collectClassloader() {
  classloaderLoading.value = true
  try {
    const results = await execCommand('classloader -l', 5000)
    if (results.length > 0) {
      const data = results[0]
      if (Array.isArray(data)) {
        classloaders.value = data.map((item: any) => ({
          name: item.name || item.classloader || 'unknown',
          loadedCount: item.loadedCount || '-',
          instanceCount: item.instanceCount || '-'
        }))
      }
    }
    message.success('类加载器信息采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error('采集失败: ' + e.message)
  } finally {
    classloaderLoading.value = false
  }
}

async function runGc() {
  gcRunLoading.value = true
  try {
    await execCommand('vmtool --action forceGc', 5000)
    message.success('GC 已触发')
    await collectMemory()
  } catch (e: any) {
    onArthasError(e)
    message.error('触发失败: ' + e.message)
  } finally {
    gcRunLoading.value = false
  }
}

async function collectAll() {
  loading.value = true
  try {
    await Promise.all([collectMemory(), collectGc(), collectClassloader()])
  } finally {
    loading.value = false
  }
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

defineExpose({ collectAll, collectMemory })
</script>

<style scoped>
.memory-tab { padding: 0 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.last-collect { color: #8c8c8c; font-size: 12px; }
.section { margin-bottom: 24px; }
.section-title { font-weight: 600; font-size: 14px; margin-bottom: 12px; color: #262626; border-left: 3px solid #1890ff; padding-left: 8px; }
.raw-json { max-height: 400px; overflow: auto; background: #f5f5f5; padding: 12px; border-radius: 4px; font-size: 12px; }
</style>
