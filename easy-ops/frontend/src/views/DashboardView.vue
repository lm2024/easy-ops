<template>
  <div class="dashboard-page">
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-card class="stat-card" :bordered="false">
          <a-statistic title="节点总数" :value="stats.totalNodes">
            <template #prefix>
              <cluster-outlined style="color: #e8ff59" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card stat-card-success" :bordered="false">
          <a-statistic title="在线节点" :value="stats.onlineNodes">
            <template #prefix>
              <check-circle-outlined style="color: #52c41a" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card stat-card-info" :bordered="false">
          <a-statistic title="项目数量" :value="stats.totalProjects">
            <template #prefix>
              <folder-open-outlined style="color: #722ed1" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card stat-card-danger" :bordered="false">
          <a-statistic title="告警数量" :value="stats.alarmCount">
            <template #prefix>
              <alert-outlined style="color: #ff4d4f" />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="16" class="chart-row">
      <a-col :span="16">
        <a-card title="节点状态" :bordered="false" class="chart-card">
          <div ref="nodeChartRef" class="chart-box"></div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="系统概览" :bordered="false" class="chart-card">
          <div ref="resourceChartRef" class="chart-box"></div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { getAlarms } from '../api/monitor'
import {
  ClusterOutlined,
  CheckCircleOutlined,
  FolderOpenOutlined,
  AlertOutlined
} from '@ant-design/icons-vue'

const stats = ref({ totalNodes: 0, onlineNodes: 0, totalProjects: 0, alarmCount: 0 })
const nodeChartRef = ref<HTMLDivElement>()
const resourceChartRef = ref<HTMLDivElement>()
let nodeChart: echarts.ECharts | null = null
let resourceChart: echarts.ECharts | null = null

async function fetchData() {
  const [nodeRes, projRes, alarmRes] = await Promise.all([
    getNodes(), getProjects(), getAlarms()
  ])
  const nodes = nodeRes.data.list || []
  stats.value = {
    totalNodes: nodes.length,
    onlineNodes: nodes.filter((n: any) => n.status === 1).length,
    totalProjects: (projRes.data.list || []).length,
    alarmCount: (alarmRes.data.list || []).length
  }
}

function initCharts() {
  if (nodeChartRef.value) {
    nodeChart = echarts.init(nodeChartRef.value)
    nodeChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['在线', '离线'], textStyle: { color: '#a1a1aa' } },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'], axisLabel: { color: '#a1a1aa' } },
      yAxis: { type: 'value', axisLabel: { color: '#a1a1aa' } },
      series: [
        { name: '在线', type: 'line', smooth: true, data: [12, 11, 13, 12, 14, 13, 15], areaStyle: { opacity: 0.15 }, itemStyle: { color: '#52c41a' } },
        { name: '离线', type: 'line', smooth: true, data: [1, 2, 0, 1, 0, 2, 1], areaStyle: { opacity: 0.15 }, itemStyle: { color: '#ff4d4f' } }
      ]
    })
  }

  if (resourceChartRef.value) {
    resourceChart = echarts.init(resourceChartRef.value)
    resourceChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: '5%', textStyle: { color: '#a1a1aa' } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
        data: [
          { value: stats.value.onlineNodes, name: '在线', itemStyle: { color: '#52c41a' } },
          { value: Math.max(0, stats.value.totalNodes - stats.value.onlineNodes), name: '离线', itemStyle: { color: '#ff4d4f' } }
        ]
      }]
    })
  }
}

function handleResize() {
  nodeChart?.resize()
  resourceChart?.resize()
}

onMounted(async () => {
  await fetchData()
  await nextTick()
  initCharts()
  setTimeout(handleResize, 100)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  nodeChart?.dispose()
  resourceChart?.dispose()
})
</script>

<style scoped>
.dashboard-page {
  min-height: 100%;
  padding-bottom: 24px;
}
.chart-row {
  margin-top: 0;
}
.stat-card {
  border-radius: 12px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.stat-card :deep(.ant-statistic-title) {
  color: #8c8c8c;
  font-size: 14px;
}
.stat-card :deep(.ant-statistic-content) {
  font-size: 28px;
  font-weight: 600;
}
.chart-card {
  border-radius: 12px;
  margin-bottom: 16px;
}
.chart-card :deep(.ant-card-head) {
  border-bottom: 1px solid #f0f0f0;
  min-height: 48px;
}
.chart-card :deep(.ant-card-head-title) {
  font-weight: 600;
}
.chart-box {
  width: 100%;
  height: 360px;
  min-height: 360px;
}
</style>
