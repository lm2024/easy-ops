<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <folder-open-outlined style="color: #722ed1" />
          <span style="font-weight: 600">项目管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-input
            v-model:value="keyword"
            placeholder="搜索项目名称..."
            style="width: 280px"
            allow-clear
            @search="fetchProjects"
          >
            <template #prefix><search-outlined /></template>
          </a-input>
          <a-button type="primary" @click="$router.push('/projects/add')">
            <plus-outlined />
            新增项目
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="projects"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a style="font-weight: 500" @click="$router.push(`/projects/${record.id}`)">{{ record.name }}</a>
          </template>
          <template v-if="column.key === 'nodeIds'">
            <a-tag color="blue">{{ record.nodeIds || '-' }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="$router.push(`/projects/${record.id}`)">
                <eye-outlined /> 详情
              </a-button>
              <a-button type="link" size="small" @click="editProject(record)">
                <edit-outlined /> 编辑
              </a-button>
              <a-popconfirm title="确定删除?" ok-text="确定" cancel-text="取消" @confirm="deleteProjectAction(record.id)">
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
import type { ProjectModel } from '../types'
import { getProjects, deleteProject } from '../api/project'
import {
  SearchOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  FolderOpenOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const projects = ref<ProjectModel[]>([])
const loading = ref(false)
const keyword = ref('')
const pagination = ref({ current: 1, pageSize: 20, total: 0 })

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: 'JVM参数', dataIndex: 'jvmOpts', key: 'jvmOpts', ellipsis: true },
  { title: '节点ID', dataIndex: 'nodeIds', key: 'nodeIds' },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const }
]

async function fetchProjects() {
  try {
    loading.value = true
    const res = await getProjects(pagination.value.current, pagination.value.pageSize, keyword.value)
    projects.value = res.data.list
    pagination.value.total = res.data.total
  } finally {
    loading.value = false
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchProjects()
}

function editProject(record: ProjectModel) {
  router.push(`/projects/${record.id}/edit`)
}

async function deleteProjectAction(id: string) {
  await deleteProject(id)
  fetchProjects()
}

onMounted(fetchProjects)
</script>
