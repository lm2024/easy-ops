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
        message="诊断连接已断开"
        description="Arthas 会话已中断（常见原因：长时间空闲被回收、Agent 重启、目标进程重启）。点击下方按钮重新连接。"
      >
        <template #action>
          <a-button type="primary" size="small" @click="reconnect" :loading="connecting">
            重新连接
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
        <a-tab-pane key="diagnose" tab="一键诊断">
          <DiagnoseTab :session-id="session.sessionId" />
        </a-tab-pane>
        <a-tab-pane key="history" tab="诊断历史">
          <HistoryTab :project-id="props.target?.projectId" />
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

    <!-- 一键体检进度/结果 -->
    <a-modal
      v-model:open="checkupModalVisible"
      :title="checkupFinished ? '体检报告' : '一键体检'"
      :closable="true"
      :footer="null"
      width="600px"
    >
      <!-- 进行中：展示进度 -->
      <template v-if="!checkupFinished">
        <a-steps :current="checkupStep" direction="vertical" size="small">
          <a-step title="采集 Dashboard" :description="checkupResults.dashboard ? '完成' : (checkupStep === 0 ? '进行中...' : '等待中')" />
          <a-step title="采集内存信息" :description="checkupResults.memory ? '完成' : (checkupStep === 1 ? '进行中...' : '等待中')" />
          <a-step title="采集线程信息" :description="checkupResults.thread ? '完成' : (checkupStep === 2 ? '进行中...' : '等待中')" />
          <a-step title="采集 GC 统计" :description="checkupResults.gc ? '完成' : (checkupStep === 3 ? '进行中...' : '等待中')" />
          <a-step title="生成体检报告" :description="checkupStep >= 4 ? '完成' : '等待中'" />
        </a-steps>
        <div v-if="checkupError" style="margin-top: 16px">
          <a-alert type="error" :message="checkupError" />
        </div>
      </template>

      <!-- 完成后：展示结果 -->
      <template v-else>
        <a-spin :spinning="checkupParsing">
          <div class="checkup-report">
            <!-- 健康评分 -->
            <div class="checkup-score">
              <div class="score-circle" :class="healthLevel">
                <span class="score-value">{{ healthScore }}</span>
                <span class="score-label">分</span>
              </div>
              <div class="score-info">
                <div class="health-level" :class="healthLevel">{{ healthLevelText }}</div>
                <div class="health-desc">{{ healthDesc }}</div>
              </div>
            </div>

            <a-divider style="margin: 16px 0" />

            <!-- 关键指标 -->
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="线程总数">
                {{ checkupSummary.thread.total }}
              </a-descriptions-item>
              <a-descriptions-item label="BLOCKED 线程" :span="1">
                <a-tag :color="checkupSummary.thread.blocked > 0 ? 'error' : 'success'">
                  {{ checkupSummary.thread.blocked }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="堆内存使用率" :span="2">
                <a-progress
                  :percent="checkupSummary.memory.heapPercent"
                  :stroke-color="checkupSummary.memory.heapPercent > 80 ? '#ff4d4f' : checkupSummary.memory.heapPercent > 60 ? '#faad14' : '#52c41a'"
                  size="small"
                />
              </a-descriptions-item>
              <a-descriptions-item label="老年代使用率" :span="2">
                <a-progress
                  :percent="checkupSummary.memory.oldGenPercent"
                  :stroke-color="checkupSummary.memory.oldGenPercent > 80 ? '#ff4d4f' : checkupSummary.memory.oldGenPercent > 60 ? '#faad14' : '#52c41a'"
                  size="small"
                />
              </a-descriptions-item>
              <a-descriptions-item label="Full GC 次数">
                <a-tag :color="checkupSummary.gc.fullCount > 10 ? 'error' : 'default'">
                  {{ checkupSummary.gc.fullCount }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="Young GC 次数">
                {{ checkupSummary.gc.youngCount }}
              </a-descriptions-item>
            </a-descriptions>

            <!-- 问题告警 -->
            <div v-if="checkupAlerts.length > 0" style="margin-top: 16px">
              <div class="section-title">发现问题</div>
              <a-alert
                v-for="(alert, idx) in checkupAlerts"
                :key="idx"
                :message="alert"
                type="warning"
                show-icon
                style="margin-bottom: 8px"
              />
            </div>

            <!-- 无问题提示 -->
            <div v-else style="margin-top: 16px">
              <a-result
                status="success"
                title="各项指标正常"
                sub-title="未发现明显异常，JVM 状态健康"
                style="padding: 12px 0"
              />
            </div>
          </div>
        </a-spin>
      </template>
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
import DiagnoseTab from './tabs/DiagnoseTab.vue'
import { startArthasDiagnose, stopArthasDiagnose, execArthasCommand } from '@/api/arthas'
import type { ArthasSession, ArthasDiagnoseTarget } from '@/types/arthas'
import { friendlyMessage, toFriendlyError } from '@/utils/arthasError'

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

// 提供给子组件的错误处理方法。
// 后端在 exec 前会先尝试自动重连一次，能走到这里说明重连也没救回来，才需要打断用户。
provide('onArthasError', (error: any) => {
  if (toFriendlyError(error).sessionLost) {
    sessionExpired.value = true
  }
})

// 一键体检
const checkupModalVisible = ref(false)
const checkupStep = ref(0)
const checkupError = ref('')
const checkupFinished = ref(false)
const checkupParsing = ref(false)
const checkupResults = ref<{ dashboard?: any; memory?: any; thread?: any; gc?: any }>({})

// 体检结果摘要
const checkupSummary = ref({
  thread: { total: 0, blocked: 0, deadlock: 0 },
  memory: { heapPercent: 0, oldGenPercent: 0 },
  gc: { youngCount: 0, youngTimeMs: 0, fullCount: 0, fullTimeMs: 0 }
})
const checkupAlerts = ref<string[]>([])
const healthScore = ref(100)
const healthLevel = ref('excellent')
const healthLevelText = ref('优秀')
const healthDesc = ref('JVM 状态健康')

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
        message.error(friendlyMessage('结束失败', e))
      }
    }
  })
}

