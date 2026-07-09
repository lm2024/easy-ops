<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <tag-outlined style="color: #13c2c2" />
          <span style="font-weight: 600">版本管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select v-model:value="projectId" style="width: 200px" placeholder="选择项目">
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
              {{ p.name }}
            </a-select-option>
          </a-select>
          <a-button type="primary" @click="showUploadModal = true">
            <upload-outlined /> 上传Jar包
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="versions"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'version'">
            <a-tag color="blue">{{ record.version }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="removeVersion(record.id)">
                <delete-outlined /> 删除
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      title="上传Jar包"
      v-model:open="showUploadModal"
      @ok="handleUpload"
      ok-text="上传"
      cancel-text="取消"
    >
      <a-upload
        :file-list="fileList"
        :before-upload="beforeUpload"
        accept=".jar"
      >
        <a-button><upload-outlined /> 选择文件</a-button>
      </a-upload>
    </a-modal>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { ProjectModel, VersionModel } from '../types'
import { getProjects } from '../api/project'
import { getVersions, deleteVersion, uploadVersion } from '../api/version'
import {
  UploadOutlined,
  DeleteOutlined,
  TagOutlined
} from '@ant-design/icons-vue'

const versions = ref<VersionModel[]>([])
const projects = ref<ProjectModel[]>([])
const loading = ref(false)
const projectId = ref('')
const showUploadModal = ref(false)
const fileList = ref<any[]>([])
const pagination = ref({ current: 1, pageSize: 20, total: 0 })

const columns = [
  { title: '版本', dataIndex: 'version', key: 'version' },
  { title: 'Jar包名', dataIndex: 'jarName', key: 'jarName' },
  { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize' },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const }
]

async function fetchVersions() {
  if (!projectId.value) return
  try {
    loading.value = true
    const res = await getVersions(projectId.value, pagination.value.current, pagination.value.pageSize)
    versions.value = res.data.list
    pagination.value.total = res.data.total
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  const res = await getProjects()
  projects.value = res.data.list
  if (projects.value.length > 0) {
    projectId.value = projects.value[0].id
    fetchVersions()
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchVersions()
}

async function handleUpload() {
  const file = fileList.value[0]?.originFileObj
  if (!file) return
  await uploadVersion(projectId.value, file)
  showUploadModal.value = false
  fileList.value = []
  fetchVersions()
}

function beforeUpload(file: File) {
  fileList.value = [{ uid: Date.now().toString(), name: file.name, status: 'done', originFileObj: file }]
  return false
}

async function removeVersion(id: string) {
  await deleteVersion(id)
  fetchVersions()
}

onMounted(fetchProjects)
</script>
