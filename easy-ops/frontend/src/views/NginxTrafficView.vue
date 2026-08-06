<template>
  <div class="nginx-traffic-view">
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <line-chart-outlined style="color: #1677ff" />
          <span style="font-weight: 600">Nginx 流量监控</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select
            v-model:value="selectedSourceIds"
            mode="multiple"
            allow-clear
            placeholder="全部日志源"
            style="min-width: 220px"
            :options="sourceOptions"
          />
          <a-select v-model:value="rangePreset" style="width: 120px" @change="onRangePresetChange">
            <a-select-option value="last10m">近10分钟</a-select-option>
            <a-select-option value="last30m">近30分钟</a-select-option>
            <a-select-option value="last1h">近1小时</a-select-option>
            <a-select-option value="last2h">近2小时</a-select-option>
            <a-select-option value="last5h">近5小时</a-select-option>
            <a-select-option value="last8h">近8小时</a-select-option>
            <a-select-option value="today">今天</a-select-option>
            <a-select-option value="yesterday">昨天</a-select-option>
            <a-select-option value="custom">自定义</a-select-option>
          </a-select>
          <a-range-picker
            v-if="rangePreset === 'custom'"
            v-model:value="customRange"
            :disabled-date="disabledFutureDate"
            style="width: 240px"
            @change="refreshAll"
          />
          <a-button :loading="loading" @click="refreshAll">
            <reload-outlined /> 刷新
          </a-button>
        </a-space>
      </template>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="overview" tab="实时概览" />
        <a-tab-pane key="rank" tab="排名分析" />
        <a-tab-pane key="slow" tab="慢接口" />
        <a-tab-pane key="config" tab="日志源配置" />
      </a-tabs>

      <!-- 实时概览 -->
      <div v-if="activeTab === 'overview'" class="tab-body">
        <div class="overview-hint">
          统计区间：{{ rangeLabel }}（{{ fmtTime(rangeStart) }} ~ {{ fmtTime(rangeEnd) }}）
          · 历史数据保留 {{ retainDays }} 天
          <span v-if="trendGranularity === 'day'"> · 趋势按天汇总</span>
        </div>
        <a-row :gutter="[16, 16]" style="margin-bottom: 16px">
          <a-col :xs="12" :sm="8" :md="6" :lg="4">
            <a-statistic :title="`${rangeLabel}总请求`" :value="overview.totalRequests || 0" />
          </a-col>
          <a-col :xs="12" :sm="8" :md="6" :lg="4">
            <a-tooltip title="区间内总请求数 ÷ 总秒数；HTTP 场景 RPS 等同 QPS">
              <a-statistic title="平均 RPS" :value="overview.avgRps ?? overview.qps ?? 0" :precision="2" suffix="req/s" />
            </a-tooltip>
          </a-col>
          <a-col :xs="12" :sm="8" :md="6" :lg="4">
            <a-tooltip title="区间内单分钟桶峰值（每分钟请求数 ÷ 60）">
              <a-statistic title="峰值 RPS" :value="overview.peakRps || 0" :precision="2" suffix="req/s" />
            </a-tooltip>
          </a-col>
          <a-col :xs="12" :sm="8" :md="6" :lg="4">
            <a-statistic title="4xx 错误" :value="overview.status4xx || 0" value-style="color: #faad14" />
          </a-col>
          <a-col :xs="12" :sm="8" :md="6" :lg="4">
            <a-statistic title="5xx 错误" :value="overview.status5xx || 0" value-style="color: #ff4d4f" />
          </a-col>
          <a-col :xs="12" :sm="8" :md="6" :lg="4">
            <a-statistic title="慢请求" :value="overview.slowCount || 0" value-style="color: #722ed1" />
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="16">
            <a-card size="small" :title="trendGranularity === 'day' ? '请求趋势（按天）' : '请求趋势（按分钟）'">
              <div v-if="trend.length === 0" class="empty-hint">该时间区间暂无数据</div>
              <div ref="trendChartRef" class="trend-chart" :style="{ visibility: trend.length ? 'visible' : 'hidden', height: trend.length ? '320px' : '0' }" />
            </a-card>
          </a-col>
          <a-col :span="8">
            <a-card size="small">
              <template #title>
                Top {{ OVERVIEW_TOP_N }} 接口
                <span class="card-subtitle">· 按请求数 ↓</span>
              </template>
              <a-table
                :columns="overviewUriColumns"
                :data-source="overviewTopUri"
                :pagination="false"
                size="small"
                row-key="uri"
                :scroll="{ y: 420 }"
              />
            </a-card>
          </a-col>
        </a-row>
      </div>

      <!-- 排名分析 -->
      <div v-if="activeTab === 'rank'" class="tab-body">
        <div class="overview-hint">排序规则：IP / 接口 / 交叉表按「请求数」降序；默认每页 20 条，最多查询 100 条/页</div>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-card size="small">
              <template #title>Top IP <span class="card-subtitle">· 按请求数 ↓</span></template>
              <template #extra>
                <a-input-search v-model:value="ipKeyword" placeholder="筛选 IP" style="width: 160px" @search="onRankIpSearch" />
              </template>
              <a-table
                :columns="ipColumns"
                :data-source="rankIp"
                :pagination="rankIpPagination"
                size="small"
                row-key="clientIp"
              />
            </a-card>
          </a-col>
          <a-col :span="12">
            <a-card size="small">
              <template #title>Top 接口 <span class="card-subtitle">· 按请求数 ↓</span></template>
              <template #extra>
                <a-input-search v-model:value="uriKeyword" placeholder="筛选接口" style="width: 160px" @search="onRankUriSearch" />
              </template>
              <a-table
                :columns="uriColumns"
                :data-source="rankUri"
                :pagination="rankUriPagination"
                size="small"
                row-key="uri"
              />
            </a-card>
          </a-col>
        </a-row>
        <a-card size="small" style="margin-top: 16px">
          <template #title>IP + 接口交叉排名 <span class="card-subtitle">· 按请求数 ↓</span></template>
          <a-space style="margin-bottom: 12px">
            <a-input v-model:value="crossIp" placeholder="IP 关键词" style="width: 180px" />
            <a-input v-model:value="crossUri" placeholder="接口关键词" style="width: 220px" />
            <a-button type="primary" @click="onRankIpUriSearch">查询</a-button>
          </a-space>
          <a-table
            :columns="crossColumns"
            :data-source="rankIpUri"
            :pagination="rankIpUriPagination"
            size="small"
            :row-key="crossRowKey"
          />
        </a-card>
      </div>

      <!-- 慢接口 -->
      <div v-if="activeTab === 'slow'" class="tab-body">
        <div class="overview-hint">
          慢请求阈值：<strong>{{ slowThresholdHint }}</strong> 秒（在「日志源配置」编辑日志源修改）
          · 仅展示耗时 ≥ 阈值的接口 · 排序：慢请求次数 ↓
        </div>
        <a-table
          :columns="slowColumns"
          :data-source="rankSlow"
          :pagination="rankSlowPagination"
          size="middle"
          row-key="uri"
        />
      </div>

      <!-- 日志源配置 -->
      <div v-if="activeTab === 'config'" class="tab-body">
        <a-button type="primary" style="margin-bottom: 12px" @click="openSourceModal()">
          <plus-outlined /> 新增日志源
        </a-button>
        <a-table :columns="sourceColumns" :data-source="sources" :pagination="false" row-key="id" size="middle">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'enabled'">
              <a-tag :color="record.enabled === 1 ? 'green' : 'default'">{{ record.enabled === 1 ? '启用' : '停用' }}</a-tag>
            </template>
            <template v-if="column.key === 'status'">
              <span v-if="record.lastReportTime">{{ fmtTime(record.lastReportTime) }}</span>
              <span v-else style="color: #999">未上报</span>
              <div v-if="record.lastError" style="color: #ff4d4f; font-size: 12px">{{ record.lastError }}</div>
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="openSourceModal(record)">编辑</a-button>
                <a-popconfirm title="确认删除？" @confirm="handleDeleteSource(record.id)">
                  <a-button type="link" danger size="small">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </a-card>

    <a-modal
      v-model:open="sourceModalVisible"
      :title="editingSource?.id ? '编辑日志源' : '新增日志源'"
      width="720px"
      @ok="handleSaveSource"
    >
      <a-tabs v-model:activeKey="sourceModalTab">
        <a-tab-pane key="basic" tab="基本配置" />
        <a-tab-pane key="alarm" tab="告警规则" />
      </a-tabs>
      <div v-show="sourceModalTab === 'basic'">
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="editingSource.name" placeholder="如：生产 Nginx access.log" />
        </a-form-item>
        <a-form-item label="关联节点" required>
          <a-select v-model:value="editingSource.nodeId" placeholder="选择 Agent 节点" :options="nodeOptions" />
        </a-form-item>
        <a-form-item label="日志绝对路径" required>
          <a-input v-model:value="editingSource.logPath" placeholder="/var/log/nginx/access.log" />
        </a-form-item>
        <a-form-item label="慢请求阈值（秒）">
          <a-input-number v-model:value="editingSource.slowThresholdSec" :min="0.01" :max="300" :step="0.5" style="width: 100%" />
          <div class="form-hint">Nginx access.log 中 request_time 超过此值计为慢请求</div>
        </a-form-item>
        <a-form-item label="启用">
          <a-switch :checked="editingSource.enabled === 1" @change="(v: boolean) => editingSource.enabled = v ? 1 : 0" />
        </a-form-item>
      </a-form>
      </div>
      <div v-show="sourceModalTab === 'alarm'" class="alarm-rules-panel">
        <a-alert
          v-if="!editingSource.id"
          type="warning"
          show-icon
          style="margin-bottom: 12px"
          message="请先保存基本配置，再配置告警规则"
        />
        <template v-else>
        <a-alert
          type="info"
          show-icon
          style="margin-bottom: 12px"
          message="触发后通过右上角通知铃铛推送；慢请求判定沿用基本配置中的慢请求阈值"
        />
        <a-table
          :columns="alarmRuleColumns"
          :data-source="alarmRules"
          :pagination="false"
          size="small"
          row-key="ruleType"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'ruleType'">
              {{ ALARM_RULE_LABELS[record.ruleType] || record.ruleType }}
            </template>
            <template v-if="column.key === 'enabled'">
              <a-switch
                :checked="record.enabled === 1"
                @change="(v: boolean) => record.enabled = v ? 1 : 0"
              />
            </template>
            <template v-if="column.key === 'windowMinutes'">
              <a-input-number v-model:value="record.windowMinutes" :min="1" :max="1440" style="width: 80px" />
            </template>
            <template v-if="column.key === 'threshold'">
              <a-input-number v-model:value="record.threshold" :min="1" style="width: 100px" />
            </template>
            <template v-if="column.key === 'level'">
              <a-select v-model:value="record.level" style="width: 110px">
                <a-select-option value="CRITICAL">严重</a-select-option>
                <a-select-option value="WARNING">警告</a-select-option>
                <a-select-option value="INFO">提示</a-select-option>
              </a-select>
            </template>
            <template v-if="column.key === 'cooldownMinutes'">
              <a-input-number v-model:value="record.cooldownMinutes" :min="1" :max="1440" style="width: 80px" />
            </template>
            <template v-if="column.key === 'requireAck'">
              <a-switch
                :checked="record.requireAck === 1"
                @change="(v: boolean) => record.requireAck = v ? 1 : 0"
              />
            </template>
          </template>
        </a-table>
        </template>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts'
import { LineChartOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { NginxAccessSourceModel, NginxTrafficAlarmRuleModel, NodeModel } from '../types'
import { getNodes } from '../api/node'
import dayjs, { type Dayjs } from 'dayjs'
import type { NginxTimeQuery } from '../api/nginxTraffic'
import {
  listNginxSources, saveNginxSource, deleteNginxSource,
  listNginxAlarmRules, saveNginxAlarmRules,
  getNginxOverview, getNginxTrend,
  getNginxRankIp, getNginxRankUri, getNginxRankIpUri, getNginxRankSlow
} from '../api/nginxTraffic'

type RangePreset = 'last10m' | 'last30m' | 'last1h' | 'last2h' | 'last5h' | 'last8h' | 'today' | 'yesterday' | 'custom'

const OVERVIEW_TOP_N = 30
const activeTab = ref('overview')
const loading = ref(false)
const rangePreset = ref<RangePreset>('today')
const customRange = ref<[Dayjs, Dayjs] | undefined>()
const retainDays = ref(7)
const trendGranularity = ref<'minute' | 'day'>('minute')
const rangeStart = ref(0)
const rangeEnd = ref(0)
const selectedSourceIds = ref<number[]>([])
const sources = ref<NginxAccessSourceModel[]>([])
const nodes = ref<NodeModel[]>([])

const overview = ref<Record<string, number>>({})
const trend = ref<Record<string, unknown>[]>([])
const rankIp = ref<Record<string, unknown>[]>([])
const rankUri = ref<Record<string, unknown>[]>([])
const rankIpUri = ref<Record<string, unknown>[]>([])
const rankSlow = ref<Record<string, unknown>[]>([])
const overviewTopUri = ref<Record<string, unknown>[]>([])
const trendChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null

const rankIpPage = ref(1)
const rankUriPage = ref(1)
const rankIpUriPage = ref(1)
const rankSlowPage = ref(1)
const rankPageSize = ref(20)
const rankIpTotal = ref(0)
const rankUriTotal = ref(0)
const rankIpUriTotal = ref(0)
const rankSlowTotal = ref(0)

const ipKeyword = ref('')
const uriKeyword = ref('')
const crossIp = ref('')
const crossUri = ref('')

const sourceModalVisible = ref(false)
const sourceModalTab = ref('basic')
const pendingSourceId = ref<number>()
const alarmRules = ref<NginxTrafficAlarmRuleModel[]>([])
const editingSource = reactive<NginxAccessSourceModel>({
  nodeId: 0,
  name: '',
  logPath: '',
  logFormat: 'main',
  enabled: 1,
  slowThresholdSec: 3,
  maxKeysPerMinute: 2000
})

const sourceOptions = computed(() =>
  sources.value.map(s => ({ label: s.name, value: s.id }))
)
const nodeOptions = computed(() =>
  nodes.value.map(n => ({ label: `${n.name} (${n.ip})`, value: n.id }))
)

/** 慢接口 Tab 提示用：取已启用日志源的最小阈值 */
const slowThresholdHint = computed(() => {
  const enabled = sources.value.filter(s => s.enabled === 1 && s.slowThresholdSec != null)
  if (!enabled.length) return 3
  return Math.min(...enabled.map(s => s.slowThresholdSec as number))
})

const sourceIdsParam = computed(() =>
  selectedSourceIds.value.length ? selectedSourceIds.value : undefined
)

function dayStart(daysAgo = 0) {
  return dayjs().subtract(daysAgo, 'day').startOf('day').valueOf()
}

function dayEnd(daysAgo = 0) {
  if (daysAgo === 0) return Date.now()
  return dayjs().subtract(daysAgo, 'day').endOf('day').valueOf()
}

const timeQuery = computed((): NginxTimeQuery => {
  const base: NginxTimeQuery = { sourceIds: sourceIdsParam.value }
  switch (rangePreset.value) {
    case 'last10m':
      return { ...base, windowMinutes: 10 }
    case 'last30m':
      return { ...base, windowMinutes: 30 }
    case 'last1h':
      return { ...base, windowMinutes: 60 }
    case 'last2h':
      return { ...base, windowMinutes: 120 }
    case 'last5h':
      return { ...base, windowMinutes: 300 }
    case 'last8h':
      return { ...base, windowMinutes: 480 }
    case 'today':
      return { ...base, startTime: dayStart(0), endTime: Date.now() }
    case 'yesterday':
      return { ...base, startTime: dayStart(1), endTime: dayEnd(1) }
    case 'custom':
      if (customRange.value?.[0] && customRange.value?.[1]) {
        return {
          ...base,
          startTime: customRange.value[0].startOf('day').valueOf(),
          endTime: customRange.value[1].endOf('day').valueOf()
        }
      }
      return { ...base, startTime: dayStart(0), endTime: Date.now() }
    default:
      return { ...base, windowMinutes: 60 }
  }
})

const rangeLabel = computed(() => {
  const map: Record<RangePreset, string> = {
    last10m: '近10分钟',
    last30m: '近30分钟',
    last1h: '近1小时',
    last2h: '近2小时',
    last5h: '近5小时',
    last8h: '近8小时',
    today: '今天',
    yesterday: '昨天',
    custom: '自定义'
  }
  return map[rangePreset.value]
})

function disabledFutureDate(current: Dayjs) {
  return current && current.endOf('day').valueOf() > Date.now()
}

function onRangePresetChange() {
  if (rangePreset.value === 'custom') {
    if (!customRange.value) {
      customRange.value = [dayjs().subtract(6, 'day'), dayjs()]
    }
  }
  resetRankPages()
  refreshAll()
}

function resetRankPages() {
  rankIpPage.value = 1
  rankUriPage.value = 1
  rankIpUriPage.value = 1
  rankSlowPage.value = 1
}

function rankNo(page: number, index: number) {
  return (page - 1) * rankPageSize.value + index + 1
}

function buildRankPagination(page: { value: number }, total: { value: number }, reload: () => void) {
  return computed(() => ({
    current: page.value,
    pageSize: rankPageSize.value,
    total: total.value,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50', '100'],
    showTotal: (t: number) => `共 ${t} 条`,
    onChange: (p: number, size: number) => {
      page.value = p
      rankPageSize.value = size
      reload()
    }
  }))
}

function onRankIpSearch() {
  rankIpPage.value = 1
  loadRankIp()
}

function onRankUriSearch() {
  rankUriPage.value = 1
  loadRankUri()
}

function onRankIpUriSearch() {
  rankIpUriPage.value = 1
  loadRankIpUri()
}

function disposeTrendChart() {
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
}

function fmtTrendLabel(ts: number) {
  if (trendGranularity.value === 'day') {
    const d = new Date(ts)
    return `${d.getMonth() + 1}/${d.getDate()}`
  }
  return fmtTimeShort(ts)
}

function fmtTimeShort(ts: number) {
  if (!ts) return '-'
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function renderTrendChart() {
  if (!trendChartRef.value || trend.value.length === 0) {
    disposeTrendChart()
    return
  }
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const labels = trend.value.map(row => fmtTrendLabel(row.bucketTime as number))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['请求数', '4xx', '5xx'], bottom: 0 },
    grid: { left: 48, right: 24, top: 24, bottom: 48 },
    xAxis: { type: 'category', data: labels, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '请求数',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.08 },
        data: trend.value.map(row => Number(row.requestCount || 0))
      },
      {
        name: '4xx',
        type: 'line',
        smooth: true,
        data: trend.value.map(row => Number(row.status4xx || 0)),
        itemStyle: { color: '#faad14' }
      },
      {
        name: '5xx',
        type: 'line',
        smooth: true,
        data: trend.value.map(row => Number(row.status5xx || 0)),
        itemStyle: { color: '#ff4d4f' }
      }
    ]
  }, true)
  trendChart.resize()
}

