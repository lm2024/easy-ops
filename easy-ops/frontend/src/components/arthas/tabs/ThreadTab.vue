<template>
  <div class="thread-tab">
    <div class="toolbar">
      <a-space>
        <a-button type="primary" size="small" @click="collectAll" :loading="loading">
          <reload-outlined /> 采集全部
        </a-button>
        <a-button size="small" @click="collectThreads" :loading="threadLoading">线程列表</a-button>
        <a-button size="small" @click="collectBusyThreads" :loading="busyLoading">最忙线程</a-button>
        <a-button size="small" danger @click="detectDeadlock" :loading="deadlockLoading">
          <warning-outlined /> 死锁检测
        </a-button>
      </a-space>
      <span v-if="lastCollectTime" class="last-collect">最后更新: {{ formatTime(lastCollectTime) }}</span>
    </div>

    <a-spin :spinning="loading">
      <!-- 线程状态分布 -->
      <div class="section" v-if="threadStats">
        <div class="section-title">线程状态分布</div>
        <a-row :gutter="16">
          <a-col :span="4"><a-statistic title="总线程" :value="threadStats.total" /></a-col>
          <a-col :span="4"><a-statistic title="RUNNABLE" :value="threadStats.runnable" :value-style="{ color: '#52c41a' }" /></a-col>
          <a-col :span="4"><a-statistic title="WAITING" :value="threadStats.waiting" /></a-col>
          <a-col :span="4"><a-statistic title="TIMED_WAIT" :value="threadStats.timedWaiting" /></a-col>
          <a-col :span="4"><a-statistic title="BLOCKED" :value="threadStats.blocked" :value-style="{ color: threadStats.blocked > 0 ? '#ff4d4f' : 'inherit' }" /></a-col>
          <a-col :span="4"><a-statistic title="死锁" :value="threadStats.deadlock" :value-style="{ color: threadStats.deadlock > 0 ? '#ff4d4f' : '#52c41a' }" /></a-col>
        </a-row>
      </div>

      <!-- 死锁告警 -->
      <div v-if="deadlockResult" class="section">
        <a-alert type="error" show-icon>
          <template #message>检测到死锁！</template>
          <template #description>
            <pre class="deadlock-detail">{{ deadlockResult }}</pre>
          </template>
        </a-alert>
      </div>

      <!-- 最忙线程 -->
      <div class="section" v-if="busyThreads.length > 0">
        <div class="section-title">CPU 最忙线程 (Top 5)</div>
        <a-table :columns="busyColumns" :data-source="busyThreads" :pagination="false" size="small" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button type="link" size="small" @click="viewThreadStack(record.id)">查看栈</a-button>
            </template>
          </template>
        </a-table>
      </div>

      <!-- 线程列表 -->
      <div class="section">
        <div class="section-title">线程列表</div>
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索线程名"
          style="margin-bottom: 12px; width: 300px"
          allow-clear
        />
        <a-table
          :columns="threadColumns"
          :data-source="filteredThreads"
          :pagination="{ pageSize: 10, size: 'small' }"
          size="small"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'state'">
              <a-tag :color="stateColor(record.state)">{{ record.state }}</a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-button type="link" size="small" @click="viewThreadStack(record.id)">栈</a-button>
            </template>
          </template>
        </a-table>
      </div>

      <!-- 线程栈详情 -->
      <a-modal
        v-model:open="stackModalVisible"
        :title="`线程栈 - ${currentThreadName}`"
        width="700px"
        :footer="null"
      >
        <pre class="thread-stack">{{ currentStack }}</pre>
      </a-modal>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, WarningOutlined } from '@ant-design/icons-vue'
import { execArthasCommand } from '@/api/arthas'

const onArthasError = inject('onArthasError', (e: any) => {})

const props = defineProps<{ sessionId: string }>()

const loading = ref(false)
const threadLoading = ref(false)
const busyLoading = ref(false)
const deadlockLoading = ref(false)
const lastCollectTime = ref(0)
const searchKeyword = ref('')

const threads = ref<any[]>([])
const busyThreads = ref<any[]>([])
const deadlockResult = ref('')
const stackModalVisible = ref(false)
const currentThreadName = ref('')
const currentStack = ref('')

const threadStats = computed(() => {
  if (threads.value.length === 0) return null
  const stats = { total: threads.value.length, runnable: 0, waiting: 0, timedWaiting: 0, blocked: 0, deadlock: 0 }
  for (const t of threads.value) {
    const state = t.state || t.threadState || ''
    if (state.includes('RUNNABLE')) stats.runnable++
    else if (state.includes('TIMED_WAIT')) stats.timedWaiting++
    else if (state.includes('WAITING')) stats.waiting++
    else if (state.includes('BLOCKED')) stats.blocked++
  }
  return stats
})

