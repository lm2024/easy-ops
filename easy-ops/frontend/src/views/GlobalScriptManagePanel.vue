<template>
  <div class="global-script-panel">
    <!-- 顶部操作栏 -->
    <div class="script-toolbar">
      <a-space>
        <a-input
          v-model:value="scanDir"
          placeholder="输入扫描目录，如 /app/scripts"
          style="width: 300px"
          allow-clear
        />
        <a-button type="primary" @click="handleScan" :loading="scanning">
          <template #icon><SearchOutlined /></template>
          扫描所有节点
        </a-button>
        <a-button @click="showAddModal">
          <template #icon><PlusOutlined /></template>
          手动添加
        </a-button>
        <a-button @click="loadFiles" :loading="loading">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </a-space>
    </div>

    <!-- 脚本文件列表 -->
    <a-table
      :dataSource="scriptFiles"
      :columns="columns"
      :loading="loading"
      row-key="id"
      size="small"
      :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 个文件` }"
      @row-click="selectFile"
      :rowClassName="(record: any) => selectedFile?.id === record.id ? 'ant-table-row-selected' : ''"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'fileType'">
          <a-tag :color="getFileTypeColor(record.fileType)">
            {{ record.fileType || 'other' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'isExecutable'">
          <a-tag v-if="record.isExecutable" color="green">可执行</a-tag>
          <a-tag v-else color="default">普通</a-tag>
        </template>
        <template v-if="column.key === 'syncStatus'">
          <a-tag :color="getSyncStatusColor(record.id)">
            {{ getSyncStatusLabel(record.id) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click.stop="handleEdit(record)">编辑</a>
            <a-popconfirm
              title="确定删除此脚本文件定义？"
              description="删除后不会影响节点上的实际文件"
              @confirm="handleDelete(record)"
              @cancel.stop
            >
              <a @click.stop style="color: #ff4d4f">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 编辑器区域 -->
    <div v-if="selectedFile" class="script-editor-section">
      <a-divider />
      
      <!-- 文件信息头 -->
      <div class="editor-header">
        <div class="file-info">
          <FileTextOutlined style="font-size: 18px; margin-right: 8px" />
          <span class="file-name">{{ selectedFile.fileName }}</span>
          <a-tag :color="getFileTypeColor(selectedFile.fileType)" style="margin-left: 8px">
            {{ selectedFile.fileType || 'other' }}
          </a-tag>
          <span class="file-path">{{ selectedFile.filePath }}</span>
        </div>
        <a-space>
          <a-button size="small" @click="loadNodeConfigStatus" :loading="loadingStatus">
            <template #icon><SyncOutlined /></template>
            刷新状态
          </a-button>
          <a-button size="small" @click="loadContentAuto" :loading="loadingContent">
            <template #icon><CloudDownloadOutlined /></template>
            读取内容
          </a-button>
        </a-space>
      </div>

      <!-- 节点状态条 -->
      <div class="node-status-bar" v-if="nodeConfigStatus.length > 0">
        <span class="status-label">节点状态：</span>
        <div class="node-chips">
          <div
            v-for="node in nodeConfigStatus"
            :key="node.nodeId"
            class="node-chip"
            :class="getNodeChipClass(node)"
            @click="loadContentFromNode(node.nodeId)"
            :title="`${node.nodeName} (${node.nodeIp}): ${getNodeSyncStatusLabel(node.syncStatus)}${node.contentHash ? ' (hash: ' + node.contentHash.substring(0, 8) + '...)' : ''}`"
          >
            <span class="node-name">{{ node.nodeName }}</span>
            <span class="node-status-dot" :style="{ backgroundColor: getNodeDotColor(node) }"></span>
            <a-tag v-if="node.nodeStatus !== 1" color="default" size="small" style="margin-left: 4px">离线</a-tag>
          </div>
        </div>
        <a-tag v-if="allSame" color="success" style="margin-left: 12px">全部一致</a-tag>
        <a-tag v-else color="warning" style="margin-left: 12px">存在差异</a-tag>
      </div>

      <!-- 加载状态 -->
      <a-spin v-if="loadingContent" tip="正在读取配置内容...">
        <div style="height: 200px" />
      </a-spin>

      <!-- 内容编辑器 -->
      <div v-else class="editor-container">
        <div v-if="editError" class="edit-error">
          <a-alert :message="editError" type="error" show-icon />
        </div>
        <div v-else-if="!editContent && editContent !== ''" class="edit-placeholder">
          <a-empty description="点击「读取内容」加载脚本文件" />
        </div>
        <div v-else class="editor-wrapper">
          <a-textarea
            v-model:value="editContent"
            :placeholder="`编辑 ${selectedFile.fileName} 内容...`"
            :auto-size="{ minRows: 15, maxRows: 40 }"
            class="config-editor"
            @change="onContentChange"
          />
          <div class="editor-footer">
            <span class="char-count">{{ editContent.length }} 字符</span>
            <span v-if="editNodeId" class="source-node">
              来源: {{ getNodeName(editNodeId) }}
            </span>
          </div>
        </div>
      </div>

      <!-- 分发面板 -->
      <div class="distribute-panel">
        <a-divider orientation="left">分发到节点</a-divider>
        <div class="distribute-options">
          <div class="node-checkboxes">
            <span class="option-label">目标节点：</span>
            <a-checkbox-group v-model:value="distributeNodeIds">
              <a-checkbox
                v-for="node in allNodes"
                :key="node.id"
                :value="Number(node.id)"
                :disabled="node.status !== 1"
              >
                <span :class="{ 'offline-node': node.status !== 1 }">
                  {{ node.name }} ({{ node.ip }})
                  <a-tag v-if="node.status !== 1" color="default" size="small">离线</a-tag>
                </span>
              </a-checkbox>
            </a-checkbox-group>
            <a-button type="link" size="small" @click="selectAllOnlineNodes">全选在线</a-button>
            <a-button type="link" size="small" @click="distributeNodeIds = []">清空</a-button>
          </div>
          <div class="distribute-actions">
            <a-checkbox v-model:checked="setExecutable">设置可执行权限</a-checkbox>
            <a-checkbox v-model:checked="autoBackup">分发前备份</a-checkbox>
            <a-button
              type="primary"
              @click="handleDistribute"
              :loading="distributing"
              :disabled="distributeNodeIds.length === 0"
            >
              <template #icon><SendOutlined /></template>
              保存并分发
            </a-button>
          </div>
        </div>

        <!-- 分发结果 -->
        <div v-if="distributeResult" class="distribute-result">
          <a-alert
            :type="distributeResult.failCount === 0 ? 'success' : 'warning'"
            :message="`分发完成: 成功 ${distributeResult.successCount} 个, 失败 ${distributeResult.failCount} 个`"
            show-icon
          />
          <div class="result-details" v-if="distributeResult.results?.length">
            <div
              v-for="r in distributeResult.results"
              :key="r.nodeId"
              class="result-item"
            >
              <a-tag :color="r.success ? 'success' : 'error'">
                {{ r.nodeName || `节点${r.nodeId}` }}
              </a-tag>
              <span v-if="!r.success" class="error-msg">{{ r.error }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑脚本文件弹窗 -->
    <a-modal
      v-model:open="addModalVisible"
      :title="editingFile ? '编辑脚本文件' : '添加脚本文件'"
      @ok="handleAddFile"
      @cancel="resetAddForm"
      :confirmLoading="saving"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="文件路径" required>
          <a-input
            v-model:value="addForm.filePath"
            placeholder="Agent 节点上的绝对路径，如 /app/scripts/start.sh"
          />
        </a-form-item>
        <a-form-item label="文件名">
          <a-input
            v-model:value="addForm.fileName"
            placeholder="文件名（可自动从路径提取）"
          />
        </a-form-item>
        <a-form-item label="文件类型">
          <a-select v-model:value="addForm.fileType" placeholder="选择文件类型">
            <a-select-option value="sh">Shell 脚本 (.sh)</a-select-option>
            <a-select-option value="conf">配置文件 (.conf)</a-select-option>
            <a-select-option value="yaml">YAML (.yml/.yaml)</a-select-option>
            <a-select-option value="properties">Properties (.properties)</a-select-option>
            <a-select-option value="service">Systemd 服务 (.service)</a-select-option>
            <a-select-option value="cron">定时任务 (.cron)</a-select-option>
            <a-select-option value="xml">XML (.xml)</a-select-option>
            <a-select-option value="json">JSON (.json)</a-select-option>
            <a-select-option value="other">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model:value="addForm.description"
            placeholder="文件用途描述，如：Agent 启动脚本"
            :rows="2"
          />
        </a-form-item>
        <a-form-item label="可执行权限">
          <a-switch v-model:checked="addForm.isExecutable" />
          <span style="margin-left: 8px; color: #999">分发时自动设置 755 权限</span>
        </a-form-item>
        <a-form-item label="自动备份">
          <a-switch v-model:checked="addForm.autoBackup" />
          <span style="margin-left: 8px; color: #999">分发前自动备份原文件</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  SearchOutlined,
  PlusOutlined,
  ReloadOutlined,
  FileTextOutlined,
  SyncOutlined,
  CloudDownloadOutlined,
  SendOutlined
} from '@ant-design/icons-vue'
import {
  listGlobalScriptFiles,
  createGlobalScriptFile,
  updateGlobalScriptFile,
  deleteGlobalScriptFile,
  scanGlobalScriptFiles,
  getGlobalScriptContentAuto,
  getGlobalScriptContent,
  getGlobalScriptSnapshot,
  distributeGlobalScript
} from '../api/globalScriptMgmt'
import { getNodes } from '../api/node'
import type {
  GlobalScriptFileModel,
  GlobalScriptSnapshotResult,
  GlobalNodeScriptSnapshotModel,
  ScriptDistributeResult
} from '../types'

// ==================== 状态 ====================
const loading = ref(false)
const scanning = ref(false)
const saving = ref(false)
const loadingContent = ref(false)
const loadingStatus = ref(false)
const distributing = ref(false)

const scriptFiles = ref<GlobalScriptFileModel[]>([])
const selectedFile = ref<GlobalScriptFileModel | null>(null)
const scanDir = ref('')

const editContent = ref<string>('')
const editNodeId = ref<number | null>(null)
const editError = ref<string | null>(null)
const contentModified = ref(false)

const nodeConfigStatus = ref<GlobalNodeScriptSnapshotModel[]>([])
const allSame = ref(false)

const distributeNodeIds = ref<number[]>([])
const setExecutable = ref(false)
const autoBackup = ref(true)
const distributeResult = ref<ScriptDistributeResult | null>(null)

const addModalVisible = ref(false)
const editingFile = ref<GlobalScriptFileModel | null>(null)
const addForm = reactive<GlobalScriptFileModel>({
  fileName: '',
  filePath: '',
  fileType: 'sh',
  description: '',
  isExecutable: 0,
  autoBackup: 1
})

// 所有节点列表
const allNodes = ref<any[]>([])

// 文件同步状态缓存
const fileSyncStatus = ref<Record<number, { status: number; label: string }>>({})

// ==================== 表格列定义 ====================
const columns = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', width: 150 },
  { title: '路径', dataIndex: 'filePath', key: 'filePath', ellipsis: true },
  { title: '类型', dataIndex: 'fileType', key: 'fileType', width: 80 },
  { title: '权限', dataIndex: 'isExecutable', key: 'isExecutable', width: 80 },
  { title: '同步状态', key: 'syncStatus', width: 100 },
  { title: '操作', key: 'action', width: 120 }
]

// ==================== 方法 ====================
async function loadNodes() {
  try {
    const res = await getNodes(1, 1000)
    allNodes.value = res.data?.list || []
  } catch {
    allNodes.value = []
  }
}

async function loadFiles() {
  loading.value = true
  try {
    const res = await listGlobalScriptFiles()
    if (res.code === 200) {
      scriptFiles.value = res.data || []
      // 加载每个文件的同步状态
      for (const file of scriptFiles.value) {
        if (file.id) {
          loadFileSyncStatus(file.id)
        }
      }
    }
  } catch (e: any) {
    message.error('加载脚本文件列表失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function loadFileSyncStatus(fileId: number) {
  try {
    const res = await getGlobalScriptSnapshot(fileId)
    if (res.code === 200 && res.data) {
      const snapResult = res.data as GlobalScriptSnapshotResult
      fileSyncStatus.value[fileId] = {
        status: snapResult.allSame ? 1 : 2,
        label: snapResult.allSame ? '一致' : '差异'
      }
    }
  } catch {
    fileSyncStatus.value[fileId] = { status: 0, label: '未知' }
  }
}

async function handleScan() {
  if (!scanDir.value.trim()) {
    message.warning('请输入扫描目录')
    return
  }
  scanning.value = true
  try {
    const res = await scanGlobalScriptFiles(scanDir.value.trim())
    if (res.code === 200) {
      scriptFiles.value = res.data || []
      message.success(`扫描完成，发现 ${scriptFiles.value.length} 个脚本文件`)
      if (scriptFiles.value.length > 0 && !selectedFile.value) {
        selectFile(scriptFiles.value[0])
      }
    } else {
      message.error(res.message || '扫描失败')
    }
  } catch (e: any) {
    message.error('扫描失败: ' + e.message)
  } finally {
    scanning.value = false
  }
}

function selectFile(file: GlobalScriptFileModel) {
  if (contentModified.value && selectedFile.value) {
    // 提示保存
  }
  selectedFile.value = file
  editContent.value = ''
  editNodeId.value = null
  editError.value = null
  contentModified.value = false
  distributeResult.value = null
  loadNodeConfigStatus()
  loadContentAuto()
}

async function loadContentAuto() {
  if (!selectedFile.value?.id) return
  loadingContent.value = true
  editError.value = null
  try {
    const res = await getGlobalScriptContentAuto(selectedFile.value.id)
    if (res.code === 200 && res.data) {
      editContent.value = res.data.content || ''
      editNodeId.value = res.data.nodeId
      contentModified.value = false
    } else {
      editError.value = res.message || '读取失败'
    }
  } catch (e: any) {
    editError.value = '读取失败: ' + e.message
  } finally {
    loadingContent.value = false
  }
}

async function loadContentFromNode(nodeId: number) {
  if (!selectedFile.value?.id) return
  loadingContent.value = true
  editError.value = null
  try {
    const res = await getGlobalScriptContent(nodeId, selectedFile.value.id)
    if (res.code === 200) {
      editContent.value = res.data || ''
      editNodeId.value = nodeId
      contentModified.value = false
    } else {
      editError.value = res.message || '读取失败'
    }
  } catch (e: any) {
    editError.value = '读取失败: ' + e.message
  } finally {
    loadingContent.value = false
  }
}

async function loadNodeConfigStatus() {
  if (!selectedFile.value?.id) return
  loadingStatus.value = true
  try {
    const res = await getGlobalScriptSnapshot(selectedFile.value.id)
    if (res.code === 200 && res.data) {
      const data = res.data as GlobalScriptSnapshotResult
      nodeConfigStatus.value = data.nodes || []
      allSame.value = data.allSame
    }
  } catch {
    nodeConfigStatus.value = []
  } finally {
    loadingStatus.value = false
  }
}

async function handleDistribute() {
  if (!selectedFile.value?.id) {
    message.warning('请先选择脚本文件')
    return
  }
  if (distributeNodeIds.value.length === 0) {
    message.warning('请选择目标节点')
    return
  }
  if (!editContent.value && editContent.value !== '') {
    message.warning('请先读取脚本内容')
    return
  }

  distributing.value = true
  try {
    const res = await distributeGlobalScript({
      scriptFileId: selectedFile.value.id,
      content: editContent.value,
      targetNodeIds: distributeNodeIds.value,
      setExecutable: setExecutable.value,
      autoBackup: autoBackup.value
    })
    if (res.code === 200 && res.data) {
      distributeResult.value = res.data as ScriptDistributeResult
      if ((res.data as ScriptDistributeResult).failCount === 0) {
        message.success('分发成功')
      } else {
        message.warning(`分发完成，${(res.data as ScriptDistributeResult).failCount} 个节点失败`)
      }
      // 刷新状态
      loadNodeConfigStatus()
      loadFileSyncStatus(selectedFile.value.id)
      contentModified.value = false
    } else {
      message.error(res.message || '分发失败')
    }
  } catch (e: any) {
    message.error('分发失败: ' + e.message)
  } finally {
    distributing.value = false
  }
}

function onContentChange() {
  contentModified.value = true
  distributeResult.value = null
}

function showAddModal() {
  editingFile.value = null
  resetAddForm()
  addModalVisible.value = true
}

function handleEdit(file: GlobalScriptFileModel) {
  editingFile.value = file
  Object.assign(addForm, {
    fileName: file.fileName,
    filePath: file.filePath,
    fileType: file.fileType || 'other',
    description: file.description || '',
    isExecutable: file.isExecutable ? true : false,
    autoBackup: file.autoBackup !== 0
  })
  addModalVisible.value = true
}

async function handleAddFile() {
  if (!addForm.filePath.trim()) {
    message.warning('请输入文件路径')
    return
  }
  if (!addForm.fileName.trim()) {
    // 自动从路径提取文件名
    const parts = addForm.filePath.replace(/\\/g, '/').split('/')
    addForm.fileName = parts[parts.length - 1] || 'unknown'
  }

  saving.value = true
  try {
    const model: GlobalScriptFileModel = {
      ...addForm,
      isExecutable: addForm.isExecutable ? 1 : 0,
      autoBackup: addForm.autoBackup ? 1 : 0
    }

    let res
    if (editingFile.value?.id) {
      res = await updateGlobalScriptFile(editingFile.value.id, model)
    } else {
      res = await createGlobalScriptFile(model)
    }

    if (res.code === 200) {
      message.success(editingFile.value ? '更新成功' : '添加成功')
      addModalVisible.value = false
      resetAddForm()
      loadFiles()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch (e: any) {
    message.error('操作失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

async function handleDelete(file: GlobalScriptFileModel) {
  if (!file.id) return
  try {
    const res = await deleteGlobalScriptFile(file.id)
    if (res.code === 200) {
      message.success('删除成功')
      if (selectedFile.value?.id === file.id) {
        selectedFile.value = null
      }
      loadFiles()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (e: any) {
    message.error('删除失败: ' + e.message)
  }
}

function resetAddForm() {
  Object.assign(addForm, {
    fileName: '',
    filePath: '',
    fileType: 'sh',
    description: '',
    isExecutable: 0,
    autoBackup: 1
  })
}

function selectAllOnlineNodes() {
  distributeNodeIds.value = allNodes.value
    .filter(n => n.status === 1)
    .map(n => Number(n.id))
}

function getNodeName(nodeId: number): string {
  const node = allNodes.value.find(n => Number(n.id) === nodeId)
  return node?.name || `节点${nodeId}`
}

function getFileTypeColor(type?: string): string {
  const colors: Record<string, string> = {
    sh: 'blue',
    conf: 'cyan',
    yaml: 'green',
    properties: 'orange',
    service: 'purple',
    cron: 'magenta',
    xml: 'gold',
    json: 'lime'
  }
  return colors[type || ''] || 'default'
}

function getSyncStatusColor(fileId?: number): string {
  if (!fileId) return 'default'
  const status = fileSyncStatus.value[fileId]
  if (!status) return 'default'
  if (status.status === 1) return 'success'
  if (status.status === 2) return 'warning'
  return 'default'
}

function getSyncStatusLabel(fileId?: number): string {
  if (!fileId) return '未知'
  return fileSyncStatus.value[fileId]?.label || '未知'
}

function getNodeChipClass(node: any): string {
  if (node.nodeStatus !== 1) return 'chip-offline'
  if (node.syncStatus === 1) return 'chip-success'
  if (node.syncStatus === 2) return 'chip-warning'
  return 'chip-default'
}

function getNodeDotColor(node: any): string {
  if (node.nodeStatus !== 1) return '#d9d9d9'
  if (node.syncStatus === 1) return '#52c41a'
  if (node.syncStatus === 2) return '#faad14'
  return '#d9d9d9'
}

function getNodeSyncStatusLabel(syncStatus?: number): string {
  if (syncStatus === 1) return '一致'
  if (syncStatus === 2) return '差异'
  if (syncStatus === 3) return '定制'
  return '未知'
}

// ==================== 初始化 ====================
onMounted(() => {
  loadNodes()
  loadFiles()
})
</script>

<style scoped>
.global-script-panel {
  padding: 0;
}

.script-toolbar {
  margin-bottom: 16px;
}

.script-editor-section {
  margin-top: 16px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.file-info {
  display: flex;
  align-items: center;
}

.file-name {
  font-weight: 600;
  font-size: 16px;
}

.file-path {
  color: #999;
  margin-left: 12px;
  font-size: 13px;
}

.node-status-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
}

.status-label {
  color: #666;
  margin-right: 8px;
}

.node-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.node-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 12px;
}

.node-chip:hover {
  opacity: 0.8;
}

.chip-success {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.chip-warning {
  background: #fffbe6;
  border: 1px solid #ffe58f;
}

.chip-default {
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
}

.chip-offline {
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
  opacity: 0.6;
}

.node-name {
  margin-right: 4px;
}

.node-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.editor-container {
  margin-bottom: 16px;
}

.editor-wrapper {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
}

.config-editor {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  border: none;
  border-radius: 0;
}

.config-editor:focus {
  box-shadow: none;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  padding: 4px 12px;
  background: #fafafa;
  border-top: 1px solid #d9d9d9;
  font-size: 12px;
  color: #999;
}

.edit-error {
  margin-bottom: 16px;
}

.edit-placeholder {
  padding: 40px;
  text-align: center;
}

.distribute-panel {
  margin-top: 16px;
}

.distribute-options {
  margin-bottom: 16px;
}

.node-checkboxes {
  margin-bottom: 12px;
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
}

.option-label {
  margin-right: 8px;
  white-space: nowrap;
}

.distribute-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.offline-node {
  color: #999;
}

.distribute-result {
  margin-top: 12px;
}

.result-details {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.result-item {
  display: flex;
  align-items: center;
}

.error-msg {
  color: #ff4d4f;
  font-size: 12px;
  margin-left: 4px;
}

:deep(.ant-table-row-selected) {
  background: #e6f7ff !important;
}
</style>
