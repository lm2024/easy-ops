<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <audit-outlined style="color: #595959" />
          <span style="font-weight: 600">操作审计</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select v-model:value="module" style="width: 150px" placeholder="模块筛选">
            <a-select-option value="">全部模块</a-select-option>
            <a-select-option value="NODE">节点</a-select-option>
            <a-select-option value="PROJECT">项目</a-select-option>
            <a-select-option value="DEPLOY">部署</a-select-option>
          </a-select>
          <a-button type="primary" @click="fetchLogs">
            <search-outlined /> 查询
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="logs"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-tag>{{ record.action }}</a-tag>
          </template>
          <template v-if="column.key === 'content'">
            <a-tooltip :title="record.content">
              <span style="max-width: 300px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                {{ record.content }}
              </span>
            </a-tooltip>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { OperationLogModel } from '../types'
import { getOperationLogs } from '../api/operationLog'
import {
  SearchOutlined,
  AuditOutlined
} from '@ant-design/icons-vue'

const logs = ref<OperationLogModel[]>([])
const loading = ref(false)
const module = ref('')
const pagination = ref({ current: 1, pageSize: 20, total: 0 })

const columns = [
  { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 80 },
  { title: '模块', dataIndex: 'module', key: 'module', width: 100 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 120 },
  { title: '内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 }
]

async function fetchLogs() {
  try {
    loading.value = true
    const res = await getOperationLogs(module.value, pagination.value.current, pagination.value.pageSize)
    logs.value = res.data.list
    pagination.value.total = res.data.total
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchLogs()
}

onMounted(fetchLogs)
</script>
