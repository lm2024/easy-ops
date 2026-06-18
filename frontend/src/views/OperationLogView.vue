<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="module" style="width: 150px">
        <a-select-option value="">全部模块</a-select-option>
        <a-select-option value="NODE">节点</a-select-option>
        <a-select-option value="PROJECT">项目</a-select-option>
        <a-select-option value="DEPLOY">部署</a-select-option>
      </a-select>
      <a-button type="primary" @click="fetchLogs">查询</a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="logs"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-tag>{{ record.action }}</a-tag>
        </template>
        <template v-if="column.key === 'content'">
          <a-popover>
            <template #title>{{ record.module }}</template>
            <span>{{ record.content }}</span>
          </a-popover>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { OperationLogModel } from '../types'
import { getOperationLogs } from '../api/operationLog'

const logs = ref<OperationLogModel[]>([])
const loading = ref(false)
const module = ref('')
const pagination = ref({ current: 1, pageSize: 20 })

const columns = [
  { title: '用户ID', dataIndex: 'userId', key: 'userId' },
  { title: '模块', dataIndex: 'module', key: 'module' },
  { title: '操作', dataIndex: 'action', key: 'action' },
  { title: '内容', dataIndex: 'content', key: 'content' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: '时间', dataIndex: 'createTime', key: 'createTime' }
]

async function fetchLogs() {
  try {
    loading.value = true
    const res = await getOperationLogs(module.value, pagination.value.current, pagination.value.pageSize)
    logs.value = res.data
  } finally {
    loading.value = false
  }
}

function handleTableChange(pagination: any) {
  pagination.value.current = pagination.current
  fetchLogs()
}

onMounted(fetchLogs)
</script>
