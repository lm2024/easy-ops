<template>
  <div v-if="deploy">
    <a-descriptions title="部署详情" bordered :column="2">
      <a-descriptions-item label="ID">{{ deploy.id }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-badge
          :status="statusMap[deploy.status]?.status"
          :text="statusMap[deploy.status]?.text"
        />
      </a-descriptions-item>
      <a-descriptions-item label="Jar包名">{{ deploy.jarName }}</a-descriptions-item>
      <a-descriptions-item label="节点ID">{{ deploy.nodeId }}</a-descriptions-item>
      <a-descriptions-item label="开始时间">{{ deploy.startTime }}</a-descriptions-item>
      <a-descriptions-item label="结束时间">{{ deploy.endTime }}</a-descriptions-item>
      <a-descriptions-item label="日志" :span="2">
        <pre style="margin: 0; white-space: pre-wrap; background: #f5f5f5; padding: 8px; border-radius: 4px;">{{ deploy.log }}</pre>
      </a-descriptions-item>
    </a-descriptions>
    <a-space style="margin-top: 16px">
      <a-button @click="$router.push('/deploy')">返回列表</a-button>
    </a-space>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { DeployModel } from '../types'
import { getDeployRecord } from '../api/deploy'

const route = useRoute()
const deploy = ref<DeployModel>()

const statusMap: Record<number, { status: string; text: string }> = {
  0: { status: 'processing', text: '进行中' },
  1: { status: 'success', text: '成功' },
  2: { status: 'error', text: '失败' },
  3: { status: 'default', text: '回滚' }
}

onMounted(async () => {
  const id = Number(route.params.id)
  const res = await getDeployRecord(id)
  deploy.value = res.data
})
</script>

<style scoped>
pre {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow-y: auto;
}
</style>
