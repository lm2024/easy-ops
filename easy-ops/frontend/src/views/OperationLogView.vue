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
          <a-select v-model:value="filterUserId" style="width: 150px" placeholder="按用户筛选" allow-clear>
            <a-select-option v-for="u in userList" :key="u.id" :value="u.id">{{ u.username }}</a-select-option>
          </a-select>
          <a-select v-model:value="filterModule" style="width: 150px" placeholder="模块筛选" allow-clear>
            <a-select-option value="">全部模块</a-select-option>
            <a-select-option value="AUTH">登录</a-select-option>
            <a-select-option value="NODE">节点</a-select-option>
            <a-select-option value="PROJECT">应用</a-select-option>
            <a-select-option value="VERSION">版本</a-select-option>
            <a-select-option value="DEPLOY">部署</a-select-option>
            <a-select-option value="CONFIG">配置</a-select-option>
            <a-select-option value="ALARM">告警</a-select-option>
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
          <template v-if="column.key === 'username'">
            {{ record.username || ('用户#' + record.userId) }}
          </template>
          <template v-if="column.key === 'module'">
            <a-tag :color="moduleColor(record.module)">{{ moduleLabel(record.module) }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-tag>{{ record.action }}</a-tag>
          </template>
          <template v-if="column.key === 'content'">
            <a-tooltip :title="record.content">
              <span style="max-width: 350px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                {{ record.content }}
              </span>
            </a-tooltip>
          </template>
          <template v-if="column.key === 'createTime'">
            {{ fmtTime(record.createTime) }}
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
const filterModule = ref('')
const filterUserId = ref<number>()
const userList = ref<any[]>([])
const pagination = ref({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
  showTotal: (total: number) => `共 ${total} 条`
})

const columns = [
  { title: '用户', dataIndex: 'username', key: 'username', width: 100 },
  { title: '模块', dataIndex: 'module', key: 'module', width: 90 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 110 },
  { title: '内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
  { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 160 }
]

function moduleColor(m: string): string {
  const map: Record<string, string> = {
    AUTH: 'blue', NODE: 'green', PROJECT: 'purple', VERSION: 'cyan',
    DEPLOY: 'orange', CONFIG: 'geekblue', ALARM: 'red'
  }
  return map[m] || 'default'
}

function moduleLabel(m: string): string {
  const map: Record<string, string> = {
    AUTH: '登录', NODE: '节点', PROJECT: '应用', VERSION: '版本',
    DEPLOY: '部署', CONFIG: '配置', ALARM: '告警'
  }
  return map[m] || m
}

function fmtTime(ts: any): string {
  if (!ts) return '-'
  const d = new Date(typeof ts === 'string' ? Number(ts) : ts)
  if (isNaN(d.getTime())) return String(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function fetchLogs() {
  try {
    loading.value = true
    const res = await getOperationLogs(filterModule.value, filterUserId.value, pagination.value.current, pagination.value.pageSize)
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

async function loadUsers() {
  try {
    // 获取所有用户列表用于筛选
    const res = await getOperationLogs('', undefined, 1, 1000)
    const userIds = new Set<number>()
    const users: any[] = []
    res.data.list.forEach((log: any) => {
      if (log.userId && !userIds.has(log.userId)) {
        userIds.add(log.userId)
        users.push({ id: log.userId, username: log.username || '用户#' + log.userId })
      }
    })
    userList.value = users
  } catch { /* ignore */ }
}

onMounted(() => {
  fetchLogs()
  loadUsers()
})
</script>
