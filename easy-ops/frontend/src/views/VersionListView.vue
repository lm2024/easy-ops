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
          <a-select v-model:value="projectId" style="width: 200px" placeholder="选择项目" @change="onProjectChange">
            <a-select-option v-for="p in projects" :key="p.id" :value="String(p.id)">
              {{ p.name }}
            </a-select-option>
          </a-select>
          <a-button type="primary" :disabled="!projectId" @click="openUploadModal('jar')">
            <upload-outlined /> 上传 Jar 包
          </a-button>
          <a-button :disabled="!projectId" @click="openUploadModal('frontend')">
            <upload-outlined /> 上传前端 dist.zip
          </a-button>
        </a-space>
      </template>

      <a-alert
        v-if="currentProject?.jarName"
        type="info"
        show-icon
        style="margin-bottom: 12px"
        :message="`当前应用要求 Jar 包名: ${currentProject.jarName}`"
      />

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
          <template v-if="column.key === 'packageType'">
            <a-tag :color="record.packageType === 'frontend' ? 'purple' : 'cyan'">
              {{ record.packageType === 'frontend' ? '前端包' : 'Jar包' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'fileSize'">
            {{ formatSize(record.fileSize) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-popconfirm title="确定删除此版本?" @confirm="removeVersion(record.id)">
              <a-button type="link" size="small" danger>
                <delete-outlined /> 删除
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      :title="uploadType === 'frontend' ? '上传前端 dist.zip' : '上传 Jar 包'"
      v-model:open="showUploadModal"
      :confirm-loading="uploading"
      @ok="handleUpload"
      ok-text="上传"
      cancel-text="取消"
    >
      <a-upload-dragger
        :file-list="fileList"
        :before-upload="beforeUpload"
        :accept="uploadType === 'frontend' ? '.zip' : '.jar'"
        :max-count="1"
      >
        <p class="ant-upload-drag-icon"><upload-outlined /></p>
        <p class="ant-upload-text">点击或拖拽文件到此处</p>
        <p class="ant-upload-hint" v-if="uploadType === 'jar' && currentProject?.jarName">
          必须为 {{ currentProject.jarName }}
        </p>
        <p class="ant-upload-hint" v-else-if="uploadType === 'frontend'">
          支持 dist.zip 等前端打包文件
        </p>
      </a-upload-dragger>
      <div v-if="uploading || uploadPercent > 0" style="margin-top: 16px">
        <a-progress :percent="uploadPercent" :status="uploading ? 'active' : 'success'" />
        <div v-if="uploadRemainingSec != null" style="color:#888;font-size:12px;margin-top:4px">
          预计剩余 {{ uploadRemainingSec }} 秒
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { ProjectModel, VersionModel } from '../types'
import { getProjects } from '../api/project'
import { getVersions, deleteVersion, uploadVersion } from '../api/version'
import { UploadOutlined, DeleteOutlined, TagOutlined } from '@ant-design/icons-vue'

const versions = ref<VersionModel[]>([])
const projects = ref<ProjectModel[]>([])
const loading = ref(false)
const projectId = ref('')
const showUploadModal = ref(false)
const uploadType = ref<'jar' | 'frontend'>('jar')
const fileList = ref<any[]>([])
const uploading = ref(false)
const uploadPercent = ref(0)
const uploadRemainingSec = ref<number | null>(null)
const pagination = ref({ current: 1, pageSize: 20, total: 0 })

const currentProject = computed(() => projects.value.find(p => String(p.id) === projectId.value))

const columns = [
  { title: '版本', dataIndex: 'version', key: 'version' },
  { title: '类型', dataIndex: 'packageType', key: 'packageType', width: 90 },
  { title: '包名', dataIndex: 'jarName', key: 'jarName' },
  { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize', width: 120 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const }
]

function formatSize(bytes?: number) {
  if (!bytes) return '-'
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return bytes + ' B'
}

function onProjectChange() {
  pagination.value.current = 1
  fetchVersions()
}

async function fetchVersions() {
  if (!projectId.value) return
  try {
    loading.value = true
    const res = await getVersions(projectId.value, pagination.value.current, pagination.value.pageSize)
    versions.value = res.data.list || []
    pagination.value.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  const res = await getProjects()
  projects.value = res.data.list || []
  if (projects.value.length > 0) {
    projectId.value = String(projects.value[0].id)
    fetchVersions()
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  fetchVersions()
}

function openUploadModal(type: 'jar' | 'frontend') {
  uploadType.value = type
  fileList.value = []
  uploadPercent.value = 0
  uploadRemainingSec.value = null
  showUploadModal.value = true
}

function beforeUpload(file: File) {
  if (uploadType.value === 'jar') {
    if (!file.name.toLowerCase().endsWith('.jar')) {
      message.error('仅支持 .jar 文件')
      return false
    }
    const expected = currentProject.value?.jarName
    if (expected && file.name !== expected) {
      message.error(`Jar 包名必须为 ${expected}，当前: ${file.name}`)
      return false
    }
  } else if (!file.name.toLowerCase().endsWith('.zip')) {
    message.error('前端包仅支持 .zip 文件')
    return false
  }
  fileList.value = [{ uid: Date.now().toString(), name: file.name, status: 'done', originFileObj: file }]
  return false
}

async function handleUpload() {
  const file = fileList.value[0]?.originFileObj as File
  if (!file || !projectId.value) {
    message.warning('请选择文件')
    return
  }
  uploading.value = true
  uploadPercent.value = 0
  try {
    await uploadVersion(projectId.value, file, uploadType.value, (percent, remaining) => {
      uploadPercent.value = percent
      uploadRemainingSec.value = remaining ?? null
    })
    message.success('上传成功')
    showUploadModal.value = false
    fileList.value = []
    fetchVersions()
  } catch {
    message.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function removeVersion(id: string) {
  await deleteVersion(id)
  message.success('已删除')
  fetchVersions()
}

onMounted(fetchProjects)
</script>
