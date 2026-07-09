<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <setting-outlined style="color: #722ed1" />
          <span style="font-weight: 600">配置文件管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select
            v-model:value="projectId"
            style="width: 220px"
            placeholder="选择项目"
            @change="onProjectChange"
          >
            <a-select-option v-for="p in projects" :key="p.id" :value="Number(p.id)">
              {{ p.name }}
            </a-select-option>
          </a-select>
          <a-button :disabled="!projectId" @click="showAddFile">
            <plus-outlined /> 新增配置
          </a-button>
          <a-button :disabled="!selectedFileId" :loading="refreshing" @click="handleRefresh">
            <reload-outlined /> 刷新快照
          </a-button>
        </a-space>
      </template>

      <a-row :gutter="16">
        <a-col :span="8">
          <a-table
            :columns="fileColumns"
            :data-source="configFiles"
            :loading="filesLoading"
            row-key="id"
            size="small"
            :pagination="false"
            :row-class-name="fileRowClass"
            :custom-row="fileCustomRow"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'isPrimary'">
                <a-tag v-if="record.isPrimary === 1" color="blue">主配置</a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-popconfirm title="确认删除？" @confirm="handleDeleteFile(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
            </template>
          </a-table>

          <a-divider>节点快照</a-divider>
          <a-table
            :columns="snapshotColumns"
            :data-source="snapshots"
            :loading="snapshotLoading"
            row-key="nodeId"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'syncStatus'">
                <a-tag :color="syncColor(record.syncStatus)">{{ syncLabel(record.syncStatus) }}</a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-button type="link" size="small" @click="loadNodeContent(record.nodeId)">查看</a-button>
              </template>
            </template>
          </a-table>
          <a-button
            v-if="snapshots.length > 1"
            block
            style="margin-top: 8px"
            :loading="comparing"
            @click="handleCompare"
          >
            <diff-outlined /> 对比差异
          </a-button>
        </a-col>

        <a-col :span="16">
          <a-space style="margin-bottom: 12px">
            <a-select
              v-model:value="editNodeId"
              style="width: 180px"
              placeholder="编辑节点"
              :disabled="!selectedFileId"
            >
              <a-select-option v-for="n in projectNodes" :key="n.id" :value="Number(n.id)">
                {{ n.name }}
              </a-select-option>
            </a-select>
            <a-button :disabled="!editNodeId" :loading="contentLoading" @click="loadContent">
              <search-outlined /> 读取
            </a-button>
            <a-button type="primary" :disabled="!editNodeId" :loading="distributing" @click="showDistribute">
              <cloud-upload-outlined /> 分发
            </a-button>
          </a-space>
          <a-alert
            v-if="contentError"
            type="warning"
            :message="contentError"
            closable
            style="margin-bottom: 8px"
            @close="contentError = ''"
          />
          <a-space v-if="contentSource.type !== 'none'" style="margin-bottom: 8px">
            <a-tag v-if="contentSource.type === 'read'" color="blue">
              已读取节点: {{ contentSource.nodeName || contentSource.nodeId }}
            </a-tag>
            <a-tag v-else-if="contentSource.type === 'manual'" color="orange">
              手动编辑中 — 未从节点 {{ projectNodes.find(n => Number(n.id) === editNodeId)?.name || editNodeId }} 读取
            </a-tag>
          </a-space>
          <a-textarea
              v-model:value="content"
              :rows="22"
              placeholder="选择配置文件和节点后点击「读取」获取远程内容，也可直接手动输入内容后分发..."
              class="config-editor"
            />
        </a-col>
      </a-row>
    </a-card>

    <!-- 新增配置文件 -->
    <a-modal v-model:open="addFileVisible" title="新增配置文件" @ok="handleAddFile">
      <a-form layout="vertical">
        <a-form-item label="文件名" required>
          <a-input v-model:value="newFile.fileName" placeholder="application.yml" />
        </a-form-item>
        <a-form-item label="相对路径" required>
          <a-input v-model:value="newFile.relativePath" placeholder="config/application.yml" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="newFile.remark" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分发 -->
    <a-modal
      v-model:open="distributeVisible"
      :title="distributeResult ? '分发结果' : '分发配置'"
      :ok-text="distributeResult ? '关闭' : '开始分发'"
      :cancel-text="distributeResult ? undefined : '取消'"
      :cancel-button-props="distributeResult ? { style: { display: 'none' } } : {}"
      @ok="distributeResult ? distributeVisible = false : handleDistribute()"
      @cancel="distributeResult = null"
    >
      <template v-if="!distributeResult">
        <a-alert
          v-if="contentSource.type === 'manual'"
          type="warning"
          message="当前内容为手动编辑，未从目标节点读取。建议先点击「读取」获取节点当前配置后再修改。"
          style="margin-bottom: 12px"
        />
        <a-form layout="vertical">
          <a-form-item label="内容来源">
            <a-tag v-if="contentSource.type === 'read'" color="blue">已读取: {{ contentSource.nodeName }}</a-tag>
            <a-tag v-else-if="contentSource.type === 'manual'" color="orange">手动编辑</a-tag>
            <span v-else style="color: #999">无</span>
            <a-tag v-if="editNodeId && projectNodes.find(n => Number(n.id) === editNodeId)?.name !== contentSource.nodeName" style="margin-left: 4px">
              编辑节点: {{ projectNodes.find(n => Number(n.id) === editNodeId)?.name }}
            </a-tag>
          </a-form-item>
          <a-form-item label="目标节点">
            <a-checkbox-group v-model:value="distributeNodeIds" :options="nodeOptions" />
          </a-form-item>
          <a-form-item>
            <a-checkbox v-model:checked="restartAfter">分发后重启进程</a-checkbox>
          </a-form-item>
        </a-form>
      </template>
      <template v-else>
        <a-alert
          :type="distributeResult.status === 1 ? 'success' : distributeResult.status === 3 ? 'error' : 'warning'"
          :message="distributeResult.status === 1 ? '全部分发成功' : distributeResult.status === 3 ? '全部分发失败' : '部分分发成功'"
          style="margin-bottom: 12px"
        />
        <a-table
          :columns="resultColumns"
          :data-source="distributeResult.results"
          row-key="nodeId"
          size="small"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'nodeId'">
              {{ projectNodes.find(n => Number(n.id) === record.nodeId)?.name || record.nodeId }}
            </template>
            <template v-if="column.key === 'result'">
              <a-tag :color="record.success ? 'green' : 'red'">
                {{ record.success ? '成功' : '失败' }}
              </a-tag>
            </template>
            <template v-if="column.key === 'restart'">
              <template v-if="record.restarted">
                <a-tag v-if="record.restartSuccess" color="green">已重启</a-tag>
                <a-tag v-else color="red">重启失败</a-tag>
              </template>
              <span v-else style="color: #999">—</span>
            </template>
          </template>
        </a-table>
      </template>
    </a-modal>

    <!-- 对比结果 -->
    <a-modal v-model:open="diffVisible" title="节点配置对比" :footer="null" width="640px">
      <div v-for="d in diffResults" :key="d.nodeId" class="diff-block">
        <a-tag :color="d.identical ? 'green' : 'red'">
          {{ d.nodeName || d.nodeId }} — {{ d.identical ? '一致' : '有差异' }}
        </a-tag>
        <pre v-if="d.diffLines?.length" class="diff-lines">{{ d.diffLines.join('\n') }}</pre>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, unref } from 'vue'