watch([trend, activeTab, trendGranularity, rangePreset], async () => {
  if (activeTab.value !== 'overview') return
  disposeTrendChart()
  await nextTick()
  renderTrendChart()
})

function handleResize() {
  trendChart?.resize()
}
const ipColumns = computed(() => [
  { title: '#', key: 'rank', width: 48, customRender: ({ index }: { index: number }) => rankNo(rankIpPage.value, index) },
  { title: 'IP', dataIndex: 'clientIp', key: 'clientIp' },
  { title: '请求数', dataIndex: 'requestCount', key: 'requestCount' },
  { title: '慢请求', dataIndex: 'slowCount', key: 'slowCount' },
  { title: '5xx', dataIndex: 'status5xx', key: 'status5xx' }
])
const uriColumns = computed(() => [
  { title: '#', key: 'rank', width: 48, customRender: ({ index }: { index: number }) => rankNo(rankUriPage.value, index) },
  { title: '接口', dataIndex: 'uri', key: 'uri', ellipsis: true },
  { title: '请求数', dataIndex: 'requestCount', key: 'requestCount' },
  { title: '平均耗时(ms)', dataIndex: 'avgRequestTimeMs', key: 'avgRequestTimeMs' },
  { title: '慢请求', dataIndex: 'slowCount', key: 'slowCount' }
])
const crossColumns = computed(() => [
  { title: '#', key: 'rank', width: 48, customRender: ({ index }: { index: number }) => rankNo(rankIpUriPage.value, index) },
  { title: 'IP', dataIndex: 'clientIp', key: 'clientIp' },
  { title: '方法', dataIndex: 'method', key: 'method', width: 80 },
  { title: '接口', dataIndex: 'uri', key: 'uri', ellipsis: true },
  { title: '请求数', dataIndex: 'requestCount', key: 'requestCount' },
  { title: '平均耗时(ms)', dataIndex: 'avgRequestTimeMs', key: 'avgRequestTimeMs' }
])
const slowColumns = computed(() => [
  { title: '#', key: 'rank', width: 48, customRender: ({ index }: { index: number }) => rankNo(rankSlowPage.value, index) },
  { title: '接口', dataIndex: 'uri', key: 'uri', ellipsis: true },
  { title: '慢请求次数', dataIndex: 'slowCount', key: 'slowCount' },
  { title: '总请求', dataIndex: 'requestCount', key: 'requestCount' },
  { title: '最大耗时(ms)', dataIndex: 'maxRequestTimeMs', key: 'maxRequestTimeMs' },
  { title: '平均耗时(ms)', dataIndex: 'avgRequestTimeMs', key: 'avgRequestTimeMs' }
])
const ALARM_RULE_LABELS: Record<string, string> = {
  IP_FREQ: '单IP访问过频',
  URI_FREQ: '单接口访问过频',
  STATUS_4XX: '4xx 错误过多',
  STATUS_5XX: '5xx 错误过多',
  SLOW: '慢请求过多'
}