const filteredThreads = computed(() => {
  if (!searchKeyword.value) return threads.value
  const kw = searchKeyword.value.toLowerCase()
  return threads.value.filter(t => (t.name || '').toLowerCase().includes(kw))
})

const threadColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '线程名', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '状态', dataIndex: 'state', key: 'state', width: 120 },
  { title: 'CPU%', dataIndex: 'cpu', key: 'cpu', width: 80 },
  { title: '操作', key: 'action', width: 80 }
]

const busyColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '线程名', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: 'CPU%', dataIndex: 'cpu', key: 'cpu', width: 100 },
  { title: '操作', key: 'action', width: 100 }
]

function stateColor(state: string): string {
  if (state.includes('RUNNABLE')) return 'green'
  if (state.includes('BLOCKED')) return 'red'
  if (state.includes('WAITING')) return 'orange'
  if (state.includes('TIMED_WAIT')) return 'blue'
  return 'default'
}

async function execCommand(command: string, timeoutMs = 5000) {
  const res = await execArthasCommand({ sessionId: props.sessionId, command, timeoutMs })
  if (res.data && res.data.success && res.data.results) {
    return res.data.results
  }
  throw new Error(res.data?.errorMsg || '命令执行失败')
}

async function collectThreads() {
  threadLoading.value = true
  try {
    const results = await execCommand('thread', 5000)
    if (results.length > 0) {
      const data = results[0]
      if (Array.isArray(data)) {
        threads.value = data.map((t: any) => ({
          id: t.id || t.threadId,
          name: t.name || t.threadName,
          state: t.state || t.threadState,
          cpu: t.cpu != null ? t.cpu.toFixed(1) : '-'
        }))
      } else if (data.threads) {
        threads.value = data.threads.map((t: any) => ({
          id: t.id || t.threadId,
          name: t.name || t.threadName,
          state: t.state || t.threadState,
          cpu: t.cpu != null ? t.cpu.toFixed(1) : '-'
        }))
      }
    }
    lastCollectTime.value = Date.now()
    message.success('线程列表采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error('采集失败: ' + e.message)
  } finally {
    threadLoading.value = false
  }
}

async function collectBusyThreads() {
  busyLoading.value = true
  try {
    const results = await execCommand('thread -n 5', 8000)
    if (results.length > 0) {
      const data = results[0]
      if (Array.isArray(data)) {
        busyThreads.value = data.map((t: any) => ({
          id: t.id || t.threadId,
          name: t.name || t.threadName,
          cpu: t.cpu != null ? t.cpu.toFixed(2) : '-'
        }))
      }
    }
    message.success('最忙线程采集完成')
  } catch (e: any) {
    onArthasError(e)
    message.error('采集失败: ' + e.message)
  } finally {
    busyLoading.value = false
  }
}

async function detectDeadlock() {
  deadlockLoading.value = true
  deadlockResult.value = ''
  try {
    const results = await execCommand('thread -b', 8000)
    if (results.length > 0) {
      const data = results[0]
      if (typeof data === 'string') {
        deadlockResult.value = data
      } else if (data.message) {
        deadlockResult.value = data.message
      } else {
        deadlockResult.value = JSON.stringify(data, null, 2)
      }
      if (deadlockResult.value.includes('No deadlock') || deadlockResult.value.includes('没有死锁')) {
        message.success('未检测到死锁')
        deadlockResult.value = ''
      } else {
        message.warning('检测到死锁！')
      }
    }
  } catch (e: any) {
    onArthasError(e)
    message.error('检测失败: ' + e.message)
  } finally {
    deadlockLoading.value = false
  }
}

async function viewThreadStack(threadId: number) {
  try {
    const thread = threads.value.find(t => t.id === threadId) || busyThreads.value.find(t => t.id === threadId)
    currentThreadName.value = thread?.name || `Thread-${threadId}`
    const results = await execCommand(`thread ${threadId}`, 5000)
    if (results.length > 0) {
      const data = results[0]
      currentStack.value = typeof data === 'string' ? data : JSON.stringify(data, null, 2)
    }
    stackModalVisible.value = true
  } catch (e: any) {
    onArthasError(e)
    message.error('获取线程栈失败: ' + e.message)
  }
}

async function collectAll() {
  loading.value = true
  try {
    await Promise.all([collectThreads(), collectBusyThreads(), detectDeadlock()])
  } finally {
    loading.value = false
  }
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

defineExpose({ collectAll, collectThreads })
</script>

<style scoped>
.thread-tab { padding: 0 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.last-collect { color: #8c8c8c; font-size: 12px; }
.section { margin-bottom: 24px; }
.section-title { font-weight: 600; font-size: 14px; margin-bottom: 12px; color: #262626; border-left: 3px solid #1890ff; padding-left: 8px; }
.thread-stack { max-height: 500px; overflow: auto; background: #f5f5f5; padding: 12px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; }
.deadlock-detail { max-height: 300px; overflow: auto; white-space: pre-wrap; font-size: 12px; }
</style>
