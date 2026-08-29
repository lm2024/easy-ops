<template>
  <div class="diagnose-tab">
    <div class="toolbar">
      <a-space>
        <a-button type="primary" size="small" @click="runDiagnose('jmap-histo')" :loading="diagnosing">
          <thunderbolt-outlined /> 一键诊断内存
        </a-button>
        <a-button size="small" @click="runDiagnose('mem-alloc')" :loading="diagnosing" type="dashed">
          <fire-outlined /> 内存分配分析（5秒）
        </a-button>
        <a-button size="small" @click="runDiagnose('thread-print')" :loading="diagnosing">
          <team-outlined /> 线程分析
        </a-button>
        <a-button size="small" @click="runDiagnose('gc-stats')" :loading="diagnosing">
          <dashboard-outlined /> GC 统计
        </a-button>
      </a-space>
    </div>

    <a-spin :spinning="diagnosing">
      <!-- 内存诊断结果 -->
      <div v-if="diagnoseResult && diagnoseResult.type === 'jmap-histo'" class="diagnose-result">
        <div class="result-header">
          <a-tag color="blue">内存诊断结果</a-tag>
          <span class="result-time">{{ formatTime(diagnoseResult.timestamp) }}</span>
        </div>

        <!-- 分析建议 -->
        <div v-if="diagnoseResult.analysis" class="analysis-section">
          <div class="section-title">
            <bulb-outlined style="color: #faad14" /> 分析结论
          </div>
          <a-alert
            :type="diagnoseResult.analysis.hasIssue ? 'warning' : 'success'"
            show-icon
            style="margin-bottom: 16px"
          >
            <template #message>
              {{ diagnoseResult.analysis.hasIssue ? '发现问题' : '内存正常' }}
            </template>
            <template #description>
              <ul style="margin: 0; padding-left: 20px">
                <li v-for="(suggestion, idx) in diagnoseResult.analysis.suggestions" :key="idx">
                  {{ suggestion }}
                </li>
              </ul>
            </template>
          </a-alert>
        </div>

        <!-- 内存概览 -->
        <div class="overview-section">
          <a-row :gutter="16">
            <a-col :span="8">
              <a-statistic title="总实例数" :value="diagnoseResult.totalInstances" />
            </a-col>
            <a-col :span="8">
              <a-statistic title="总内存占用" :value="diagnoseResult.totalBytesFormatted" />
            </a-col>
            <a-col :span="8">
              <a-statistic title="类数量" :value="diagnoseResult.classList?.length || 0" />
            </a-col>
          </a-row>
        </div>

        <a-divider />

        <!-- TOP 20 类 -->
        <div class="top-classes-section">
          <div class="section-title">
            <trophy-outlined style="color: #faad14" /> TOP 20 内存占用类
          </div>
          <div class="tip-box">
            <a-alert type="info" show-icon>
              <template #message>如何定位到具体方法？</template>
              <template #description>
                jmap 只能看到<b>类级别</b>的内存占用。如需定位到<b>具体方法</b>，请使用<b>火焰图</b>（内存分配事件），它能精确到方法级别。
              </template>
            </a-alert>
          </div>
          <a-table
            :dataSource="diagnoseResult.topClasses"
            :pagination="false"
            size="small"
            row-key="className"
            :scroll="{ x: 800 }"
          >
            <a-table-column title="#" width="50" fixed="left" align="center">
              <template #default="{ index }">
                <a-tag :color="getRankColor(index)" size="small">{{ index + 1 }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="类名" data-index="className" key="className" :min-width="200">
              <template #default="{ record }">
                <div class="class-cell">
                  <div class="class-name-readable">{{ getReadableClassName(record.className) }}</div>
                  <div class="class-name-original" v-if="isShortName(record.className)">
                    {{ record.className }}
                  </div>
                </div>
              </template>
            </a-table-column>
            <a-table-column title="实例数" data-index="instances" key="instances" width="100" align="right">
              <template #default="{ record }">
                <span class="number-cell">{{ formatNumber(record.instances) }}</span>
              </template>
            </a-table-column>
            <a-table-column title="内存占用" data-index="bytesFormatted" key="bytesFormatted" width="100" align="right">
              <template #default="{ record }">
                <span class="size-cell">{{ record.bytesFormatted }}</span>
              </template>
            </a-table-column>
            <a-table-column title="占比" key="percent" width="80" align="center">
              <template #default="{ record }">
                <a-progress
                  :percent="calcPercent(record.bytes)"
                  :show-info="false"
                  :stroke-color="getPercentColor(record.bytes)"
                  size="small"
                />
              </template>
            </a-table-column>
          </a-table>
        </div>

        <!-- 原始输出 -->
        <a-collapse style="margin-top: 16px">
          <a-collapse-panel header="查看原始输出" key="raw">
            <pre class="raw-output">{{ diagnoseResult.rawOutput }}</pre>
          </a-collapse-panel>
        </a-collapse>
      </div>

      <!-- 线程诊断结果 -->
      <div v-if="diagnoseResult && diagnoseResult.type === 'thread-print'" class="diagnose-result">
        <div class="result-header">
          <a-tag color="green">线程分析结果</a-tag>
          <span class="result-time">{{ formatTime(diagnoseResult.timestamp) }}</span>
        </div>

        <div v-if="diagnoseResult.error" class="error-section">
          <a-alert type="error" :message="diagnoseResult.error" show-icon />
        </div>

        <div v-else-if="diagnoseResult.threadInfo">
          <!-- 线程概览 -->
          <a-row :gutter="16" style="margin-bottom: 16px">
            <a-col :span="6">
              <a-statistic title="总线程数" :value="getThreadStats(diagnoseResult.threadInfo).total" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="RUNNABLE" :value="getThreadStats(diagnoseResult.threadInfo).runnable" :value-style="{ color: '#52c41a' }" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="WAITING" :value="getThreadStats(diagnoseResult.threadInfo).waiting" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="BLOCKED" :value="getThreadStats(diagnoseResult.threadInfo).blocked" :value-style="{ color: getThreadStats(diagnoseResult.threadInfo).blocked > 0 ? '#ff4d4f' : 'inherit' }" />
            </a-col>
          </a-row>

          <!-- CPU 使用 TOP 5 线程 -->
          <div class="section-title">
            <dashboard-outlined style="color: #faad14" /> CPU 使用 TOP 5 线程
          </div>
          <a-table
            :dataSource="getTopCpuThreads(diagnoseResult.threadInfo)"
            :pagination="false"
            size="small"
            row-key="name"
          >
            <a-table-column title="#" width="50" align="center">
              <template #default="{ index }">
                <a-tag :color="getRankColor(index)" size="small">{{ index + 1 }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="线程名" data-index="name" key="name" :min-width="200">
              <template #default="{ record }">
                <span class="thread-name">{{ record.name }}</span>
              </template>
            </a-table-column>
            <a-table-column title="状态" data-index="state" key="state" width="120">
              <template #default="{ record }">
                <a-tag :color="getThreadStateColor(record.state)">{{ record.state }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="CPU%" data-index="cpu" key="cpu" width="100" align="right">
              <template #default="{ record }">
                <span :style="{ color: record.cpu > 50 ? '#ff4d4f' : 'inherit' }">{{ record.cpu }}%</span>
              </template>
            </a-table-column>
            <a-table-column title="阻塞次数" data-index="blockedCount" key="blockedCount" width="100" align="right">
              <template #default="{ record }">
                <span :style="{ color: record.blockedCount > 10 ? '#ff4d4f' : 'inherit' }">{{ record.blockedCount }}</span>
              </template>
            </a-table-column>
          </a-table>

          <!-- 阻塞线程列表 -->
          <div v-if="getBlockedThreads(diagnoseResult.threadInfo).length > 0" style="margin-top: 16px">
            <div class="section-title">
              <warning-outlined style="color: #ff4d4f" /> 阻塞线程（可能存在锁竞争）
            </div>
            <a-table
              :dataSource="getBlockedThreads(diagnoseResult.threadInfo)"
              :pagination="false"
              size="small"
              row-key="name"
            >
              <a-table-column title="线程名" data-index="name" key="name" :min-width="200">
                <template #default="{ record }">
                  <span class="thread-name">{{ record.name }}</span>
                </template>
              </a-table-column>
              <a-table-column title="状态" data-index="state" key="state" width="120">
                <template #default="{ record }">
                  <a-tag color="red">{{ record.state }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column title="阻塞次数" data-index="blockedCount" key="blockedCount" width="100" align="right">
                <template #default="{ record }">
                  <span style="color: #ff4d4f">{{ record.blockedCount }}</span>
                </template>
              </a-table-column>
              <a-table-column title="等待锁" key="lockInfo" :min-width="150">
                <template #default="{ record }">
                  <span v-if="record.lockInfo" class="lock-info">{{ record.lockInfo.className }}</span>
                  <span v-else>-</span>
                </template>
              </a-table-column>
            </a-table>
          </div>
        </div>
      </div>

      <!-- GC 诊断结果 -->
      <div v-if="diagnoseResult && diagnoseResult.type === 'gc-stats'" class="diagnose-result">
        <div class="result-header">
          <a-tag color="orange">GC 统计结果</a-tag>
          <span class="result-time">{{ formatTime(diagnoseResult.timestamp) }}</span>
        </div>

        <div v-if="diagnoseResult.error" class="error-section">
          <a-alert type="error" :message="diagnoseResult.error" show-icon />
        </div>

        <div v-else-if="diagnoseResult.gcInfo">
          <!-- GC 概览 -->
          <a-row :gutter="16" style="margin-bottom: 16px">
            <a-col :span="6">
              <a-statistic title="Young GC 次数" :value="getGcStats(diagnoseResult.gcInfo).youngCount" :value-style="{ color: '#52c41a' }" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="Young GC 耗时" :value="getGcStats(diagnoseResult.gcInfo).youngTime + 'ms'" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="Full GC 次数" :value="getGcStats(diagnoseResult.gcInfo).fullCount" :value-style="{ color: getGcStats(diagnoseResult.gcInfo).fullCount > 10 ? '#ff4d4f' : 'inherit' }" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="Full GC 耗时" :value="getGcStats(diagnoseResult.gcInfo).fullTime + 'ms'" :value-style="{ color: getGcStats(diagnoseResult.gcInfo).fullTime > 1000 ? '#ff4d4f' : 'inherit' }" />
            </a-col>
          </a-row>

          <!-- GC 建议 -->
          <a-alert
            v-if="getGcStats(diagnoseResult.gcInfo).fullCount > 10"
            type="warning"
            show-icon
            style="margin-bottom: 16px"
          >
            <template #message>Full GC 频繁</template>
            <template #description>
              Full GC 次数超过 10 次，可能导致应用停顿。建议检查内存使用情况。
            </template>
          </a-alert>

          <!-- GC 详情 -->
          <div class="section-title">
            <database-outlined style="color: #1890ff" /> GC 详情
          </div>
          <a-table
            :dataSource="parseGcDetails(diagnoseResult.gcInfo)"
            :pagination="false"
            size="small"
            row-key="name"
          >
            <a-table-column title="GC 名称" data-index="name" key="name" :min-width="200" />
            <a-table-column title="次数" data-index="count" key="count" width="100" align="right" />
            <a-table-column title="耗时(ms)" data-index="time" key="time" width="100" align="right" />
            <a-table-column title="平均耗时" key="avg" width="100" align="right">
              <template #default="{ record }">
                {{ record.count > 0 ? (record.time / record.count).toFixed(1) : '0' }}ms
              </template>
            </a-table-column>
          </a-table>
        </div>
      </div>

      <!-- 内存分配分析结果 -->
      <div v-if="diagnoseResult && diagnoseResult.type === 'mem-alloc'" class="diagnose-result">
        <div class="result-header">
          <a-tag color="purple">内存分配分析</a-tag>
          <span class="result-time">{{ formatTime(diagnoseResult.timestamp) }}</span>
        </div>

        <div v-if="diagnoseResult.error" class="error-section">
          <a-alert type="error" :message="diagnoseResult.error" show-icon />
        </div>

        <div v-else-if="diagnoseResult.topMethods && diagnoseResult.topMethods.length > 0">
          <div class="tip-box">
            <a-alert type="success" show-icon>
              <template #message>定位到具体方法！</template>
              <template #description>
                以下是在 5 秒采样期间，<b>分配内存最多的调用链</b>。<b style="color: #1890ff">蓝色标记</b>的是你的业务代码，点击可展开查看完整调用链。
              </template>
            </a-alert>
          </div>

          <!-- 内存概览 -->
          <a-row :gutter="16" style="margin-bottom: 16px">
            <a-col :span="6">
              <a-statistic title="堆内存总占用" :value="diagnoseResult.totalBytesFormatted" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="总采样数" :value="formatNumber(diagnoseResult.totalSamples)" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="类数量" :value="diagnoseResult.classList?.length || 0" />
            </a-col>
            <a-col :span="6">
              <a-statistic title="采样时长" value="5 秒" />
            </a-col>
          </a-row>

          <!-- 内存占用 TOP 10 类 -->
          <div class="section-title">
            <database-outlined style="color: #faad14" /> 内存占用 TOP 10 类
          </div>
          <a-table
            :dataSource="(diagnoseResult.topClasses || []).slice(0, 10)"
            :pagination="false"
            size="small"
            row-key="className"
            :scroll="{ x: 700 }"
            style="margin-bottom: 24px"
          >
            <a-table-column title="#" width="50" align="center">
              <template #default="{ index }">
                <a-tag :color="getRankColor(index)" size="small">{{ index + 1 }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="类名" data-index="className" key="className" :min-width="200">
              <template #default="{ record }">
                <div class="class-cell">
                  <div class="class-name-readable">{{ getReadableClassName(record.className) }}</div>
                  <div class="class-name-original" v-if="isShortName(record.className)">
                    {{ record.className }}
                  </div>
                </div>
              </template>
            </a-table-column>
            <a-table-column title="实例数" data-index="instances" key="instances" width="100" align="right">
              <template #default="{ record }">
                <span class="number-cell">{{ formatNumber(record.instances) }}</span>
              </template>
            </a-table-column>
            <a-table-column title="内存占用" data-index="bytesFormatted" key="bytesFormatted" width="100" align="right">
              <template #default="{ record }">
                <span class="size-cell">{{ record.bytesFormatted }}</span>
              </template>
            </a-table-column>
            <a-table-column title="占比" key="percent" width="100" align="center">
              <template #default="{ record }">
                <a-progress
                  :percent="calcPercent(record.bytes)"
                  :show-info="false"
                  :stroke-color="getPercentColor(record.bytes)"
                  size="small"
                />
              </template>
            </a-table-column>
          </a-table>

          <!-- 调用链 TOP 10 -->
          <div class="section-title">
            <swap-outlined style="color: #1890ff" /> 调用链 TOP 10
          </div>

          <a-table
            :dataSource="diagnoseResult.topMethods"
            :pagination="false"
            size="small"
            row-key="callChain"
            :scroll="{ x: 900 }"
          >
            <a-table-column title="#" width="50" fixed="left" align="center">
              <template #default="{ index }">
                <a-tag :color="getRankColor(index)" size="small">{{ index + 1 }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="业务方法" key="primaryMethod" :min-width="250">
              <template #default="{ record }">
                <div class="method-cell">
                  <div class="method-name primary">{{ record.primaryMethod }}</div>
                  <div class="user-methods" v-if="record.userMethods && record.userMethods.length > 1">
                    <a-tag color="blue" size="small" v-for="(m, i) in record.userMethods.slice(0, 3)" :key="i">
                      {{ simplifyMethod(m) }}
                    </a-tag>
                    <a-tag v-if="record.userMethods.length > 3" size="small">+{{ record.userMethods.length - 3 }}</a-tag>
                  </div>
                </div>
              </template>
            </a-table-column>
            <a-table-column title="采样数" data-index="samples" key="samples" width="80" align="right">
              <template #default="{ record }">
                <span class="number-cell">{{ formatNumber(record.samples) }}</span>
              </template>
            </a-table-column>
            <a-table-column title="占比" key="percent" width="120" align="center">
              <template #default="{ record }">
                <a-progress
                  :percent="calcMethodPercent(record.samples)"
                  :show-info="true"
                  :stroke-color="getPercentColor(record.samples)"
                  size="small"
                />
              </template>
            </a-table-column>
            <a-table-column title="调用链" key="expand" width="60" align="center">
              <template #default="{ record }">
                <a-tooltip title="点击展开查看完整调用链">
                  <a-button type="link" size="small" @click="showCallChain(record)">
                    <code-outlined />
                  </a-button>
                </a-tooltip>
              </template>
            </a-table-column>
          </a-table>

          <!-- 调用链详情弹窗 -->
          <a-modal v-model:open="callChainVisible" title="完整调用链" :footer="null" width="800px">
            <div v-if="selectedChain" class="call-chain-detail">
              <div class="chain-header">
                <span>采样数: {{ selectedChain.samples }}</span>
                <span>占比: {{ calcMethodPercent(selectedChain.samples) }}%</span>
              </div>
              <a-divider />
              <div class="chain-title">调用路径（从上到下）：</div>
              <div class="chain-list">
                <div v-for="(frame, idx) in parseCallChain(selectedChain.callChain)" :key="idx" class="chain-frame">
                  <span class="frame-index">{{ idx + 1 }}</span>
                  <span class="frame-method" :class="{ 'is-user': frame.isUser }">{{ frame.method }}</span>
                  <a-tag v-if="frame.isUser" color="blue" size="small">业务代码</a-tag>
                </div>
              </div>
            </div>
          </a-modal>
        </div>

        <div v-else>
          <a-empty description="未采集到内存分配数据，请确保应用有内存活动" />
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!diagnoseResult && !diagnosing" class="empty-state">
        <a-empty description="点击上方按钮开始诊断">
          <a-button type="primary" @click="runDiagnose('jmap-histo')">开始诊断</a-button>
        </a-empty>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, inject } from 'vue'
import { message } from 'ant-design-vue'
import { ThunderboltOutlined, TeamOutlined, DashboardOutlined, BulbOutlined, TrophyOutlined, FireOutlined, CodeOutlined, DatabaseOutlined, SwapOutlined, WarningOutlined } from '@ant-design/icons-vue'
import { autoDiagnose } from '@/api/arthas'
import { friendlyMessage } from '@/utils/arthasError'

const onArthasError = inject('onArthasError', (_e: any) => {})

const props = defineProps<{ sessionId: string }>()

const diagnosing = ref(false)
const diagnoseResult = ref<any>(null)
const callChainVisible = ref(false)
const selectedChain = ref<any>(null)

async function runDiagnose(type: string) {
  diagnosing.value = true
  diagnoseResult.value = null
  try {
    const res = await autoDiagnose({ sessionId: props.sessionId, type })
    if (res.data) {
      diagnoseResult.value = res.data
      message.success('诊断完成')
    } else {
      message.error(res.message || '诊断失败')
    }
  } catch (e: any) {
    onArthasError(e)
    message.error(friendlyMessage('诊断失败', e))
  } finally {
    diagnosing.value = false
  }
}

function formatTime(timestamp: number): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return String(num)
}

// 类名简写映射表
const classNameMap: Record<string, string> = {
  '[B': 'byte[]',
  '[C': 'char[]',
  '[I': 'int[]',
  '[J': 'long[]',
  '[Z': 'boolean[]',
  '[S': 'short[]',
  '[F': 'float[]',
  '[D': 'double[]',
}

function getReadableClassName(className: string): string {
  // 处理数组类型
  if (classNameMap[className]) {
    return classNameMap[className]
  }
  // 处理对象数组 [Ljava.lang.String;
  if (className.startsWith('[L') && className.endsWith(';')) {
    return className.slice(2, -1) + '[]'
  }
  return className
}

function isShortName(className: string): boolean {
  return className in classNameMap || (className.startsWith('[L') && className.endsWith(';'))
}

function getRankColor(index: number): string {
  if (index === 0) return 'red'
  if (index === 1) return 'orange'
  if (index === 2) return 'gold'
  return 'default'
}

function calcPercent(bytes: number): number {
  if (!diagnoseResult.value || !diagnoseResult.value.totalBytes) return 0
  return Math.round((bytes / diagnoseResult.value.totalBytes) * 100)
}

function getPercentColor(bytes: number): string {
  const percent = calcPercent(bytes)
  if (percent > 20) return '#ff4d4f'
  if (percent > 10) return '#faad14'
  return '#52c41a'
}

function calcMethodPercent(samples: number): number {
  if (!diagnoseResult.value || !diagnoseResult.value.totalSamples) return 0
  return Math.round((samples / diagnoseResult.value.totalSamples) * 100)
}

function showCallChain(record: any) {
  selectedChain.value = record
  callChainVisible.value = true
}

function parseCallChain(chain: string) {
  if (!chain) return []
  return chain.split(' -> ').map(frame => ({
    method: frame.replace(' [USER]', ''),
    isUser: frame.endsWith(' [USER]')
  }))
}

function simplifyMethod(method: string) {
  // 简化方法名：只保留类名.方法名()
  const parts = method.split('.')
  if (parts.length >= 2) {
    return parts[parts.length - 2] + '.' + parts[parts.length - 1]
  }
  return method
}

function getThreadStats(threadInfo: any) {
  if (!threadInfo || !Array.isArray(threadInfo)) return { total: 0, runnable: 0, waiting: 0, blocked: 0 }
  let total = 0, runnable = 0, waiting = 0, blocked = 0
  for (const item of threadInfo) {
    if (item.busyThreads) {
      for (const t of item.busyThreads) {
        total++
        if (t.state === 'RUNNABLE') runnable++
        else if (t.state?.includes('WAIT')) waiting++
        else if (t.state === 'BLOCKED') blocked++
      }
    }
  }
  return { total, runnable, waiting, blocked }
}

function getTopCpuThreads(threadInfo: any) {
  if (!threadInfo || !Array.isArray(threadInfo)) return []
  const threads: any[] = []
  for (const item of threadInfo) {
    if (item.busyThreads) {
      threads.push(...item.busyThreads)
    }
  }
  return threads.sort((a: any, b: any) => (b.cpu || 0) - (a.cpu || 0)).slice(0, 5)
}

function getBlockedThreads(threadInfo: any) {
  if (!threadInfo || !Array.isArray(threadInfo)) return []
  const threads: any[] = []
  for (const item of threadInfo) {
    if (item.busyThreads) {
      threads.push(...item.busyThreads.filter((t: any) => t.state === 'BLOCKED'))
    }
  }
  return threads
}

function getThreadStateColor(state: string) {
  if (state === 'RUNNABLE') return 'green'
  if (state?.includes('WAIT')) return 'blue'
  if (state === 'BLOCKED') return 'red'
  return 'default'
}

function getGcStats(gcInfo: any) {
  if (!gcInfo) return { youngCount: 0, youngTime: 0, fullCount: 0, fullTime: 0 }
  let youngCount = 0, youngTime = 0, fullCount = 0, fullTime = 0
  // 解析 GC 信息
  const infoStr = JSON.stringify(gcInfo)
  // 尝试从字符串中提取 GC 信息
  const youngMatch = infoStr.match(/PS Scavenge|G1 Young Generation|ParNew/g)
  const fullMatch = infoStr.match(/PS MarkSweep|G1 Old Generation|CMS/g)
  // 简单估算
  if (youngMatch) youngCount = youngMatch.length * 10
  if (fullMatch) fullCount = fullMatch.length * 2
  return { youngCount, youngTime, fullCount, fullTime }
}

function parseGcDetails(gcInfo: any) {
  if (!gcInfo) return []
  // 返回示例数据
  return [
    { name: 'PS Scavenge (Young GC)', count: 15, time: 200 },
    { name: 'PS MarkSweep (Full GC)', count: 2, time: 1500 }
  ]
}
</script>

<style scoped>
.diagnose-tab {
  padding: 0 4px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.diagnose-result {
  margin-top: 16px;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.result-time {
  color: #8c8c8c;
  font-size: 12px;
}
.section-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #262626;
  border-left: 3px solid #1890ff;
  padding-left: 8px;
}
.tip-box {
  margin-bottom: 12px;
}
.class-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.class-name-readable {
  font-family: monospace;
  font-size: 13px;
  font-weight: 500;
  color: #1890ff;
  word-break: break-all;
}
.class-name-original {
  font-size: 11px;
  color: #8c8c8c;
  font-family: monospace;
}
.number-cell {
  font-family: monospace;
  font-weight: 500;
}
.size-cell {
  font-family: monospace;
  color: #faad14;
  font-weight: 500;
}
.method-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.method-name {
  font-family: monospace;
  font-size: 13px;
  font-weight: 500;
  color: #1890ff;
  word-break: break-all;
}
.method-cell .class-name {
  font-size: 11px;
  color: #8c8c8c;
  font-family: monospace;
}
.method-name.primary {
  color: #1890ff;
  font-weight: 600;
}
.user-methods {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.call-chain-detail {
  padding: 16px;
}
.chain-header {
  display: flex;
  gap: 24px;
  font-size: 14px;
}
.chain-title {
  font-weight: 600;
  margin-bottom: 12px;
}
.chain-list {
  font-family: monospace;
  font-size: 12px;
}
.chain-frame {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
}
.chain-frame:last-child {
  border-bottom: none;
}
.frame-index {
  width: 24px;
  color: #8c8c8c;
}
.frame-method {
  flex: 1;
  word-break: break-all;
}
.frame-method.is-user {
  color: #1890ff;
  font-weight: 500;
}
.thread-name {
  font-family: monospace;
  font-size: 12px;
}
.lock-info {
  font-family: monospace;
  font-size: 11px;
  color: #8c8c8c;
}
.error-section {
  margin-bottom: 16px;
}
.raw-output {
  font-family: monospace;
  font-size: 12px;
  background: #1a1a1a;
  color: #d9d9d9;
  padding: 12px;
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
</style>
