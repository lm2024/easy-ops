<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-input
        v-model:value="keyword"
        placeholder="搜索节点..."
        style="width: 250px"
        allow-clear
        @search="fetchNodes"
      >
        <template #prefix><search-outlined /></template>
      </a-input>
      <a-button type="primary" @click="$router.push('/nodes/add')">
        新增节点
      </a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="nodes"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-badge :status="record.status === 1 ? 'success' : 'error'"
                   :text="record.status === 1 ? '在线' : '离线'" />
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="viewNode(record)">详情</a-button>
            <a-button size="small" @click="editNode(record)">编辑</a-button>
            <a-popconfirm
              title="确定删除此节点?"
              @confirm="deleteNodeAction(record.id)"
            >
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { NodeModel } from '../types'
import { getNodes, deleteNode } from '../api/node'
import { SearchOutlined } from '@ant-design/icons-vue'

const nodes = ref<NodeModel[]>([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: '端口', dataIndex: 'port', key: 'port' },
  { title: '系统', dataIndex: 'osInfo', key: 'osInfo' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最后心跳', dataIndex: 'lastHeartbeat', key: 'lastHeartbeat' },
  { title: '操作', key: 'action' }
]

async function fetchNodes() {
  try {
    loading.value = true
    const res = await getNodes(page.value, pageSize.value, keyword.value)
    nodes.value = res.data
  } finally {
    loading.value = false
  }
}

function handleTableChange(pagination: any) {
  page.value = pagination.current
  pageSize.value = pagination.pageSize
  fetchNodes()
}

function viewNode(record: NodeModel) {
  // Navigate to node detail (reuse form for now)
}
function editNode(record: NodeModel) {
  // Navigate to edit form
}
async function deleteNodeAction(id: string) {
  await deleteNode(id)
  fetchNodes()
}

onMounted(fetchNodes)
</script>
