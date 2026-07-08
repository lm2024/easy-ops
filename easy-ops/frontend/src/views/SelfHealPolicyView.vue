<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px; margin-bottom: 16px">
      <template #title>
        <a-space>
          <medicine-box-outlined style="color: #52c41a" />
          <span style="font-weight: 600">自愈策略</span>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="showEditPolicy(null)">
          <plus-outlined /> 新增策略
        </a-button>
      </template>

      <a-table
        :columns="policyColumns"
        :data-source="policies"
        :loading="policyLoading"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled === 1 ? 'green' : 'default'">
              {{ record.enabled === 1 ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'circuitBreaker'">
            <a-tag v-if="record.circuitBreaker === 1" color="red">熔断中</a-tag>
            <span v-else>正常</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditPolicy(record)">编辑</a-button>
              <a-button
                v-if="record.circuitBreaker === 1"
                type="link"
                size="small"
                @click="handleResetBreaker(record.projectId)"
              >
                解除熔断
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <history-outlined style="color: #1890ff" />
          <span style="font-weight: 600">自愈事件</span>
        </a-space>
      </template>
      <template #extra>
        <a-select
          v-model:value="filterProjectId"
          style="width: 200px"
          placeholder="全部项目"
          allow-clear
          @change="fetchEvents"
        >
          <a-select-option v-for="p in projects" :key="p.id" :value="Number(p.id)">
            {{ p.name }}
          </a-select-option>
        </a-select>
      </template>

      <a-timeline>
        <a-timeline-item
          v-for="evt in events"
          :key="evt.id"
          :color="eventColor(evt.eventType)"
        >
          <div class="event-title">
            <a-tag>{{ evt.eventType }}</a-tag>
            <span>{{ evt.nodeName || evt.nodeId }}</span>
            <span class="event-time">{{ formatTime(evt.createTime) }}</span>
          </div>
          <div class="event-detail">{{ evt.detail }}</div>
          <div v-if="evt.retryCount != null" class="event-retry">
            重试 {{ evt.retryCount }}/{{ evt.maxRetries }}
          </div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-if="!events.length && !eventsLoading" />
      <div v-if="eventsTotal > events.length" style="text-align: center; margin-top: 12px">
        <a-button :loading="eventsLoading" @click="loadMoreEvents">加载更多</a-button>
      </div>
    </a-card>

    <!-- 策略编辑 -->
    <a-modal
      v-model:open="policyModalVisible"
      :title="editingPolicy?.id ? '编辑策略' : '新增策略'"
      @ok="handleSavePolicy"
      :confirm-loading="policySaving"
    >
      <a-form layout="vertical">
        <a-form-item label="项目" required>
          <a-select v-model:value="editingPolicy.projectId" :disabled="!!editingPolicy.id">
            <a-select-option v-for="p in projects" :key="p.id" :value="Number(p.id)">
              {{ p.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="启用">
          <a-switch :checked="editingPolicy.enabled === 1" @change="(v: boolean) => editingPolicy.enabled = v ? 1 : 0" />
        </a-form-item>
        <a-form-item label="最大重试次数">
          <a-input-number v-model:value="editingPolicy.maxRetries" :min="0" :max="10" />
        </a-form-item>
        <a-form-item label="重试间隔(秒)">
          <a-input-number v-model:value="editingPolicy.retryIntervalSec" :min="5" :max="300" />
        </a-form-item>
        <a-form-item label="检查间隔(秒)">
          <a-input-number v-model:value="editingPolicy.checkIntervalSec" :min="10" :max="600" />
        </a-form-item>
        <a-form-item label="弹窗通知">
          <a-switch :checked="editingPolicy.notifyPopup === 1" @change="(v: boolean) => editingPolicy.notifyPopup = v ? 1 : 0" />
        </a-form-item>
        <a-form-item label="自动 AI 诊断">
          <a-switch :checked="editingPolicy.autoAiDiagnose === 1" @change="(v: boolean) => editingPolicy.autoAiDiagnose = v ? 1 : 0" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import type { ProjectModel, SelfHealPolicyModel, SelfHealEventModel } from '../types'
import { getProjects } from '../api/project'
import { listPolicies, savePolicy, resetCircuitBreaker, listEvents } from '../api/selfHeal'
import {
  MedicineBoxOutlined, PlusOutlined, HistoryOutlined
} from '@ant-design/icons-vue'

const projects = ref<ProjectModel[]>([])
const policies = ref<SelfHealPolicyModel[]>([])
const policyLoading = ref(false)
const events = ref<SelfHealEventModel[]>([])
const eventsLoading = ref(false)
const eventsTotal = ref(0)
const eventsPage = ref(1)
const filterProjectId = ref<number>()
const policyModalVisible = ref(false)
const policySaving = ref(false)
const editingPolicy = reactive<SelfHealPolicyModel>({
  projectId: 0, enabled: 1, maxRetries: 3, retryIntervalSec: 30,
  checkIntervalSec: 60, notifyPopup: 1, autoAiDiagnose: 0
})

const policyColumns = [
  { title: '项目ID', dataIndex: 'projectId', key: 'projectId', width: 80 },
  { title: '状态', key: 'enabled', width: 80 },
  { title: '最大重试', dataIndex: 'maxRetries', key: 'maxRetries', width: 90 },
  { title: '重试间隔(s)', dataIndex: 'retryIntervalSec', key: 'retryIntervalSec', width: 110 },
  { title: '检查间隔(s)', dataIndex: 'checkIntervalSec', key: 'checkIntervalSec', width: 110 },
  { title: '熔断', key: 'circuitBreaker', width: 80 },
  { title: '操作', key: 'action', width: 160 }
]

function formatTime(ts?: number) {
  return ts ? dayjs(ts).format('MM-DD HH:mm:ss') : ''
}

function eventColor(type: string) {
  const map: Record<string, string> = {
    RESTART: 'blue', RETRY: 'orange', CIRCUIT_BREAK: 'red', RECOVER: 'green'
  }
  return map[type] || 'gray'
}

async function fetchPolicies() {
  policyLoading.value = true
  try {
    const res = await listPolicies()
    policies.value = res.data || []
  } finally {
    policyLoading.value = false
  }
}

async function fetchEvents() {
  eventsPage.value = 1
  eventsLoading.value = true
  try {
    const res = await listEvents(filterProjectId.value, 1, 20)
    events.value = res.data?.list || []
    eventsTotal.value = res.data?.total || 0
  } finally {
    eventsLoading.value = false
  }
}

async function loadMoreEvents() {
  eventsPage.value++
  eventsLoading.value = true
  try {
    const res = await listEvents(filterProjectId.value, eventsPage.value, 20)
    events.value.push(...(res.data?.list || []))
  } finally {
    eventsLoading.value = false
  }
}

function showEditPolicy(record: SelfHealPolicyModel | null) {
  if (record) {
    Object.assign(editingPolicy, record)
  } else {
    Object.assign(editingPolicy, {
      id: undefined, projectId: projects.value[0] ? Number(projects.value[0].id) : 0,
      enabled: 1, maxRetries: 3, retryIntervalSec: 30, checkIntervalSec: 60,
      notifyPopup: 1, autoAiDiagnose: 0
    })
  }
  policyModalVisible.value = true
}

async function handleSavePolicy() {
  policySaving.value = true
  try {
    await savePolicy({ ...editingPolicy })
    policyModalVisible.value = false
    message.success('策略已保存')
    fetchPolicies()
  } finally {
    policySaving.value = false
  }
}

async function handleResetBreaker(projectId: number) {
  await resetCircuitBreaker(projectId)
  message.success('熔断已解除')
  fetchPolicies()
}

onMounted(async () => {
  const res = await getProjects(1, 100)
  projects.value = res.data.list || []
  fetchPolicies()
  fetchEvents()
})
</script>

<style scoped>
.event-title { font-weight: 500; margin-bottom: 4px; }
.event-time { color: #71717a; font-size: 12px; margin-left: 8px; }
.event-detail { color: #a1a1aa; font-size: 13px; }
.event-retry { color: #faad14; font-size: 12px; margin-top: 2px; }
</style>
