<template>
  <a-drawer
    :open="visible"
    :title="title"
    :width="720"
    :bodyStyle="{ padding: '12px 16px' }"
    @close="$emit('close')"
  >
    <template #extra>
      <a-space v-if="!isAgentMode" size="small">
        <a-spin v-if="autoRefreshing" size="small" />
        <span style="color:#8c8c8c;font-size:11px">
          {{ autoRefreshing ? '刷新中...' : (lastRefreshAgo || '') }}
        </span>
      </a-space>
    </template>

    <!-- 数据更新闪烁效果容器 -->
    <div :class="{ 'detail-flash': dataJustUpdated }">
    <!-- 进程未运行时的警告 -->
    <a-alert
      v-if="!hasPid"
      type="warning"
      show-icon
      style="margin-bottom: 12px"
      message="进程未运行或 PID 未知"
      description="进程未检测到 PID，基础指标和 JVM 详情可能不完整。线程详情和 JVM 详情需要有效 PID 才能采集。请确认进程已启动后再试。"
    />

    <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
      <!-- ==================== Tab 1: 基础指标 ==================== -->
      <a-tab-pane key="basic" tab="基础指标">
        <a-row :gutter="[16, 16]">
          <a-col :span="12">
            <a-card size="small" title="主机 CPU">
              <a-progress
                type="dashboard"
                :percent="hostCpu"
                :stroke-color="hostCpu > 80 ? '#ff4d4f' : hostCpu > 60 ? '#faad14' : '#52c41a'"
                :size="100"
              />
              <div style="text-align:center;margin-top:4px;color:#888;font-size:12px">
                {{ valOrDash(basicData?.hostCpuPercent) }}%
              </div>
            </a-card>
          </a-col>
          <a-col :span="12">
            <a-card size="small" title="进程 CPU">
              <a-progress
                type="dashboard"
                :percent="procCpu"
                :stroke-color="procCpu > 80 ? '#ff4d4f' : procCpu > 60 ? '#faad14' : '#52c41a'"
                :size="100"
              />
              <div style="text-align:center;margin-top:4px;color:#888;font-size:12px">
                {{ valOrDash(basicData?.cpuPercent) }}%
              </div>
            </a-card>
          </a-col>
          <a-col :span="12">
            <a-card size="small" title="主机内存">
              <a-progress
                type="dashboard"
                :percent="hostMem"
                :stroke-color="hostMem > 80 ? '#ff4d4f' : hostMem > 60 ? '#faad14' : '#52c41a'"
                :size="100"
              />
              <div style="text-align:center;margin-top:4px;color:#888;font-size:12px">
                {{ valOrDash(basicData?.hostMemoryPercent) }}%
              </div>
            </a-card>
          </a-col>
          <a-col :span="12">
            <a-card size="small" title="JVM 堆内存">
              <a-progress
                type="dashboard"
                :percent="heapPercent"
                :stroke-color="heapPercent > 85 ? '#ff4d4f' : heapPercent > 70 ? '#faad14' : '#52c41a'"
                :size="100"
              />
              <div style="text-align:center;margin-top:4px;color:#888;font-size:12px">
                {{ basicData?.heapUsedMb ?? '--' }} / {{ basicData?.heapMaxMb ?? '--' }} MB
              </div>
              <div v-if="basicData?.xmxMb" style="text-align:center;margin-top:2px;color:#1890ff;font-size:11px">
                JVM 上限(-Xmx): {{ basicData.xmxMb }} MB
              </div>
            </a-card>
          </a-col>
        </a-row>
        <a-divider style="margin:12px 0" />
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="进程状态">
            <a-tag :color="basicData?.processStatus === 'RUNNING' ? 'green' : 'red'">
              {{ basicData?.processStatus ?? 'UNKNOWN' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item :label="isAgentMode ? 'Agent PID' : '应用PID'">{{ basicData?.processPid != null && basicData.processPid > 0 ? basicData.processPid : '未检测到' }}</a-descriptions-item>
          <a-descriptions-item label="Agent PID" v-if="!isAgentMode">{{ basicData?.agentPid != null && basicData.agentPid > 0 ? basicData.agentPid : '未上报' }}</a-descriptions-item>
          <a-descriptions-item label="进程内存">{{ basicData?.memoryMb != null ? basicData.memoryMb + ' MB' : '未采集' }}</a-descriptions-item>
          <a-descriptions-item label="磁盘使用">{{ basicData?.diskUsagePercent != null ? basicData.diskUsagePercent + '%' : '未采集' }}</a-descriptions-item>
          <a-descriptions-item label="GC 次数">{{ basicData?.gcCount ?? '未采集' }}</a-descriptions-item>
          <a-descriptions-item label="GC 耗时">{{ basicData?.gcTimeMs != null ? basicData.gcTimeMs + ' ms' : '未采集' }}</a-descriptions-item>
          <a-descriptions-item label="JVM 上限(-Xmx)">{{ basicData?.xmxMb != null ? basicData.xmxMb + ' MB' : '未采集' }}</a-descriptions-item>
          <a-descriptions-item label="响应时间">
            <span v-if="basicData?.responseMs != null" :style="{ color: basicData.responseMs > 3000 ? '#ff4d4f' : basicData.responseMs > 1000 ? '#faad14' : '#52c41a' }">
              {{ basicData.responseMs }} ms
            </span>
            <span v-else style="color:#999">未配置探针</span>
          </a-descriptions-item>
          <a-descriptions-item label="健康状态">
            <a-badge :status="healthBadge" :text="basicData?.healthStatus ?? 'UNKNOWN'" />
          </a-descriptions-item>
          <a-descriptions-item label="健康详情" :span="2">
            <span style="font-size:12px;color:#666">{{ basicData?.healthDetail ?? basicData?.lastError ?? '无' }}</span>
          </a-descriptions-item>
        </a-descriptions>
      </a-tab-pane>

      <!-- ==================== Tab 2: 线程详情 ==================== -->
      <a-tab-pane key="thread" tab="线程详情">
        <div v-if="!hasPid">
          <a-empty description="进程未运行，无法采集线程信息">
            <template #image>
              <span style="font-size:48px">🔒</span>
            </template>
          </a-empty>
        </div>
        <div v-else>
          <a-spin :spinning="threadLoading" tip="正在采集线程信息，请稍候...">
            <template v-if="threadTop">
              <!-- 线程状态分布 -->
              <a-card size="small" title="线程状态分布" style="margin-bottom:12px">
                <a-space wrap>
                  <a-tag v-for="(count, state) in threadTop.stateDistribution" :key="state" :color="stateColor(String(state))">
                    {{ state }}: {{ count }}
                  </a-tag>
                  <a-tag color="default">总计: {{ threadTop.totalThreads }}</a-tag>
                  <a-tag :color="threadTop.totalCpuPercent > 100 ? 'red' : 'blue'">
                    总 CPU: {{ threadTop.totalCpuPercent }}%
                  </a-tag>
                </a-space>
              </a-card>

              <!-- 死锁检测 -->
              <a-alert
                v-if="threadInfo?.deadlock?.detected"
                type="error"
                show-icon
                style="margin-bottom:12px"
              >
                <template #message>
                  <span style="font-weight:bold">⚠️ 检测到死锁！</span>
                </template>
                <template #description>
                  <div>涉及线程: {{ threadInfo.deadlock.threads.join(', ') }}</div>
                  <pre v-if="threadInfo.deadlock.detail" style="margin-top:4px;font-size:11px;max-height:120px;overflow:auto;background:#fff1f0;padding:4px;border-radius:4px">{{ threadInfo.deadlock.detail }}</pre>
                </template>
              </a-alert>
              <a-alert v-else-if="threadInfo" type="success" show-icon style="margin-bottom:12px" message="未检测到死锁 ✅" />

              <!-- 线程 CPU Top 表格 -->
              <a-table
                :data-source="threadTop.topThreads"
                :columns="threadColumns"
                :pagination="false"
                size="small"
                :scroll="{ y: 360 }"
                row-key="tid"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'name'">
                    <a-tooltip :title="record.javaName || record.name">
                      <span style="cursor:help">{{ record.javaName || record.name }}</span>
                    </a-tooltip>
                  </template>
                  <template v-if="column.key === 'cpu'">
                    <a-progress
                      :percent="Math.min(record.cpuPercent, 100)"
                      :stroke-color="record.cpuPercent > 50 ? '#ff4d4f' : record.cpuPercent > 20 ? '#faad14' : '#1890ff'"
                      size="small"
                      :format="() => record.cpuPercent + '%'"
                    />
                  </template>
                  <template v-if="column.key === 'state'">
                    <a-tag :color="stateColor(record.state)">{{ record.state || '--' }}</a-tag>
                  </template>
                </template>
              </a-table>

              <!-- 栈信息 -->
              <a-collapse v-if="hasAnyStack" ghost style="margin-top:8px">
                <a-collapse-panel key="stacks" header="查看线程栈摘要">
                  <div v-for="t in threadInfoThreads" :key="t.name" style="margin-bottom:8px">
                    <div style="font-weight:bold;font-size:12px">
                      <a-tag :color="stateColor(t.state)" style="font-size:11px">{{ t.state }}</a-tag>
                      {{ t.name }}
                    </div>
                    <pre v-if="t.stack?.length" style="font-size:11px;color:#666;margin:2px 0 0 20px;white-space:pre-wrap">{{ t.stack.join('\n') }}</pre>
                  </div>
                </a-collapse-panel>
              </a-collapse>
            </template>
            <a-empty v-else-if="!threadLoading && threadLoadAttempted" description="未获取到线程数据，可能 Agent 未响应或 jcmd 不可用">
              <template #image>
                <span style="font-size:48px">⚠️</span>
              </template>
            </a-empty>
          </a-spin>
        </div>
      </a-tab-pane>

      <!-- ==================== Tab 3: JVM 详情 ==================== -->
      <a-tab-pane key="jvm" tab="JVM 详情">
        <div v-if="!hasPid">
          <a-empty description="进程未运行，无法采集 JVM 信息">
            <template #image>
              <span style="font-size:48px">🔒</span>
            </template>
          </a-empty>
        </div>
        <div v-else>
          <a-spin :spinning="jvmLoading" tip="正在采集 JVM 信息，请稍候...">
            <template v-if="jvmDetail">
              <!-- 堆内存分区 -->
              <a-card size="small" title="堆内存分区" style="margin-bottom:12px">
                <a-row :gutter="[8, 8]">
                  <a-col :span="8" v-for="item in heapParts" :key="item.label">
                    <a-statistic :title="item.label" :value="item.used" :suffix="'/ ' + item.max + ' MB'" :value-style="{ fontSize: '14px' }" />
                    <a-progress :percent="item.max > 0 ? Math.round(item.used / item.max * 100) : 0" size="small" :stroke-color="item.used / (item.max || 1) > 0.85 ? '#ff4d4f' : '#1890ff'" />
                  </a-col>
                </a-row>
              </a-card>

              <!-- 非堆内存 -->
              <a-card size="small" title="非堆内存" style="margin-bottom:12px">
                <a-row :gutter="[16, 8]">
                  <a-col :span="8">
                    <a-statistic title="Metaspace" :value="jvmDetail.metaspaceUsedMb ?? 0" :suffix="'/ ' + (jvmDetail.metaspaceCapacityMb ?? 0) + ' MB'" :value-style="{ fontSize: '14px' }" />
                  </a-col>
                  <a-col :span="8">
                    <a-statistic title="压缩类空间" :value="jvmDetail.compressedClassUsedMb ?? 0" :suffix="'/ ' + (jvmDetail.compressedClassCapacityMb ?? 0) + ' MB'" :value-style="{ fontSize: '14px' }" />
                  </a-col>
                  <a-col :span="8">
                    <a-statistic title="RSS (物理内存)" :value="jvmDetail.rssKb ? Math.round(jvmDetail.rssKb / 1024) : 0" suffix="MB" :value-style="{ fontSize: '14px' }" />
                  </a-col>
                </a-row>
              </a-card>

              <!-- GC 统计 -->
              <a-card size="small" title="GC 统计" style="margin-bottom:12px">
                <a-row :gutter="[16, 8]">
                  <a-col :span="6">
                    <a-statistic title="Young GC 次数" :value="jvmDetail.gcYoungCount ?? 0" :value-style="{ fontSize: '14px' }" />
                  </a-col>
                  <a-col :span="6">
                    <a-statistic title="Young GC 耗时" :value="jvmDetail.gcYoungTimeMs ?? 0" suffix="ms" :value-style="{ fontSize: '14px' }" />
                  </a-col>
                  <a-col :span="6">
                    <a-statistic title="Full GC 次数" :value="jvmDetail.gcFullCount ?? 0" :value-style="{ fontSize: '14px', color: (jvmDetail.gcFullCount ?? 0) > 0 ? '#ff4d4f' : undefined }" />
                  </a-col>
                  <a-col :span="6">
                    <a-statistic title="Full GC 耗时" :value="jvmDetail.gcFullTimeMs ?? 0" suffix="ms" :value-style="{ fontSize: '14px' }" />
                  </a-col>
                </a-row>
              </a-card>

              <!-- 运行时信息 -->
              <a-card size="small" title="运行时信息">
                <a-descriptions :column="2" bordered size="small">
                  <a-descriptions-item label="类加载（已加载）">{{ jvmDetail.classLoaded ?? '--' }}</a-descriptions-item>
                  <a-descriptions-item label="类加载（已卸载）">{{ jvmDetail.classUnloaded ?? '--' }}</a-descriptions-item>
                  <a-descriptions-item label="JIT 编译耗时">{{ jvmDetail.jitCompileTimeMs ?? '--' }} ms</a-descriptions-item>
                  <a-descriptions-item label="线程数">{{ jvmDetail.procThreadCount ?? jvmDetail.threadCount ?? '--' }}</a-descriptions-item>
                  <a-descriptions-item label="文件描述符">{{ jvmDetail.fdCount ?? '--' }}{{ jvmDetail.fdLimit ? ' / ' + jvmDetail.fdLimit : '' }}</a-descriptions-item>
                  <a-descriptions-item label="虚拟内存峰值">{{ jvmDetail.vmPeakKb ? Math.round(jvmDetail.vmPeakKb / 1024) + ' MB' : '--' }}</a-descriptions-item>
                </a-descriptions>
              </a-card>
            </template>
            <a-empty v-else-if="!jvmLoading && jvmLoadAttempted" description="未获取到 JVM 数据，可能 Agent 未响应或 jstat 不可用">
              <template #image>
                <span style="font-size:48px">⚠️</span>
              </template>
            </a-empty>
          </a-spin>
        </div>
      </a-tab-pane>
    </a-tabs>
    </div><!-- /detail-flash wrapper -->
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import type {
  AppMonitorNodeInfo, ThreadTopResult, ThreadInfoResult, JvmDetailResult
} from '../types'
import { getThreadTop, getThreadInfo, getJvmDetail, refreshAppNodeDetail, getAppNodeDetail } from '../api/monitorApp'

interface DrawerRecord extends AppMonitorNodeInfo {
  projectId: number
  projectName?: string
  jarName?: string
}

const props = defineProps<{
  visible: boolean
  record: DrawerRecord | null
}>()

defineEmits<{
  (e: 'close'): void
}>()

const activeTab = ref('basic')
const threadLoading = ref(false)
const jvmLoading = ref(false)
const threadLoadAttempted = ref(false)
const jvmLoadAttempted = ref(false)

const threadTop = ref<ThreadTopResult | null>(null)
const threadInfo = ref<ThreadInfoResult | null>(null)
const jvmDetail = ref<JvmDetailResult | null>(null)

const localRecord = ref<DrawerRecord | null>(null)

// ====== 自动刷新 + 更新动效 ======
const autoRefreshing = ref(false)
const dataJustUpdated = ref(false)
const lastRefreshTime = ref<number>(0)
const nowTick = ref<number>(Date.now()) // 每秒更新，驱动 ago 计算
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null
let flashTimer: ReturnType<typeof setTimeout> | null = null
let agoTimer: ReturnType<typeof setInterval> | null = null

const lastRefreshAgo = computed(() => {
  if (!lastRefreshTime.value) return ''
  const sec = Math.round((nowTick.value - lastRefreshTime.value) / 1000)
  if (sec < 3) return '刚刚更新'
  if (sec < 60) return `${sec}秒前更新`
  return `${Math.round(sec / 60)}分钟前更新`
})

function startAutoRefresh() {
  stopAutoRefresh()
  // 每 3 秒从 DB 拉取最新快照数据
  autoRefreshTimer = setInterval(async () => {
    const r = localRecord.value
    if (!r?.projectId || !r?.nodeId) return
    autoRefreshing.value = true
    try {
      const res = await getAppNodeDetail(r.projectId, r.nodeId)
      if (res.data && localRecord.value) {
        const fresh = res.data
        // 检测数据是否有变化
        const changed = hasDataChanged(localRecord.value, fresh)
        // 合并：保留本地实时采集的 PID，其余用 DB 最新值
        localRecord.value = { ...localRecord.value, ...fresh }
        lastRefreshTime.value = Date.now()
        if (changed) {
          triggerFlash()
        }
      }
    } catch { /* 静默失败，不影响抽屉展示 */ }
    finally { autoRefreshing.value = false }
  }, 3000)

  // 每秒刷新 "N秒前" 文案
  agoTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
}

function stopAutoRefresh() {
  if (autoRefreshTimer) { clearInterval(autoRefreshTimer); autoRefreshTimer = null }
  if (agoTimer) { clearInterval(agoTimer); agoTimer = null }
  if (flashTimer) { clearTimeout(flashTimer); flashTimer = null }
}

function triggerFlash() {
  dataJustUpdated.value = true
  if (flashTimer) clearTimeout(flashTimer)
  flashTimer = setTimeout(() => { dataJustUpdated.value = false }, 1200)
}

function hasDataChanged(oldData: any, newData: any): boolean {
  const keys = ['hostCpuPercent', 'cpuPercent', 'hostMemoryPercent', 'heapUsedMb',
    'heapMaxMb', 'diskUsagePercent', 'processStatus', 'processPid', 'healthStatus',
    'responseMs', 'memoryMb', 'gcCount', 'gcTimeMs', 'xmxMb']
  for (const k of keys) {
    if (oldData[k] !== newData[k]) return true
  }
  return false
}

onUnmounted(() => {
  stopAutoRefresh()
})

// 是否为「Agent 自身」详情（无 projectId）；无 project 时跳过实时刷新、文案切换
const isAgentMode = computed(() => !localRecord.value?.projectId)

const title = computed(() => {
  const r = localRecord.value
  if (!r) return '实例详情'
  if (isAgentMode.value) {
    const pid = (r.agentPid != null && r.agentPid > 0) ? r.agentPid : '未知'
    const addr = `${r.ip ?? ''}:${r.port ?? ''}`
    return `Agent · ${r.nodeName ?? '节点'} (${addr} | PID: ${pid})`
  }
  const procPid = (r.processPid != null && r.processPid > 0) ? r.processPid : '未知'
  const agentPid = (r.agentPid != null && r.agentPid > 0) ? r.agentPid : '未知'
  return `${r.projectName ?? '应用'} / ${r.nodeName ?? '节点'} (应用PID: ${procPid} | AgentPID: ${agentPid})`
})

const basicData = computed(() => localRecord.value)

const hasPid = computed(() => {
  const pid = basicData.value?.processPid
  return pid != null && pid > 0
})

const hostCpu = computed(() => Math.round(basicData.value?.hostCpuPercent ?? 0))
const procCpu = computed(() => Math.round(basicData.value?.cpuPercent ?? 0))
const hostMem = computed(() => basicData.value?.hostMemoryPercent ?? 0)
const heapPercent = computed(() => {
  const used = basicData.value?.heapUsedMb ?? 0
  const max = basicData.value?.heapMaxMb ?? 1
  return max > 0 ? Math.round(used / max * 100) : 0
})

const healthBadge = computed(() => {
  const s = basicData.value?.healthStatus
  if (s === 'UP') return 'success' as const
  if (s === 'DOWN') return 'error' as const
  return 'warning' as const
})

const heapParts = computed(() => {
  const d = jvmDetail.value
  if (!d) return []
  return [
    { label: 'Eden', used: d.edenUsedMb ?? 0, max: d.edenCapacityMb ?? 0 },
    { label: 'Survivor', used: d.survivorUsedMb ?? 0, max: d.survivorUsedMb ?? 0 },
    { label: 'Old', used: d.oldUsedMb ?? 0, max: d.oldCapacityMb ?? 0 },
  ]
})

const threadColumns = [
  { title: '#', key: 'index', width: 50, customRender: (_: any, __: any, index: number) => index + 1 },
  { title: '线程名', key: 'name', dataIndex: 'name', ellipsis: true },
  { title: 'CPU', key: 'cpu', width: 140 },
  { title: '状态', key: 'state', width: 110 },
]

const hasAnyStack = computed(() => {
  return (threadInfo.value?.threads ?? []).some((t: any) => t.stack?.length > 0)
})

const threadInfoThreads = computed(() => threadInfo.value?.threads ?? [])

// Tab 切换时按需加载
function onTabChange(tab: string | number) {
  const key = String(tab)
  const pid = props.record?.processPid
  const nodeId = props.record?.nodeId
  if (!pid || pid <= 0 || !nodeId) return

  if (key === 'thread' && !threadTop.value && !threadLoading.value) {
    loadThreadData(nodeId, pid)
  } else if (key === 'jvm' && !jvmDetail.value && !jvmLoading.value) {
    loadJvmData(nodeId, pid)
  }
}

async function loadThreadData(nodeId: number, pid: number) {
  threadLoading.value = true
  threadLoadAttempted.value = true
  try {
    const [topRes, infoRes] = await Promise.all([
      getThreadTop(nodeId, pid, 30),
      getThreadInfo(nodeId, pid, 5),
    ])
    threadTop.value = topRes.data ?? null
    threadInfo.value = infoRes.data ?? null
    if (!threadTop.value) {
      message.warning('未获取到线程数据，请确认 Agent 正常运行且 jcmd 可用')
    }
  } catch (e: any) {
    message.error('线程信息加载失败: ' + (e?.message ?? '未知错误'))
  } finally {
    threadLoading.value = false
  }
}

async function loadJvmData(nodeId: number, pid: number) {
  jvmLoading.value = true
  jvmLoadAttempted.value = true
  try {
    const res = await getJvmDetail(nodeId, pid)
    jvmDetail.value = res.data ?? null
    if (!jvmDetail.value) {
      message.warning('未获取到 JVM 数据，请确认 Agent 正常运行且 jstat 可用')
    }
  } catch (e: any) {
    message.error('JVM 详情加载失败: ' + (e?.message ?? '未知错误'))
  } finally {
    jvmLoading.value = false
  }
}

// 打开时重置状态，并实时拉取 Agent 当前真实 PID（不落库、不告警）
watch(() => props.visible, (v) => {
  if (v) {
    activeTab.value = 'basic'
    threadTop.value = null
    threadInfo.value = null
    jvmDetail.value = null
    threadLoadAttempted.value = false
    jvmLoadAttempted.value = false
    dataJustUpdated.value = false
    const r = props.record
    localRecord.value = r ? { ...r } : null
    if (r?.projectId && r?.nodeId) {
      refreshAppNodeDetail(r.projectId, r.nodeId)
        .then((res) => {
          const fresh = res.data
          if (fresh && localRecord.value) {
            // 以实时采集结果覆盖（含两个 PID 与实时指标），原记录缺字段时补齐
            localRecord.value = { ...localRecord.value, ...fresh }
            lastRefreshTime.value = Date.now()
          }
        })
        .catch(() => { /* 实时采集失败则保留快照数据，不报错 */ })
      // 启动自动刷新（每 3 秒从 DB 拉最新快照）
      startAutoRefresh()
    }
  } else {
    // 关闭时停止自动刷新
    stopAutoRefresh()
  }
})

function valOrDash(val: number | undefined | null): string {
  return val != null ? Number(val).toFixed(1) : '--'
}

function stateColor(state: string): string {
  switch (state) {
    case 'RUNNABLE': return 'green'
    case 'BLOCKED': return 'red'
    case 'WAITING': return 'orange'
    case 'TIMED_WAITING': return 'blue'
    case 'TERMINATED': return 'default'
    default: return 'default'
  }
}
</script>

<style scoped>
/* 详情数据更新闪烁：1.2s 柔和蓝色边框脉冲 */
.detail-flash {
  animation: detail-flash-anim 1.2s ease-out;
  border-radius: 4px;
}
@keyframes detail-flash-anim {
  0%   { box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.35); }
  50%  { box-shadow: 0 0 0 1px rgba(24, 144, 255, 0.15); }
  100% { box-shadow: none; }
}
</style>