const alarmRuleColumns = [
  { title: '规则', dataIndex: 'ruleType', key: 'ruleType' },
  { title: '启用', key: 'enabled', width: 70 },
  { title: '窗口(分)', key: 'windowMinutes', width: 100 },
  { title: '阈值(次)', key: 'threshold', width: 110 },
  { title: '级别', key: 'level', width: 120 },
  { title: '冷却(分)', key: 'cooldownMinutes', width: 100 },
  { title: '需确认', key: 'requireAck', width: 80 }
]

const sourceColumns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '节点ID', dataIndex: 'nodeId', key: 'nodeId', width: 90 },
  { title: '日志路径', dataIndex: 'logPath', key: 'logPath', ellipsis: true },
  { title: '慢请求阈值(秒)', dataIndex: 'slowThresholdSec', key: 'slowThresholdSec', width: 130 },
  { title: '状态', key: 'enabled', width: 80 },
  { title: '最近上报', key: 'status', width: 180 },
  { title: '操作', key: 'action', width: 140 }
]
const overviewUriColumns = [
  { title: '#', key: 'rank', width: 36, customRender: ({ index }: { index: number }) => index + 1 },
  { title: '接口', dataIndex: 'uri', key: 'uri', ellipsis: true },
  { title: '请求', dataIndex: 'requestCount', key: 'requestCount', width: 64 },
  { title: '均耗时', dataIndex: 'avgRequestTimeMs', key: 'avgRequestTimeMs', width: 64 }
]