import { message } from 'ant-design-vue'
import type { ProjectModel, NodeModel, ProjectConfigFileModel, NodeConfigSnapshotModel, ConfigSnapshotResult } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import {
  listConfigFiles, createConfigFile, deleteConfigFile,
  getConfigSnapshot, getConfigContent, compareConfig,
  distributeConfig, refreshConfigSnapshots
} from '../api/configMgmt'
import {
  SettingOutlined, PlusOutlined, ReloadOutlined, SearchOutlined,
  CloudUploadOutlined, DiffOutlined
} from '@ant-design/icons-vue'

const projects = ref<ProjectModel[]>([])
const allNodes = ref<NodeModel[]>([])
const projectId = ref<number>()
const configFiles = ref<ProjectConfigFileModel[]>([])
const selectedFileId = ref<number>()
const snapshots = ref<NodeConfigSnapshotModel[]>([])
const content = ref('')
const contentError = ref('')
const contentSource = ref<{ type: 'read' | 'manual' | 'none'; nodeId?: number; nodeName?: string }>({ type: 'none' })
const editNodeId = ref<number>()
const filesLoading = ref(false)
const snapshotLoading = ref(false)
const contentLoading = ref(false)
const refreshing = ref(false)
const comparing = ref(false)
const distributing = ref(false)
const addFileVisible = ref(false)
const distributeVisible = ref(false)
const diffVisible = ref(false)
const diffResults = ref<Array<{ nodeId: number; nodeName?: string; identical: boolean; diffLines?: string[] }>>([])
const distributeNodeIds = ref<number[]>([])
const restartAfter = ref(false)
const distributeResult = ref<{ status: number; results: Array<{ nodeId: number; success: boolean; error?: string }> } | null>(null)
const newFile = ref({ fileName: '', relativePath: '', remark: '' })

