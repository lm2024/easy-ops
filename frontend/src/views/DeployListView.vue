<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <rocket-outlined style="color: #fa541c" />
          <span style="font-weight: 600">部署记录</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select v-model:value="projectId" style="width: 200px" placeholder="选择项目">
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
              {{ p.name }}
            </a-select-option>
          </a-select>
          <a-button type="primary" @click="newDeployment = true">
            <plus-outlined /> 新建部署
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="deploys"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
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
              <a-button type="link" size="small" @click="viewDetail(record)">
                <eye-outlined /> 详情
              </a-button>
              <a-popconfirm title="确认回滚?" ok-text="确定" cancel-text="取消" @confirm="rollback(record)">
                <a-button type="link" size="small" danger>
                  <undo-outlined /> 回滚
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      title="新建部署"
      v-model:open="newDeployment"
      @ok="startDeploy"
      ok-text="部署"
      cancel-text="取消"
    >
      <a-form layout="vertical">
        <a-form-item label="版本">
          <a-select v-model:value="selectedVersionId" placeholder="选择版本">
            <a-select-option v-for="v in versionList" :key="v.id" :value="v.id">
              {{ v.version }} - {{ v.jarName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="目标节点">
          <a-select v-model:value="selectedNodeId" placeholder="选择节点">
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { ProjectModel, VersionModel, NodeModel, DeployModel } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { getVersions } from '../api/version'
import { getDeployRecords, createDeploy, rollbackDeploy } from '../api/deploy'
import {
  PlusOutlined,
  EyeOutlined,
  UndoOutlined,
  RocketOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const projects = ref<ProjectModel[]>([])
const nodeList = ref<NodeModel[]>([])
const versionList = ref<VersionModel[]>([])
const deploys = ref<DeployModel[]>([])
const loading = ref(false)
const projectId = ref('')
const pagination = ref({ current: 1, pageSize: 20, total: 0 })
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
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '版本', dataIndex: 'versionId', key: 'versionId' },
  { title: '节点', dataIndex: 'nodeId', key: 'nodeId' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: 'Jar包名', dataIndex: 'jarName', key: 'jarName' },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const }
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
    deploys.value = deployRes.data.list
    versionList.value = verRes.data.list
    nodeList.value = nodeRes.data.list
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  const res = await getProjects()
  projects.value = res.data.list
  if (projects.value.length > 0) {
    projectId.value = projects.value[0].id
    fetchData()
  }
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
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