function fmtTime(ts: number) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString()
}

function crossRowKey(row: Record<string, unknown>) {
  return `${row.clientIp}-${row.method}-${row.uri}`
}

async function loadSources() {
  const res = await listNginxSources()
  sources.value = res.data || []
}

async function loadOverviewTopUri() {
  const res = await getNginxRankUri(timeQuery.value, undefined, { page: 1, pageSize: OVERVIEW_TOP_N })
  overviewTopUri.value = res.data?.list || []
}

async function loadNodes() {
  const res = await getNodes(1, 200)
  nodes.value = res.data?.list || []
}

async function loadOverview() {
  const [ov, tr] = await Promise.all([
    getNginxOverview(timeQuery.value),
    getNginxTrend(timeQuery.value),
    loadOverviewTopUri()
  ])
  overview.value = (ov.data || {}) as Record<string, number>
  retainDays.value = Number(ov.data?.retainDays || 7)
  rangeStart.value = Number(ov.data?.startTime || timeQuery.value.startTime || 0)
  rangeEnd.value = Number(ov.data?.endTime || timeQuery.value.endTime || Date.now())
  const trendData = tr.data
  if (trendData && Array.isArray((trendData as unknown as Record<string, unknown>[]))) {
    trendGranularity.value = 'minute'
    trend.value = trendData as unknown as Record<string, unknown>[]
  } else {
    trendGranularity.value = trendData?.granularity || 'minute'
    trend.value = trendData?.points || []
  }
}