const fileColumns = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '路径', dataIndex: 'relativePath', key: 'relativePath', ellipsis: true },
  { title: '类型', key: 'isPrimary', width: 80 },
  { title: '操作', key: 'action', width: 60 }
]
const snapshotColumns = [
  { title: '节点', dataIndex: 'nodeName', key: 'nodeName' },
  { title: '哈希', dataIndex: 'contentHash', key: 'contentHash', ellipsis: true, width: 100 },
  { title: '状态', key: 'syncStatus', width: 80 },
  { title: '操作', key: 'action', width: 60 }
]
const resultColumns = [
  { title: '节点', dataIndex: 'nodeId', key: 'nodeId' },
  { title: '分发', key: 'result', width: 100 },
  { title: '重启', key: 'restart', width: 100 },
  { title: '详情', dataIndex: 'error', key: 'error', ellipsis: true }
]

const projectNodes = computed(() => {
  // 兼容 ref 与 setupState reactive 解包后的数组两种形态（避免 .value 在解包下取到 undefined）
  const ao = unref(allNodes)
  const pv = unref(projects)
  const pid = unref(projectId)
  if (!pid) return []
  const proj = (pv || []).find(p => Number(p.id) === Number(pid))
  if (!proj?.nodeIds) return []
  const ids = String(proj.nodeIds).split(',').map(s => Number(s.trim()))
  return (ao || []).filter(n => ids.includes(Number(n.id)))
})

const nodeOptions = computed(() =>
  projectNodes.value.map(n => ({ label: n.name, value: Number(n.id) }))
)

function syncColor(s?: number) {
  return ({ 1: 'green', 2: 'red', 3: 'orange' } as Record<number, string>)[s || 0] || 'default'
}
function syncLabel(s?: number) {
  return ({ 0: '未知', 1: '一致', 2: '差异', 3: '定制' } as Record<number, string>)[s || 0] || '未知'
}

function fileRowClass(record: ProjectConfigFileModel) {
  return record.id === selectedFileId.value ? 'selected-row' : ''
}
function fileCustomRow(record: ProjectConfigFileModel) {
  return { onClick: () => selectFile(record) }
}

async function fetchProjects() {
  const [pRes, nRes] = await Promise.all([getProjects(1, 100), getNodes(1, 200)])
  projects.value = pRes.data.list || []
  allNodes.value = nRes.data.list || []
}

async function onProjectChange() {
  selectedFileId.value = undefined
  configFiles.value = []
  snapshots.value = []
  content.value = ''
  contentError.value = ''
  contentSource.value = { type: 'none' }
  if (!projectId.value) return
  filesLoading.value = true
  try {
    const res = await listConfigFiles(projectId.value)
    configFiles.value = res.data || []
  } finally {
    filesLoading.value = false
  }
}

async function selectFile(file: ProjectConfigFileModel) {
  selectedFileId.value = file.id
  contentError.value = ''
  snapshotLoading.value = true
  try {
    const res = await getConfigSnapshot(projectId.value!, file.id!)
    const data: ConfigSnapshotResult = res.data
    snapshots.value = (data.nodes || []).map((s: NodeConfigSnapshotModel) => ({
      ...s,
      nodeName: projectNodes.value.find(n => Number(n.id) === s.nodeId)?.name
    }))
  } finally {
    snapshotLoading.value = false
  }
}

async function loadNodeContent(nodeId: number) {
  editNodeId.value = nodeId
  await loadContent()
}

