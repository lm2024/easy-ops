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
            <a-card size="small" :title="nonheapTitle">
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
              <a-statistic title="已使用" :value="directUsed.value" :suffix="directUsed.unit" />
              <a-statistic title="最大" :value="directMax.value" :suffix="directMax.unit" :value-style="{ fontSize: '14px' }" />
            </a-card>
          </a-col>
        </a-row>
      </div>

      <!-- 各代详情 -->
      <div class="section" v-if="memoryPools.length > 0">
        <div class="section-title">各内存代详情</div>
        <a-table :columns="poolColumns" :data-source="memoryPools" :pagination="false" size="small" row-key="name">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'used'">
              <span>{{ formatBytesText(record.usedBytes) }}</span>
            </template>
            <template v-else-if="column.key === 'max'">
              <span v-if="record.maxBytes > 0">{{ formatBytesText(record.maxBytes) }}</span>
              <span v-else class="muted">未限制</span>
            </template>
            <template v-else-if="column.key === 'usedPercent'">
              <a-progress v-if="record.maxBytes > 0" :percent="record.usedPercent" :size="'small'"
                :stroke-color="record.usedPercent > 80 ? '#ff4d4f' : record.usedPercent > 60 ? '#faad14' : '#52c41a'" />
              <span v-else class="muted">无上限</span>
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
      <div class="section" v-if="rawJson">
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
import { parseMemory, parseGc, formatBytes, formatBytesText } from '@/utils/arthasParse'
import { friendlyMessage } from '@/utils/arthasError'

const onArthasError = inject('onArthasError', (_e: any) => {})

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

// 内存指标统一走解析层：Arthas 4.x 的内存数据藏在 memoryInfo 里且是数组结构，
// 直接在组件里按旧结构取字段会全部拿到 undefined。
const heapUsed = computed(() => memoryData.value?.heapUsed ?? 0)
const heapMax = computed(() => memoryData.value?.heapMax ?? 0)
const heapPercent = computed(() => memoryData.value?.heapPercent ?? 0)

const nonheapUsed = computed(() => memoryData.value?.nonheapUsed ?? 0)
const nonheapMax = computed(() => memoryData.value?.nonheapMax ?? 0)
const nonheapPercent = computed(() => memoryData.value?.nonheapPercent ?? 0)

/**
 * 直接内存单独走自适应单位。
 * 它常常只有几十 KB（实测 81921 字节 ≈ 80KB），按 MB 取整必然显示 0。
 */
const directPool = computed(
  () => (memoryData.value?.pools || []).find((p: any) => p.name === 'direct') || null
)
const directUsed = computed(() => formatBytes(directPool.value?.usedBytes || 0))
const directMax = computed(() => {
  const p = directPool.value
  // maxBytes <= 0 表示 JVM 未上报上限：Arthas 对 direct 直接返回 Long.MIN_VALUE
  return p && p.maxBytes > 0 ? formatBytes(p.maxBytes) : { value: '未限制', unit: '' }
})

// 非堆（metaspace）常返回 max=-1，此时分母已退化成 total，标题上标注一下避免误解
const nonheapTitle = computed(() =>
  memoryData.value?.nonheapMaxUnlimited ? '非堆 (Non-Heap, 按已提交)' : '非堆 (Non-Heap)'
)

const memoryPools = computed(() => memoryData.value?.pools || [])

const poolColumns = [
  { title: '内存池', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '已用', dataIndex: 'used', key: 'used', width: 110 },
  { title: '最大', dataIndex: 'max', key: 'max', width: 110 },
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
    memoryData.value = parseMemory(results)
    rawJson.value = JSON.stringify(results[0] ?? null, null, 2)
    if (!memoryData.value) {
      message.warning('未能解析内存数据，请查看原始 JSON')
    }
    lastCollectTime.value = Date.now()
    message.success('内存数据采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error(friendlyMessage('采集失败', e))
  } finally {
    memoryLoading.value = false
  }
}

async function collectGc() {
  gcLoading.value = true
  try {
    const results = await execCommand('jvm', 5000)
    const gc = parseGc(results)
    if (gc) {
      gcStats.value = gc
      message.success('GC 统计采集完成')
    } else {
      message.warning('未能解析 GC 统计，请查看原始 JSON')
    }
  } catch (e: any) {
    onArthasError(e)
    message.error(friendlyMessage('采集失败', e))
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
    message.error(friendlyMessage('采集失败', e))
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
    message.error(friendlyMessage('触发失败', e))
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
.raw-json { max-height: 400px; overflow: auto; background: rgba(0, 0, 0, 0.15); padding: 12px; border-radius: 4px; font-size: 12px; color: inherit; min-height: unset; }
.muted { opacity: 0.45; font-size: 12px; }
</style>
