<template>
  <a-drawer
    :open="visible"
    title="JVM 诊断"
    placement="right"
    :width="960"
    :mask="false"
    :destroy-on-close="true"
    @update:open="handleClose"
  >
    <!-- 连接中 -->
    <div v-if="connecting" class="connecting">
      <a-spin size="large" />
      <div class="connecting-text">正在 attach 到目标 JVM...</div>
      <div class="connecting-detail">PID: {{ target?.pid }} | {{ target?.projectName }} ({{ target?.nodeName }})</div>
    </div>

    <!-- 连接失败 -->
    <div v-else-if="connectError" class="connect-error">
      <a-result status="error" title="Attach 失败" :sub-title="connectError">
        <template #extra>
          <a-button type="primary" @click="retry">重试</a-button>
        </template>
      </a-result>
    </div>

    <!-- 已连接 -->
    <div v-else-if="session">
      <!-- 会话过期提示 -->
      <a-alert
        v-if="sessionExpired"
        type="warning"
        show-icon
        style="margin-bottom: 12px"
        message="诊断会话已过期"
        description="Arthas 会话因空闲超时已自动结束，点击下方按钮重新 attach。"
      >
        <template #action>
          <a-button type="primary" size="small" @click="reconnect" :loading="connecting">
            重新 attach
          </a-button>
        </template>
      </a-alert>

      <ArthasStatusBar
        :connected="!sessionExpired"
        :arthas-version="session.arthasVersion"
        :elapsed-seconds="elapsedSeconds"
        :loading="statusBarLoading"
        @quick-checkup="handleQuickCheckup"
        @generate-report="handleGenerateReport"
        @stop="handleStop"
      />

      <a-tabs v-model:activeKey="activeTab" type="card">
        <a-tab-pane key="overview" tab="概览">
          <OverviewTab ref="overviewTabRef" :session-id="session.sessionId" />
        </a-tab-pane>
        <a-tab-pane key="memory" tab="内存分析">
          <MemoryTab ref="memoryTabRef" :session-id="session.sessionId" />
        </a-tab-pane>
        <a-tab-pane key="thread" tab="线程分析">
          <ThreadTab ref="threadTabRef" :session-id="session.sessionId" />
        </a-tab-pane>
        <a-tab-pane key="flamegraph" tab="火焰图">
          <FlameGraphTab :session-id="session.sessionId" />
        </a-tab-pane>
        <a-tab-pane key="trace" tab="方法追踪">
          <TraceTab :session-id="session.sessionId" />
        </a-tab-pane>
        <a-tab-pane key="history" tab="诊断历史">
          <HistoryTab />
        </a-tab-pane>
      </a-tabs>
    </div>

    <!-- 未连接（初始状态） -->
    <div v-else class="not-connected">
      <a-result status="info" title="JVM 诊断">
        <template #extra>
          <a-button type="primary" @click="startDiagnose">开始诊断</a-button>
        </template>
      </a-result>
    </div>

    <!-- 一键体检进度 -->
    <a-modal
      v-model:open="checkupModalVisible"
      title="一键体检"
      :footer="null"
      :closable="false"
      width="500px"
    >
      <a-steps :current="checkupStep" direction="vertical" size="small">
        <a-step title="采集 Dashboard" :description="checkupResults.dashboard ? '完成' : '进行中...'" />
        <a-step title="采集内存信息" :description="checkupResults.memory ? '完成' : '等待中'" />
        <a-step title="采集线程信息" :description="checkupResults.thread ? '完成' : '等待中'" />
        <a-step title="采集 GC 统计" :description="checkupResults.gc ? '完成' : '等待中'" />
        <a-step title="生成体检报告" :description="checkupStep >= 4 ? '完成' : '等待中'" />
      </a-steps>
      <div v-if="checkupError" style="margin-top: 16px">
        <a-alert type="error" :message="checkupError" />
      </div>
    </a-modal>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, provide } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ArthasStatusBar from './ArthasStatusBar.vue'