async function loadContent() {
  if (!projectId.value || !editNodeId.value || !selectedFileId.value) return
  contentLoading.value = true
  contentError.value = ''
  try {
    const res = await getConfigContent(projectId.value, editNodeId.value, selectedFileId.value)
    content.value = res.data || ''
    const node = projectNodes.value.find(n => Number(n.id) === editNodeId.value)
    contentSource.value = { type: 'read', nodeId: editNodeId.value, nodeName: node?.name }
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '读取失败'
    contentError.value = msg + '。你可以直接在下方编辑区手动输入配置内容后分发。'
    const node = projectNodes.value.find(n => Number(n.id) === editNodeId.value)
    contentSource.value = { type: 'manual', nodeId: editNodeId.value, nodeName: node?.name }
  } finally {
    contentLoading.value = false
  }
}

function showAddFile() {
  newFile.value = { fileName: '', relativePath: '', remark: '' }
  addFileVisible.value = true
}

async function handleAddFile() {
  if (!projectId.value || !newFile.value.fileName) return
  await createConfigFile({
    projectId: projectId.value,
    fileName: newFile.value.fileName,
    relativePath: newFile.value.relativePath || newFile.value.fileName,
    remark: newFile.value.remark
  })
  addFileVisible.value = false
  message.success('已添加')
  onProjectChange()
}

async function handleDeleteFile(id: number) {
  await deleteConfigFile(id, projectId.value!)
  message.success('已删除')
  onProjectChange()
}

async function handleRefresh() {
  refreshing.value = true
  try {
    await refreshConfigSnapshots(projectId.value!, selectedFileId.value!)
    message.success('快照已刷新')
    const file = configFiles.value.find(f => f.id === selectedFileId.value)
    if (file) await selectFile(file)
  } finally {
    refreshing.value = false
  }
}

// 静默刷新快照（不分发后不弹提示）
async function refreshSnapshotsSilently() {
  try {
    await refreshConfigSnapshots(projectId.value!, selectedFileId.value!)
    const file = configFiles.value.find(f => f.id === selectedFileId.value)
    if (file) await selectFile(file)
  } catch {
    // 静默忽略
  }
}

async function handleCompare() {
  if (snapshots.value.length < 2) return
  comparing.value = true
  try {
    const baseNodeId = snapshots.value[0].nodeId
    const targetNodeIds = snapshots.value.slice(1).map(s => s.nodeId)
    const res = await compareConfig({
      projectId: projectId.value!,
      configFileId: selectedFileId.value!,
      baseNodeId,
      targetNodeIds
    })
    diffResults.value = res.data?.diffs || []
    diffVisible.value = true
  } finally {
    comparing.value = false
  }
}

function showDistribute() {
  // 默认只选当前编辑节点，避免误分发到所有节点
  distributeNodeIds.value = editNodeId.value ? [editNodeId.value] : []
  distributeResult.value = null
  distributeVisible.value = true
}

async function handleDistribute() {
  if (!distributeNodeIds.value.length) return
  distributing.value = true
  distributeResult.value = null
  try {
    const res = await distributeConfig({
      projectId: projectId.value!,
      configFileId: selectedFileId.value!,
      content: content.value,
      targetNodeIds: distributeNodeIds.value,
      distributeType: distributeNodeIds.value.length > 1 ? 'BATCH' : 'SINGLE',
      restartAfter: restartAfter.value
    })
    // 展示分发结果
    distributeResult.value = res.data
    // 分发后自动刷新节点快照
    if (res.data?.status === 1 || res.data?.status === 2) {
      refreshSnapshotsSilently()
    }
    if (res.data?.status === 1) {
      message.success('全部分发成功')
    } else if (res.data?.status === 3) {
      message.error('全部分发失败')
    } else {
      message.warning('部分分发成功')
    }
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '分发失败')
  } finally {
    distributing.value = false
  }
}

// 切换编辑节点时清空所有状态
watch(editNodeId, (newId, oldId) => {
  if (!newId || newId === oldId) return
  content.value = ''
  contentError.value = ''
  contentSource.value = { type: 'none' }
})

// 监听手动编辑：用户修改 textarea 内容时，如果之前是从节点读取的，标记为已修改
watch(content, (newVal, oldVal) => {
  if (contentSource.value.type === 'read' && newVal !== oldVal && contentSource.value.nodeId) {
    contentSource.value = { ...contentSource.value, type: 'manual' }
  }
})

onMounted(fetchProjects)
</script>

<style scoped>
.config-editor {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
}
:deep(.selected-row) { background: rgba(114, 46, 209, 0.1) !important; }
.diff-block { margin-bottom: 12px; }
.diff-lines {
  background: #1a1a1a;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-top: 4px;
  max-height: 200px;
  overflow: auto;
}
</style>
