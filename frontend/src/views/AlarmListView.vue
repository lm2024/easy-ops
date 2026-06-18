<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="alarmType" style="width: 150px">
        <a-select-option value="">全部类型</a-select-option>
        <a-select-option value="NODE_OFFLINE">节点离线</a-select-option>
        <a-select-option value="PROCESS_DOWN">进程异常</a-select-option>
      </a-select>
      <a-button type="primary" @click="fetchAlarms">查询</a-button>
      <a-button @click="$router.push('/alarm-config')">告警配置</a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="alarms"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">
          <a-tag>{{ record.type }}</a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button size="small" @click="viewDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { AlarmModel } from '../types'
import { getAlarms } from '../api/monitor'

const alarms = ref<AlarmModel[]>([])
const loading = ref(false)
const alarmType = ref('')
const pagination = ref({ current: 1, pageSize: 20 })

const columns = [
  { title: '类型', dataIndex: 'type', key: 'type' },
  { title: '内容', dataIndex: 'content', key: 'content' },
  { title: '发送结果', dataIndex: 'sendResult', key: 'sendResult' },
  { title: '发送时间', dataIndex: 'sendTime', key: 'sendTime' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action' }
]

async function fetchAlarms() {
  try {
    loading.value = true
    const res = await getAlarms(undefined, alarmType.value, pagination.value.current, pagination.value.pageSize)
    alarms.value = res.data
  } finally {
    loading.value = false
  }
}

function handleTableChange(pagination: any) {
  pagination.value.current = pagination.current
  fetchAlarms()
}

onMounted(fetchAlarms)
</script>
