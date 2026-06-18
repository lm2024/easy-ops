<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="6">
        <a-statistic title="节点总数" :value="stats.totalNodes" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="在线节点" :value="stats.onlineNodes" value-style="color: #52c41a" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="项目数量" :value="stats.totalProjects" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="告警数量" :value="stats.alarmCount" value-style="color: #ff4d4f" />
      </a-col>
    </a-row>

    <a-row :gutter="16" style="margin-top: 16px">
      <a-col :span="12">
        <a-card title="节点状态分布">
          <div ref="nodeChartRef" style="height: 300px"></div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="资源使用">
          <div ref="resourceChartRef" style="height: 300px"></div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { getAlarms } from '../api/monitor'

const stats = ref({ totalNodes: 0, onlineNodes: 0, totalProjects: 0, alarmCount: 0 })
const nodeChartRef = ref<HTMLDivElement>()
const resourceChartRef = ref<HTMLDivElement>()

async function fetchData() {
  const [nodeRes, projRes, alarmRes] = await Promise.all([
    getNodes(), getProjects(), getAlarms()
  ])
  stats.value = {
    totalNodes: nodeRes.data.length,
    onlineNodes: nodeRes.data.filter((n: any) => n.status === 1).length,
    totalProjects: projRes.data.length,
    alarmCount: alarmRes.data.length
  }
}

onMounted(async () => {
  await fetchData()
  // Initialize charts (placeholder)
  if (nodeChartRef.value) {
    // echarts.init(nodeChartRef.value) - would init node status chart
  }
  if (resourceChartRef.value) {
    // echarts.init(resourceChartRef.value) - would init resource chart
  }
})
</script>