async function loadRankIp() {
  const res = await getNginxRankIp(timeQuery.value, ipKeyword.value || undefined, {
    page: rankIpPage.value,
    pageSize: rankPageSize.value
  })
  rankIp.value = res.data?.list || []
  rankIpTotal.value = res.data?.total || 0
}

async function loadRankUri() {
  const res = await getNginxRankUri(timeQuery.value, uriKeyword.value || undefined, {
    page: rankUriPage.value,
    pageSize: rankPageSize.value
  })
  rankUri.value = res.data?.list || []
  rankUriTotal.value = res.data?.total || 0
}

async function loadRankIpUri() {
  const res = await getNginxRankIpUri(
    timeQuery.value,
    crossIp.value || undefined,
    crossUri.value || undefined,
    { page: rankIpUriPage.value, pageSize: rankPageSize.value }
  )
  rankIpUri.value = res.data?.list || []
  rankIpUriTotal.value = res.data?.total || 0
}

async function loadRankSlow() {
  const res = await getNginxRankSlow(timeQuery.value, {
    page: rankSlowPage.value,
    pageSize: rankPageSize.value
  })
  rankSlow.value = res.data?.list || []
  rankSlowTotal.value = res.data?.total || 0
}

const rankIpPagination = buildRankPagination(rankIpPage, rankIpTotal, loadRankIp)
const rankUriPagination = buildRankPagination(rankUriPage, rankUriTotal, loadRankUri)
const rankIpUriPagination = buildRankPagination(rankIpUriPage, rankIpUriTotal, loadRankIpUri)
const rankSlowPagination = buildRankPagination(rankSlowPage, rankSlowTotal, loadRankSlow)

