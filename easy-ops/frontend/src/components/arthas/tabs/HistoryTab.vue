<template>
  <div class="history-tab">
    <div class="toolbar">
      <a-space>
        <a-select v-model:value="filterNodeId" placeholder="节点" allow-clear style="width: 150px" size="small">
          <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">{{ n.nodeName }}</a-select-option>
        </a-select>
        <a-select v-model:value="filterStatus" placeholder="状态" allow-clear style="width: 120px" size="small">
          <a-select-option value="SUCCESS">成功</a-select-option>
          <a-select-option value="FAILED">失败</a-select-option>
          <a-select-option value="RUNNING">进行中</a-select-option>
        </a-select>
        <a-range-picker v-model:value="filterTime" size="small" style="width: 280px" />
        <a-button type="primary" size="small" @click="loadHistory">
          <search-outlined /> 查询
        </a-button>
        <a-button size="small" @click="resetFilter">重置</a-button>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="historyList"
        :pagination="pagination"
        size="small"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-if="column.key === 'diagnoseType'">
            <a-tag color="blue">{{ diagnoseTypeText(record.diagnoseType) }}</a-tag>
          </template>
          <template v-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="viewDetail(record)">详情</a-button>
              <a-button type="link" size="small" danger @click="deleteRecord(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-spin>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="诊断详情"
      width="800px"
      :footer="null"
    >
      <a-spin :spinning="detailLoading">
        <div v-if="currentDetail">
          <a-descriptions :column="2" size="small" bordered>
            <a-descriptions-item label="诊断ID">{{ currentDetail.id }}</a-descriptions-item>
            <a-descriptions-item label="诊断类型">{{ diagnoseTypeText(currentDetail.diagnoseType) }}</a-descriptions-item>
            <a-descriptions-item label="节点">{{ currentDetail.nodeName }}</a-descriptions-item>
            <a-descriptions-item label="项目">{{ currentDetail.projectName }}</a-descriptions-item>
            <a-descriptions-item label="PID">{{ currentDetail.pid }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(currentDetail.status)">{{ statusText(currentDetail.status) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="开始时间">{{ formatTime(currentDetail.startTime) }}</a-descriptions-item>
            <a-descriptions-item label="结束时间">{{ formatTime(currentDetail.endTime) }}</a-descriptions-item>
            <a-descriptions-item label="耗时" :span="2">{{ currentDetail.durationMs }} ms</a-descriptions-item>
          </a-descriptions>

          <div class="detail-section">
            <div class="detail-title">诊断结果</div>
            <a-tabs v-if="currentDetail.results && currentDetail.results.length > 0">
              <a-tab-pane
                v-for="(result, idx) in currentDetail.results"
                :key="idx"
                :tab="result.commandType || `结果${idx + 1}`"
              >
                <pre class="result-json">{{ formatResult(result) }}</pre>
              </a-tab-pane>
            </a-tabs>
            <a-empty v-else description="无诊断结果" />
          </div>

          <div v-if="currentDetail.errorMsg" class="detail-section">
            <div class="detail-title">错误信息</div>
            <a-alert type="error" :message="currentDetail.errorMsg" />
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { getDiagnoseHistory, getDiagnoseDetail, deleteDiagnoseRecord } from '@/api/arthas'
import { friendlyMessage } from '@/utils/arthasError'

// projectId 由诊断抽屉传入，后端它是必填参数。
// 早期这里没传，后端 @RequestParam 直接抛 "Required request parameter 'projectId' is not present"，
// 前端只看到一句"系统内部异常"，历史列表从来没打开过。
const props = defineProps<{ projectId?: number }>()

const nodes = ref<any[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const historyList = ref<any[]>([])
const currentDetail = ref<any>(null)
const detailVisible = ref(false)

const filterNodeId = ref<number | undefined>()
const filterStatus = ref<string>()
const filterTime = ref<any[]>([])

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '诊断类型', dataIndex: 'diagnoseType', key: 'diagnoseType', width: 120 },
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName', width: 120 },
  { title: '项目', dataIndex: 'projectName', key: 'projectName', width: 120 },
  { title: 'PID', dataIndex: 'pid', key: 'pid', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 120, fixed: 'right' as const }
]

function statusColor(status: string): string {
  switch (status) {
    case 'SUCCESS': return 'green'
    case 'FAILED': return 'red'
    case 'RUNNING': return 'processing'
    default: return 'default'
  }
}

function statusText(status: string): string {
  switch (status) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'RUNNING': return '进行中'
    default: return status
  }
}

function diagnoseTypeText(type: string): string {
  switch (type) {
    case 'FULL_GC': return 'Full GC 分析'
    case 'MEMORY': return '内存分析'
    case 'THREAD': return '线程分析'
    case 'CPU': return 'CPU 分析'
    case 'CUSTOM': return '自定义诊断'
    default: return type
  }
}

function formatTime(ts: number | string): string {
  if (!ts) return '-'
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

function formatResult(result: any): string {
  if (typeof result === 'string') return result
  if (result.output) return result.output
  if (result.results) return JSON.stringify(result.results, null, 2)
  return JSON.stringify(result, null, 2)
}

async function loadHistory() {
  // 没有项目上下文时请求必然失败，直接给出说明，不要发一个注定报错的请求
  if (!props.projectId) {
    historyList.value = []
    pagination.value.total = 0
    return
  }
  loading.value = true
  try {
    const params: any = {
      projectId: props.projectId,
      page: pagination.value.current,
      pageSize: pagination.value.pageSize
    }
    if (filterNodeId.value) params.nodeId = filterNodeId.value
    if (filterStatus.value) params.status = filterStatus.value
    if (filterTime.value && filterTime.value.length === 2) {
      params.startTime = filterTime.value[0].valueOf()
      params.endTime = filterTime.value[1].valueOf()
    }
    const res = await getDiagnoseHistory(params)
    historyList.value = res.data?.list || []
    pagination.value.total = res.data?.total || 0
  } catch (e: any) {
    message.error(friendlyMessage('加载历史失败', e))
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filterNodeId.value = undefined
  filterStatus.value = undefined
  filterTime.value = []
  pagination.value.current = 1
  loadHistory()
}

function handleTableChange(pag: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  loadHistory()
}

async function viewDetail(record: any) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getDiagnoseDetail(record.id)
    currentDetail.value = res.data
  } catch (e: any) {
    message.error(friendlyMessage('加载详情失败', e))
  } finally {
    detailLoading.value = false
  }
}

function deleteRecord(record: any) {
  Modal.confirm({
    title: '删除诊断记录',
    content: `确定要删除诊断记录 #${record.id} 吗？此操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteDiagnoseRecord(record.id)
        message.success('删除成功')
        loadHistory()
      } catch (e: any) {
        message.error(friendlyMessage('删除失败', e))
      }
    }
  })
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.history-tab { padding: 0 4px; }
.toolbar { margin-bottom: 16px; }
.detail-section { margin-top: 16px; }
.detail-title { font-weight: 600; font-size: 14px; margin-bottom: 8px; color: #262626; }
.result-json { max-height: 400px; overflow: auto; background: #f5f5f5; padding: 12px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; }
</style>
