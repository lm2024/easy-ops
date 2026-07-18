<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <alert-outlined style="color: #ff4d4f" />
          <span style="font-weight: 600">告警中心</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select v-model:value="alarmType" style="width: 150px" placeholder="告警类型">
            <a-select-option value="">全部类型</a-select-option>
            <a-select-option value="NODE_OFFLINE">节点离线</a-select-option>
            <a-select-option value="PROCESS_DOWN">进程异常</a-select-option>
          </a-select>
          <a-button type="primary" @click="fetchAlarms">
            <search-outlined /> 查询
          </a-button>
          <a-button @click="$router.push('/alarm-config')">
            <setting-outlined /> 告警配置
          </a-button>
          <a-popconfirm
            v-if="isAdmin"
            title="确定要清空所有告警记录吗？"
            ok-text="确认清空"
            cancel-text="取消"
            placement="bottomRight"
            @confirm="handleClearAll"
          >
            <a-button danger>
              <delete-outlined /> 清空
            </a-button>
          </a-popconfirm>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="alarms"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.type === 'NODE_OFFLINE' ? 'orange' : 'red'">{{ record.type }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small">
              <eye-outlined /> 详情
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { AlarmModel } from '../types'
import { getAlarms, clearAlarms } from '../api/monitor'
import { useAuthStore } from '../stores/auth'
import {
  SearchOutlined,
  SettingOutlined,
  EyeOutlined,
  AlertOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')

const alarms = ref<AlarmModel[]>([])
const loading = ref(false)
const alarmType = ref('')
const pagination = ref({ current: 1, pageSize: 20, total: 0 })

const columns = [
  { title: '类型', dataIndex: 'type', key: 'type', width: 150 },
  { title: '内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '发送结果', dataIndex: 'sendResult', key: 'sendResult', width: 100 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const }
]

async function fetchAlarms() {
  try {
    loading.value = true
    const res = await getAlarms(undefined, alarmType.value, pagination.value.current, pagination.value.pageSize)
    alarms.value = res.data.list
    pagination.value.total = res.data.total
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchAlarms()
}

async function handleClearAll() {
  try {
    await clearAlarms()
    message.success('已清空所有告警')
    pagination.value.current = 1
    await fetchAlarms()
  } catch {
    message.error('清空失败')
  }
}

onMounted(fetchAlarms)
</script>