import OverviewTab from './tabs/OverviewTab.vue'
import MemoryTab from './tabs/MemoryTab.vue'
import ThreadTab from './tabs/ThreadTab.vue'
import FlameGraphTab from './tabs/FlameGraphTab.vue'
import TraceTab from './tabs/TraceTab.vue'
import HistoryTab from './tabs/HistoryTab.vue'
import { startArthasDiagnose, stopArthasDiagnose, execArthasCommand } from '@/api/arthas'
import type { ArthasSession, ArthasDiagnoseTarget } from '@/types/arthas'

const props = defineProps<{
  visible: boolean
  target?: ArthasDiagnoseTarget | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const connecting = ref(false)
const connectError = ref('')
const session = ref<ArthasSession | null>(null)
const activeTab = ref('overview')
const elapsedSeconds = ref(0)
const statusBarLoading = ref(false)
const overviewTabRef = ref()
const memoryTabRef = ref()
const threadTabRef = ref()
const sessionExpired = ref(false)

// 提供给子组件的错误处理方法
provide('onArthasError', (error: any) => {
  const msg = error?.message || String(error)
  if (msg.includes('会话不存在') || msg.includes('已结束') || msg.includes('session')) {
    sessionExpired.value = true
  }
})

// 一键体检
const checkupModalVisible = ref(false)
const checkupStep = ref(0)
const checkupError = ref('')
const checkupResults = ref<{ dashboard?: any; memory?: any; thread?: any; gc?: any }>({})

let elapsedTimer: number | null = null

watch(() => props.visible, (val) => {
  if (val && props.target && !session.value && !connecting.value) {
    startDiagnose()
  }
})

onMounted(() => {
  if (props.visible && props.target) {
    startDiagnose()
  }
})

onBeforeUnmount(() => {
  stopElapsedTimer()
})

async function startDiagnose() {
  if (!props.target) {
    message.error('未指定诊断目标')
    return
  }
  connecting.value = true
  connectError.value = ''
  try {
    const res = await startArthasDiagnose({
      projectId: props.target.projectId,
      nodeId: props.target.nodeId,
      pid: props.target.pid
    })
    if (res.data) {
      session.value = res.data
      startElapsedTimer()
      message.success('Arthas attach 成功')
      // 自动采集概览
      setTimeout(() => {
        overviewTabRef.value?.collectOverview()
      }, 500)
    } else {
      connectError.value = res.message || '未知错误'
    }
  } catch (e: any) {
    connectError.value = e.message || 'attach 失败'
  } finally {
    connecting.value = false
  }
}

function retry() {
  connectError.value = ''
  startDiagnose()
}

// 重新 attach（会话过期后随时可以拉起来）
async function reconnect() {
  sessionExpired.value = false
  session.value = null
  connectError.value = ''
  stopElapsedTimer()
  await startDiagnose()
}

async function handleStop() {
  if (!session.value) return
  Modal.confirm({
    title: '结束诊断',
    content: '确定要结束本次诊断会话吗？Arthas 将从目标 JVM 卸载。',
    okText: '结束',
    cancelText: '取消',
    onOk: async () => {
      try {
        await stopArthasDiagnose({ sessionId: session.value!.sessionId })
        message.success('诊断已结束')
        session.value = null
        stopElapsedTimer()
        emit('update:visible', false)
      } catch (e: any) {
        message.error('结束失败: ' + (e.message || e))
      }
    }
  })
}

async function handleQuickCheckup() {
  if (!session.value) return
  checkupModalVisible.value = true
  checkupStep.value = 0
  checkupError.value = ''
  checkupResults.value = {}

  try {
    // 1. Dashboard
    checkupStep.value = 0
    const dashboardRes = await execArthasCommand({
      sessionId: session.value.sessionId,
      command: 'dashboard -n 1',
      timeoutMs: 10000
    })
    checkupResults.value.dashboard = dashboardRes.data?.results
    checkupStep.value = 1

    // 2. Memory
    const memoryRes = await execArthasCommand({
      sessionId: session.value.sessionId,
      command: 'memory',
      timeoutMs: 5000
    })
    checkupResults.value.memory = memoryRes.data?.results
    checkupStep.value = 2

    // 3. Thread
    const threadRes = await execArthasCommand({
      sessionId: session.value.sessionId,
      command: 'thread',
      timeoutMs: 5000
    })
    checkupResults.value.thread = threadRes.data?.results
    checkupStep.value = 3

    // 4. GC (从 jvm 命令中提取)
    const gcRes = await execArthasCommand({
      sessionId: session.value.sessionId,
      command: 'jvm',
      timeoutMs: 5000
    })
    checkupResults.value.gc = gcRes.data?.results
    checkupStep.value = 4

    message.success('一键体检完成')
    setTimeout(() => {
      checkupModalVisible.value = false
      // 自动切换到概览并刷新
      activeTab.value = 'overview'
      overviewTabRef.value?.collectOverview()
    }, 1000)
  } catch (e: any) {
    checkupError.value = e.message || '体检失败'
    message.error('一键体检失败: ' + e.message)
  }
}

function handleGenerateReport() {
  if (!session.value) return
  message.loading('正在生成诊断报告...')
  // 生成报告：汇总当前所有 Tab 的数据
  setTimeout(() => {
    const report = generateReport()
    // 下载报告
    const blob = new Blob([report], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `jvm-diagnose-report-${Date.now()}.txt`
    a.click()
    URL.revokeObjectURL(url)
    message.success('报告已生成并下载')
  }, 1000)
}

function generateReport(): string {
  const lines: string[] = []
  lines.push('='.repeat(60))
  lines.push('JVM 诊断报告')
  lines.push('='.repeat(60))
  lines.push(`生成时间: ${new Date().toLocaleString()}`)
  lines.push(`节点: ${props.target?.nodeName || '-'}`)
  lines.push(`项目: ${props.target?.projectName || '-'}`)
  lines.push(`PID: ${props.target?.pid || '-'}`)
  lines.push(`Arthas 版本: ${session.value?.arthasVersion || '-'}`)
  lines.push(`诊断时长: ${elapsedSeconds.value} 秒`)
  lines.push('')
  lines.push('【一键体检结果】')
  if (checkupResults.value.dashboard) {
    lines.push('- Dashboard: 已采集')
  }
  if (checkupResults.value.memory) {
    lines.push('- 内存: 已采集')
  }
  if (checkupResults.value.thread) {
    lines.push('- 线程: 已采集')
  }
  if (checkupResults.value.gc) {
    lines.push('- GC: 已采集')
  }
  lines.push('')
  lines.push('【建议】')
  lines.push('1. 查看内存分析 Tab，关注堆内存使用率和 Full GC 次数')
  lines.push('2. 查看线程分析 Tab，关注 BLOCKED 状态线程和死锁')
  lines.push('3. 使用火焰图定位 CPU 热点方法')
  lines.push('4. 使用方法追踪定位慢方法')
  lines.push('')
  lines.push('='.repeat(60))
  return lines.join('\n')
}

function handleClose(val: boolean) {
  if (!val && session.value) {
    Modal.confirm({
      title: '关闭诊断面板',
      content: '诊断会话将在后台继续运行，10 分钟无活动后自动结束。确定关闭吗？',
      okText: '关闭',
      cancelText: '取消',
      onOk: () => {
        emit('update:visible', false)
      }
    })
    return
  }
  emit('update:visible', val)
}

function startElapsedTimer() {
  stopElapsedTimer()
  elapsedSeconds.value = 0
  elapsedTimer = window.setInterval(() => {
    elapsedSeconds.value++
  }, 1000)
}

function stopElapsedTimer() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}
</script>

<style scoped>
.connecting,
.connect-error,
.not-connected {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
.connecting-text {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 500;
}
.connecting-detail {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 13px;
}
</style>
