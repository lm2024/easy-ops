<template>
  <div v-if="deploy">
    <a-card :bordered="false" style="border-radius: 8px; max-width: 800px">
      <template #title>
        <a-space>
          <rocket-outlined style="color: #fa541c" />
          <span style="font-weight: 600">部署详情</span>
        </a-space>
      </template>

      <a-descriptions bordered :column="2" size="small">
        <a-descriptions-item label="ID">{{ deploy.id }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-badge :status="statusMap[deploy.status]?.status" :text="statusMap[deploy.status]?.text" />
        </a-descriptions-item>
        <a-descriptions-item label="Jar包名">{{ deploy.jarName }}</a-descriptions-item>
        <a-descriptions-item label="节点ID">{{ deploy.nodeId }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ fmtTime(deploy.startTime) }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ deploy.endTime ? fmtTime(deploy.endTime) : '-' }}</a-descriptions-item>
        <a-descriptions-item label="日志" :span="2">
          <pre class="log-pre">{{ deploy.log || '-' }}</pre>
        </a-descriptions-item>
      </a-descriptions>

      <a-space style="margin-top: 24px">
        <a-button @click="$router.push('/deploy')">
          <arrow-left-outlined /> 返回列表
        </a-button>
      </a-space>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getDeployRecord } from '../api/deploy'
import { RocketOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const deploy = ref<any>()

function fmtTime(ts: any): string {
  if (!ts) return '-'
  const d = new Date(typeof ts === 'string' ? Number(ts) : ts)
  if (isNaN(d.getTime())) return String(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const statusMap: Record<number, { status: string; text: string }> = {
  0: { status: 'processing', text: '⏳ 进行中' },
  1: { status: 'success', text: '✅ 部署成功' },
  2: { status: 'error', text: '❌ 部署失败' },
  3: { status: 'default', text: '⛔ 已取消' },
  4: { status: 'success', text: '↩️✅ 回滚成功' }
}

onMounted(async () => {
  const id = Number(route.params.id)
  const res = await getDeployRecord(id)
  deploy.value = res.data
})
</script>

<style scoped>
.log-pre {
  margin: 0;
  white-space: pre-wrap;
  background: var(--eo-code-bg);
  color: var(--eo-code-text);
  border: 1px solid var(--eo-border);
  padding: 8px;
  border-radius: 8px;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
}
</style>
