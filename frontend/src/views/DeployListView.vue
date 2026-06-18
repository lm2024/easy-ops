<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="projectId" style="width: 200px">
        <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
          {{ p.name }}
        </a-select-option>
      </a-select>
      <a-button type="primary" @click="newDeployment = true">新建部署</a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="deploys"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-badge
            :status="statusMap[record.status]?.status"
            :text="statusMap[record.status]?.text"
          />
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="viewDetail(record)">详情</a-button>
            <a-popconfirm title="确认回滚?" @confirm="rollback(record)">
              <a-button size="small" danger>回滚</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新建部署弹窗 -->
    <a-modal
      title="新建部署"
      v-model:open="newDeployment"
      @ok="startDeploy"
    >
      <a-form layout="vertical">
        <a-form-item label="版本">
          <a-select v-model:value="selectedVersionId">
            <a-select-option v-for="v in versionList" :key="v.id" :value="v.id">
              {{ v.version }} - {{ v.jarName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="目标节点">
          <a-select v-model:value="selectedNodeId">
            <a-select-option v-for="n in nodeList" :key="n.id" :value="n.id">
              {{ n.name }} ({{ n.ip }})
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { ProjectModel, VersionModel, NodeModel, DeployModel } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { getVersions } from '../api/version'
import { getDeployRecords, createDeploy, rollbackDeploy } from '../api/deploy'

const router = useRouter()
const projects = ref<ProjectModel[]>([])
const nodeList = ref<NodeModel[]>([])
const versionList = ref<VersionModel[]>([])
const deploys = ref<DeployModel[]>([])
const loading = ref(false)
const projectId = ref('')
const pagination = ref({ current: 1, pageSize: 20 })
const newDeployment = ref(false)
const selectedVersionId = ref('')
const selectedNodeId = ref('')

const statusMap: Record<number, { status: string; text: string }> = {
  0: { status: 'processing', text: '进行中' },
  1: { status: 'success', text: '成功' },
  2: { status: 'error', text: '失败' },
  3: { status: 'default', text: '回滚' }
}

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '版本', dataIndex: 'versionId', key: 'versionId' },
  { title: '节点', dataIndex: 'nodeId', key: 'nodeId' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: 'Jar包名', dataIndex: 'jarName', key: 'jarName' },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime' },
  { title: '操作', key: 'action' }
]

async function fetchData() {
  if (!projectId.value) return
  try {
    loading.value = true
    const [deployRes, verRes, nodeRes] = await Promise.all([
      getDeployRecords(projectId.value, pagination.value.current, pagination.value.pageSize),
      getVersions(projectId.value),
      getNodes()
    ])
    deploys.value = deployRes.data
    versionList.value = verRes.data
    nodeList.value = nodeRes.data
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  const res = await getProjects()
  projects.value = res.data
  if (projects.value.length > 0) {
    projectId.value = projects.value[0].id
    fetchData()
  }
}

function handleTableChange(pagination: any) {
  pagination.value.current = pagination.current
  fetchData()
}

async function startDeploy() {
  await createDeploy(projectId.value, selectedVersionId.value, selectedNodeId.value)
  newDeployment.value = false
  fetchData()
}

function viewDetail(record: DeployModel) {
  router.push(`/deploy/detail/${record.id}`)
}

async function rollback(record: DeployModel) {
  await rollbackDeploy(record.id)
  fetchData()
}

onMounted(fetchProjects)
</script>
