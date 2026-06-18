<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="projectId" style="width: 200px">
        <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
          {{ p.name }}
        </a-select-option>
      </a-select>
      <a-button type="primary" @click="showUploadModal = true">上传Jar包</a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="versions"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="deleteVersion(record.id)">删除</a-button>
            <a-button size="small" @click="deploy(record)">部署</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 上传Jar弹窗 -->
    <a-modal
      title="上传Jar包"
      v-model:open="showUploadModal"
      @ok="handleUpload"
    >
      <a-upload
        v-model:file-list="fileList"
        :before-upload="beforeUpload"
        accept=".jar"
      >
        <a-button>选择文件</a-button>
      </a-upload>
    </a-modal>

    <!-- 部署弹窗 -->
    <a-modal
      title="确认部署"
      v-model:open="deployModal"
      @ok="confirmDeploy"
    >
      <p>将部署 {{ deployTarget?.jarName }} 到选定节点？</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import type { ProjectModel, VersionModel } from '../types'
import { getProjects } from '../api/project'
import { getVersions, deleteVersion, uploadVersion } from '../api/version'
import { UploadOutlined } from '@ant-design/icons-vue'

const versions = ref<VersionModel[]>([])
const projects = ref<ProjectModel[]>([])
const loading = ref(false)
const projectId = ref('')
const showUploadModal = ref(false)
const fileList = ref<any[]>([])
const pagination = ref({ current: 1, pageSize: 20 })

const columns = [
  { title: '版本', dataIndex: 'version', key: 'version' },
  { title: 'Jar包名', dataIndex: 'jarName', key: 'jarName' },
  { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action' }
]

const deployTarget = ref<VersionModel | null>(null)
const deployModal = ref(false)
const deployNodeId = ref('')

async function fetchVersions() {
  if (!projectId.value) return
  try {
    loading.value = true
    const res = await getVersions(projectId.value, pagination.value.current, pagination.value.pageSize)
    versions.value = res.data
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  const res = await getProjects()
  projects.value = res.data
  if (projects.value.length > 0) {
    projectId.value = projects.value[0].id
    fetchVersions()
  }
}

function handleTableChange(pagination: any) {
  pagination.value.current = pagination.current
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

async function deleteVersion(id: string) {
  await deleteVersion(id)
  fetchVersions()
}

function deploy(record: VersionModel) {
  deployTarget.value = record
  deployModal.value = true
}

async function confirmDeploy() {
  if (deployTarget.value) {
    deployModal.value = false
  }
}

onMounted(fetchProjects)
</script>