async function handleQuickCheckup() {
  if (!session.value) return
  checkupModalVisible.value = true
  checkupStep.value = 0
  checkupError.value = ''
  checkupFinished.value = false
  checkupParsing.value = false
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

    // 解析结果并生成报告
    checkupParsing.value = true
    await parseCheckupResults()
    checkupFinished.value = true
    message.success('一键体检完成')
  } catch (e: any) {
    checkupError.value = e.message || '体检失败'
    message.error(friendlyMessage('一键体检失败', e))
  } finally {
    checkupParsing.value = false
  }
}

async function parseCheckupResults() {
  // 解析线程数据
  const threadData = checkupResults.value.thread
  if (threadData && Array.isArray(threadData)) {
    let total = 0, blocked = 0, deadlock = 0
    for (const item of threadData) {
      const threads = item.threads || item.data?.threads || []
      if (Array.isArray(threads)) {
        total += threads.length
        for (const t of threads) {
          const state = String(t.state || '').toUpperCase()
          if (state.includes('BLOCKED')) blocked++
          if (state.includes('DEADLOCK') || state === 'deadlock') deadlock++
        }
      } else if (typeof item === 'object') {
        // 可能是聚合格式
        total += item.total || 0
        blocked += item.blocked || 0
        deadlock += item.deadlock || 0
      }
    }
    checkupSummary.value.thread = { total, blocked, deadlock }
  }

  // 解析内存数据
  const memData = checkupResults.value.memory
  if (memData && Array.isArray(memData)) {
    let heapUsed = 0, heapMax = 0, oldGenUsed = 0, oldGenMax = 0
    for (const item of memData) {
      if (item.type === 'memory' || item.name?.includes('heap')) {
        heapUsed += item.used || 0
        heapMax += item.max || 0
      }
      const name = String(item.name || '').toLowerCase()
      if (name.includes('old') || name.includes('tenured')) {
        oldGenUsed += item.used || 0
        oldGenMax += item.max || 0
      }
    }
    checkupSummary.value.memory = {
      heapPercent: heapMax > 0 ? Math.round((heapUsed / heapMax) * 100) : 0,
      oldGenPercent: oldGenMax > 0 ? Math.round((oldGenUsed / oldGenMax) * 100) : 0
    }
  }

  // 解析 GC 数据
  const gcData = checkupResults.value.gc
  if (gcData && Array.isArray(gcData)) {
    let youngCount = 0, youngTimeMs = 0, fullCount = 0, fullTimeMs = 0
    for (const item of gcData) {
      const gcInfos = item.gcInfos || item.data?.gcInfos || []
      if (Array.isArray(gcInfos)) {
        for (const gc of gcInfos) {
          const name = String(gc.name || '').toLowerCase()
          if (name.includes('young') || name.includes('scavenge') || name === 'ps scavenge' || name === 'g1 young generation') {
            youngCount += gc.count || 0
            youngTimeMs += gc.time || 0
          } else if (name.includes('old') || name.includes('mark') || name.includes('full') || name === 'ps marksweep' || name === 'g1 old generation') {
            fullCount += gc.count || 0
            fullTimeMs += gc.time || 0
          }
        }
      }
    }
    checkupSummary.value.gc = { youngCount, youngTimeMs, fullCount, fullTimeMs }
  }

  // 生成告警
  const alerts: string[] = []
  if (checkupSummary.value.thread.blocked > 0) {
    alerts.push(`检测到 ${checkupSummary.value.thread.blocked} 个 BLOCKED 线程，可能存在锁竞争`)
  }
  if (checkupSummary.value.thread.deadlock > 0) {
    alerts.push(`检测到 ${checkupSummary.value.thread.deadlock} 个死锁线程！`)
  }
  if (checkupSummary.value.memory.heapPercent > 80) {
    alerts.push(`堆内存使用率 ${checkupSummary.value.memory.heapPercent}%，建议检查内存泄漏`)
  }
  if (checkupSummary.value.memory.oldGenPercent > 80) {
    alerts.push(`老年代使用率 ${checkupSummary.value.memory.oldGenPercent}%，可能需要 Full GC`)
  }
  if (checkupSummary.value.gc.fullCount > 10) {
    alerts.push(`Full GC 次数 ${checkupSummary.value.gc.fullCount}，频率过高`)
  }
  checkupAlerts.value = alerts

  // 计算健康分数
  let score = 100
  if (checkupSummary.value.thread.blocked > 0) score -= 10
  if (checkupSummary.value.thread.deadlock > 0) score -= 30
  if (checkupSummary.value.memory.heapPercent > 80) score -= 20
  if (checkupSummary.value.memory.oldGenPercent > 80) score -= 15
  if (checkupSummary.value.gc.fullCount > 10) score -= 10
  score = Math.max(0, score)
  healthScore.value = score

  if (score >= 80) {
    healthLevel.value = 'excellent'
    healthLevelText.value = '优秀'
    healthDesc.value = 'JVM 状态健康，各项指标正常'
  } else if (score >= 60) {
    healthLevel.value = 'good'
    healthLevelText.value = '良好'
    healthDesc.value = 'JVM 状态基本正常，存在轻微问题'
  } else if (score >= 40) {
    healthLevel.value = 'warning'
    healthLevelText.value = '警告'
    healthDesc.value = 'JVM 存在明显问题，需要关注'
  } else {
    healthLevel.value = 'danger'
    healthLevelText.value = '危险'
    healthDesc.value = 'JVM 状态异常，建议立即处理'
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

/* 体检报告样式 */
.checkup-report {
  padding: 8px 0;
}
.checkup-score {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}
.score-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.score-circle.excellent {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}
.score-circle.good {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}
.score-circle.warning {
  background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
}
.score-circle.danger {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
}
.score-value {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  line-height: 1;
}
.score-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2px;
}
.score-info {
  flex: 1;
}
.health-level {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
}
.health-level.excellent { color: #52c41a; }
.health-level.good { color: #1890ff; }
.health-level.warning { color: #faad14; }
.health-level.danger { color: #ff4d4f; }
.health-desc {
  font-size: 13px;
  color: #8c8c8c;
}
.section-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
  color: #262626;
}
</style>