async function refreshAll() {
  loading.value = true
  try {
    await loadSources()
    await Promise.all([loadOverview(), loadRankIp(), loadRankUri(), loadRankIpUri(), loadRankSlow()])
  } catch (e) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function openSourceModal(record?: NginxAccessSourceModel) {
  sourceModalTab.value = 'basic'
  if (record) {
    Object.assign(editingSource, record)
    pendingSourceId.value = record.id
    loadAlarmRules(record.id!)
  } else {
    Object.assign(editingSource, {
      id: undefined,
      nodeId: nodes.value[0]?.id || 0,
      name: '',
      logPath: '/var/log/nginx/access.log',
      logFormat: 'main',
      enabled: 1,
      slowThresholdSec: 3,
      maxKeysPerMinute: 2000
    })
    pendingSourceId.value = undefined
    alarmRules.value = []
  }
  sourceModalVisible.value = true
}

async function loadAlarmRules(sourceId: number) {
  const res = await listNginxAlarmRules(sourceId)
  alarmRules.value = res.data || []
}

async function handleSaveSource() {
  if (!editingSource.name || !editingSource.nodeId || !editingSource.logPath) {
    message.warning('请填写完整信息')
    return
  }
  const saved = await saveNginxSource(editingSource)
  const sourceId = saved.data?.id || editingSource.id
  if (sourceId) {
    editingSource.id = sourceId
    pendingSourceId.value = sourceId
    if (alarmRules.value.length === 0) {
      await loadAlarmRules(sourceId)
    }
    if (sourceModalTab.value === 'alarm' || alarmRules.value.some(r => r.enabled === 1)) {
      await saveNginxAlarmRules(sourceId, alarmRules.value)
    }
  }
  message.success('保存成功')
  sourceModalVisible.value = false
  await refreshAll()
}

async function handleDeleteSource(id?: number) {
  if (!id) return
  await deleteNginxSource(id)
  message.success('已删除')
  await refreshAll()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadNodes()
  await refreshAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeTrendChart()
})
</script>

<style scoped>
.tab-body {
  margin-top: 16px;
}
.empty-hint {
  color: #999;
  padding: 24px;
  text-align: center;
}
.overview-hint {
  color: #8c8c8c;
  font-size: 12px;
  margin-bottom: 12px;
}
.trend-chart {
  width: 100%;
}
.card-subtitle {
  color: #8c8c8c;
  font-size: 12px;
  font-weight: normal;
}
.form-hint {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 4px;
}
.alarm-rules-panel {
  margin-top: 8px;
}
</style>
