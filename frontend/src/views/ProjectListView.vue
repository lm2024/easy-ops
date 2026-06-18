<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-input
        v-model:value="keyword"
        placeholder="搜索项目..."
        style="width: 250px"
        allow-clear
        @search="fetchProjects"
      >
        <template #prefix><search-outlined /></template>
      </a-input>
      <a-button type="primary" @click="$router.push('/projects/add')">
        新增项目
      </a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="projects"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="$router.push(`/projects/${record.id}`)">详情</a-button>
            <a-button size="small" @click="editProject(record)">编辑</a-button>
            <a-popconfirm title="确定删除?" @confirm="deleteProjectAction(record.id)">
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
import { useRouter } from 'vue-router'
import type { ProjectModel } from '../types'
import { getProjects, deleteProject } from '../api/project'
import { SearchOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const projects = ref<ProjectModel[]>([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: 'JVM参数', dataIndex: 'jvmOpts', key: 'jvmOpts' },
  { title: '节点ID', dataIndex: 'nodeIds', key: 'nodeIds' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action' }
]

async function fetchProjects() {
  try {
    loading.value = true
    const res = await getProjects(page.value, 20, keyword.value)
    projects.value = res.data
  } finally {
    loading.value = false
  }
}

function handleTableChange(pagination: any) {
  page.value = pagination.current
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
