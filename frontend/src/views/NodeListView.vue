<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <cluster-outlined style="color: #1890ff" />
          <span style="font-weight: 600">节点管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select
            v-model:value="filterStatus"
            style="width: 120px"
            placeholder="节点状态"
            allow-clear
            @change="fetchNodes"
          >
            <a-select-option value="">全部状态</a-select-option>
            <a-select-option value="1">在线</a-select-option>
            <a-select-option value="0">离线</a-select-option>
          </a-select>
          <a-input
            v-model:value="keyword"
            placeholder="搜索名称或IP..."
            style="width: 240px"
            allow-clear
            @press-enter="fetchNodes"
          >
            <template #prefix><search-outlined /></template>
          </a-input>
          <a-button @click="fetchNodes">
            <search-outlined /> 搜索
          </a-button>
          <a-button @click="handleExport">
            <download-outlined /> 导出CSV
          </a-button>
          <a-button @click="handleImportClick">
            <upload-outlined /> 导入CSV
          </a-button>
          <input
            ref="fileInputRef"
            type="file"
            accept=".csv"
            style="display: none"
            @change="handleImportFile"
          />
          <a-button type="primary" @click="$router.push('/nodes/add')">
            <plus-outlined />
            新增节点
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="nodes"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a style="font-weight: 500">{{ record.name }}</a>
          </template>
          <template v-if="column.key === 'ip'">
            <a-tag>{{ record.ip }}</a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-badge :status="record.status === 1 ? 'success' : 'error'"
                     :text="record.status === 1 ? '在线' : '离线'" />
          </template>
          <template v-if="column.key === 'lastHeartbeat'">
            <span style="color: #8c8c8c; font-size: 13px">
              {{ record.lastHeartbeat ? new Date(record.lastHeartbeat).toLocaleString() : '-' }}
            </span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="editNode(record)">
                <edit-outlined /> 编辑
              </a-button>
              <a-popconfirm
                title="确定删除此节点?"
                ok-text="确定"
                cancel-text="取消"
                @confirm="deleteNodeAction(record.id)"
              >
                <a-button type="link" size="small" danger>
                  <delete-outlined /> 删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { NodeModel } from '../types'
import { getNodes, deleteNode, exportNodesCsv, importNodesCsv } from '../api/node'
import {
  SearchOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ClusterOutlined,
  DownloadOutlined,
  UploadOutlined
} from '@ant-design/icons-vue'

const router = useRouter()

const nodes = ref<NodeModel[]>([])
const loading = ref(false)
const keyword = ref('')
const filterStatus = ref<string | undefined>(undefined)
const pagination = ref({ current: 1, pageSize: 20, total: 0 })
const fileInputRef = ref<HTMLInputElement>()

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: '端口', dataIndex: 'port', key: 'port', width: 80 },
  { title: '系统', dataIndex: 'osInfo', key: 'osInfo' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '最后心跳', dataIndex: 'lastHeartbeat', key: 'lastHeartbeat', width: 180 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const }
]

async function fetchNodes() {
  try {
    loading.value = true
    const res = await getNodes(
      pagination.value.current,
      pagination.value.pageSize,
      keyword.value,
      filterStatus.value
    )
    nodes.value = res.data.list
    pagination.value.total = res.data.total
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchNodes()
}

function editNode(record: NodeModel) {
  router.push(`/nodes/${record.id}/edit`)
}

async function deleteNodeAction(id: string) {
  await deleteNode(id)
  fetchNodes()
  message.success('节点已删除')
}

async function handleExport() {
  try {
    await exportNodesCsv()
    message.success('导出成功')
  } catch {
    message.error('导出失败')
  }
}

function handleImportClick() {
  fileInputRef.value?.click()
}

async function handleImportFile(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  try {
    const res = await importNodesCsv(file)
    message.success(`成功导入 ${res.data.imported} 个节点`)
    fetchNodes()
  } catch {
    message.error('导入失败，请检查CSV格式')
  } finally {
    target.value = '' // allow re-import of same file
  }
}

onMounted(fetchNodes)
</script>
