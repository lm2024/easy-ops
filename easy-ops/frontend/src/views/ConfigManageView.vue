<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <setting-outlined style="color: #1890ff" />
          <span style="font-weight: 600">配置管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-select
            v-model:value="selectedProjectId"
            style="width: 220px"
            placeholder="选择应用"
            allow-clear
            @change="onProjectChange"
          >
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
              {{ p.name }}
            </a-select-option>
          </a-select>
          <a-button type="primary" size="small" :disabled="!selectedProjectId" @click="showAddModal">
            <plus-outlined /> 新建配置
          </a-button>
        </a-space>
      </template>

      <!-- 未选择应用 -->
      <a-empty v-if="!selectedProjectId" description="请先选择一个应用" style="margin: 80px 0" />

      <!-- 已选择应用 -->
      <template v-else>
        <!-- 配置文件列表为空 -->
        <a-empty v-if="!loading && configFiles.length === 0" description="该应用下暂无配置文件，点击「新建配置」添加" style="margin: 80px 0">
          <a-button type="primary" @click="showAddModal"><plus-outlined /> 新建配置</a-button>
        </a-empty>

        <!-- 左右分栏 -->
        <a-row v-else :gutter="16" style="min-height: 500px">
          <!-- 左侧：配置文件列表 -->
          <a-col :span="6">
            <div style="border-right: 1px solid #f0f0f0; padding-right: 12px">
              <div style="font-weight: 500; margin-bottom: 8px; color: #666">配置文件 ({{ configFiles.length }})</div>
              <div
                v-for="file in configFiles"
                :key="file.id"
                class="config-file-item"
                :class="{ active: selectedFile?.id === file.id }"
                @click="selectFile(file)"
              >
                <div style="display: flex; align-items: center; justify-content: space-between">
                  <div>
                    <file-text-outlined style="margin-right: 4px; color: #1890ff" />
                    <span style="font-weight: 500">{{ file.fileName }}</span>
                  </div>
                  <a-space size="small">
                    <a-tooltip title="删除">
                      <a-popconfirm title="确认删除此配置文件定义？" @confirm="handleDeleteFile(file.id!)">
                        <delete-outlined style="color: #ff4d4f; cursor: pointer" />
                      </a-popconfirm>
                    </a-tooltip>
                  </a-space>
                </div>
                <div style="font-size: 11px; color: #999; margin-top: 2px">{{ file.relativePath }}</div>
                <!-- 同步状态 -->
                <div v-if="fileSyncStatus[file.id!]" style="margin-top: 4px">
                  <a-tag :color="fileSyncStatus[file.id!].allSame ? 'green' : 'orange'" size="small">
                    {{ fileSyncStatus[file.id!].syncLabel }}
                  </a-tag>
                </div>
              </div>
            </div>
          </a-col>

          <!-- 右侧：编辑器 -->
          <a-col :span="18">
            <template v-if="selectedFile">
              <!-- 文件名 + 节点配置状态 -->
              <div style="margin-bottom: 8px">
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px">
                  <a-space>
                    <span style="font-weight: 600; font-size: 15px">{{ selectedFile.fileName }}</span>
                    <span style="color: #999; font-size: 12px">{{ selectedFile.relativePath }}</span>
                  </a-space>
                  <a-button size="small" @click="loadContentAuto" :loading="contentLoading">
                    <reload-outlined /> 刷新
                  </a-button>
                </div>

                <!-- 节点配置状态条：一眼看出哪些节点配置一致/不同 -->
                <div style="display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px">
                  <div
                    v-for="n in nodeConfigStatus"
                    :key="n.nodeId"
                    class="node-status-chip"
                    :class="{ active: editNodeId === n.nodeId, online: n.online, offline: !n.online }"
                    @click="switchNode(n)"
                  >
                    <a-badge :status="n.online ? (n.hashOk ? 'success' : 'warning') : 'default'" />
                    <span>{{ n.nodeName }}</span>
                    <span v-if="!n.online" style="color: #999; font-size: 10px">(离线)</span>
                  </div>
                </div>

                <!-- 当前读取状态 -->
                <a-alert v-if="contentSource === 'manual' && editContent" type="warning" show-icon style="margin-bottom: 8px" :banner="true">
                  <template #message>内容已手动修改，分发前请确认</template>
                </a-alert>
                <a-alert v-if="contentError" type="error" show-icon style="margin-bottom: 8px">
                  <template #message>{{ contentError }}</template>
                </a-alert>
                <a-alert v-if="!editContent && !contentLoading && !contentError" type="info" show-icon style="margin-bottom: 8px">
                  <template #message>
                    <span v-if="editNodeId">该节点上此配置文件为空或不存在，请编辑后分发</span>
                    <span v-else>所有节点均无此配置文件，请编辑后分发</span>
                  </template>
                </a-alert>
                <div v-if="editNodeId && editContent && !contentError" style="font-size: 12px; color: #52c41a; margin-bottom: 8px">
                  ✓ 已从 <b>{{ currentEditNodeName }}</b> 读取配置内容
                </div>
              </div>

              <!-- 编辑器 -->
              <a-textarea
                v-model:value="editContent"
                :rows="22"
                placeholder="配置内容..."
                style="font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; line-height: 1.6"
                @input="onContentEdit"
              />

              <!-- 分发面板 -->
              <a-card size="small" style="margin-top: 12px" title="📤 分发配置">
                <a-row :gutter="16" align="middle">
                  <a-col :span="14">
                    <div style="color: #888; font-size: 12px; margin-bottom: 4px">分发到节点：</div>
                    <a-checkbox-group v-model:value="distributeNodeIds">
                      <a-checkbox v-for="n in projectNodes" :key="n.id" :value="n.id">
                        {{ n.name }}
                      </a-checkbox>
                    </a-checkbox-group>
                  </a-col>
                  <a-col :span="4">
                    <a-checkbox v-model:checked="restartAfterDistribute">分发后重启</a-checkbox>
                  </a-col>
                  <a-col :span="6" style="text-align: right">
                    <a-button
                      type="primary"
                      @click="handleDistribute"
                      :loading="distributing"
                      :disabled="distributeNodeIds.length === 0 || !editContent"
                    >
                      <send-outlined /> 保存并分发
                    </a-button>
                  </a-col>
                </a-row>

                <!-- 分发结果 -->
                <div v-if="distributeResult" style="margin-top: 8px">
                  <a-divider style="margin: 8px 0" />
                  <div v-for="(r, i) in distributeResult" :key="i" style="font-size: 12px; margin-bottom: 2px">
                    <check-circle-outlined v-if="r.success" style="color: #52c41a; margin-right: 4px" />
                    <close-circle-outlined v-else style="color: #ff4d4f; margin-right: 4px" />
                    {{ r.nodeName }}: {{ r.message }}
                  </div>
                </div>
              </a-card>
            </template>

            <a-empty v-else description="请从左侧选择一个配置文件" style="margin: 80px 0" />
          </a-col>
        </a-row>
      </template>
    </a-card>

    <!-- 新建配置弹窗 -->
    <a-modal v-model:open="addModalVisible" title="新建配置文件" @ok="handleAddFile" :confirm-loading="addLoading" width="420px">
      <a-form layout="vertical">
        <a-form-item label="配置文件路径（相对于部署目录）" required>
          <a-input v-model:value="newFile.relativePath" placeholder="config/application.yml" size="large" />
        </a-form-item>
        <div style="margin-bottom: 16px">
          <div style="color: #888; font-size: 12px; margin-bottom: 6px">快速选择常用配置：</div>
          <a-space wrap>
            <a-button v-for="preset in pathPresets" :key="preset.path" size="small" @click="applyPreset(preset)">
              {{ preset.label }}
            </a-button>
          </a-space>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { ProjectConfigFileModel } from '../types'
import {
  listConfigFiles, createConfigFile, deleteConfigFile,
  getConfigSnapshot, getConfigContent, getConfigContentAuto, distributeConfig
} from '../api/configMgmt'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import {
  SettingOutlined, PlusOutlined, FileTextOutlined, DeleteOutlined,
  ReloadOutlined, SendOutlined, CheckCircleOutlined, CloseCircleOutlined
} from '@ant-design/icons-vue'

// ====== 状态 ======
const projects = ref<any[]>([])
const selectedProjectId = ref<number>()
const configFiles = ref<ProjectConfigFileModel[]>([])
const loading = ref(false)

// 文件同步状态
const fileSyncStatus = ref<Record<number, { allSame: boolean; syncLabel: string; totalNodes: number; sameCount: number }>>({})

// 编辑器
const selectedFile = ref<ProjectConfigFileModel>()
const editContent = ref('')
const editNodeId = ref<number>()
const contentLoading = ref(false)
const contentSource = ref<'node' | 'manual'>('node')
const contentError = ref('')

// 节点
const projectNodes = ref<any[]>([])

// 分发
const distributeNodeIds = ref<number[]>([])
const restartAfterDistribute = ref(false)
const distributing = ref(false)
const distributeResult = ref<Array<{ nodeName: string; success: boolean; message: string }>>()

// 新建配置
const addModalVisible = ref(false)
const addLoading = ref(false)
const newFile = reactive<ProjectConfigFileModel>({
  projectId: 0,
  fileName: '',
  relativePath: ''
})

// 节点配置状态（用于状态条显示）
const nodeConfigStatus = ref<Array<{
  nodeId: number; nodeName: string; nodeIp: string;
  online: boolean; hashOk: boolean; hash: string
}>>([])

const currentEditNodeName = computed(() => {
  const n = nodeConfigStatus.value.find(n => n.nodeId === editNodeId.value)
  return n?.nodeName || '未知节点'
})

const pathPresets = [
  { name: 'application.yml', path: 'config/application.yml', label: 'application.yml' },
  { name: 'application-dev.yml', path: 'config/application-dev.yml', label: 'application-dev.yml' },
  { name: 'application-prod.yml', path: 'config/application-prod.yml', label: 'application-prod.yml' },
  { name: 'logback-spring.xml', path: 'config/logback-spring.xml', label: 'logback.xml' },
  { name: 'bootstrap.yml', path: 'config/bootstrap.yml', label: 'bootstrap.yml' }
]

// ====== 方法 ======
async function loadProjects() {
  const res = await getProjects()
  projects.value = res.data.list
}

async function loadProjectNodes() {
  if (!selectedProjectId.value) { projectNodes.value = []; return }
  const project = projects.value.find(p => p.id === selectedProjectId.value)
  if (!project || !project.nodeIds) { projectNodes.value = []; return }
  const nodeIds = project.nodeIds.split(',').map((s: string) => s.trim()).filter(Boolean)
  const nodeRes = await getNodes(1, 1000)
  projectNodes.value = nodeRes.data.list.filter((n: any) => nodeIds.includes(String(n.id)))
  // 默认全选
  distributeNodeIds.value = projectNodes.value.map((n: any) => n.id)
}

async function loadConfigFiles() {
  if (!selectedProjectId.value) { configFiles.value = []; return }
  loading.value = true
  try {
    const res = await listConfigFiles(selectedProjectId.value)
    configFiles.value = res.data || []
    // 加载每个文件的同步状态
    for (const file of configFiles.value) {
      if (file.id) loadFileSyncStatus(file.id)
    }
  } finally {
    loading.value = false
  }
}

async function loadFileSyncStatus(fileId: number) {
  if (!selectedProjectId.value) return
  try {
    const res = await getConfigSnapshot(selectedProjectId.value, fileId)
    const data = res.data
    const totalNodes = data.nodes?.length || 0
    const sameCount = data.nodes?.filter((n: any) => n.syncStatus === 1).length || 0
    fileSyncStatus.value[fileId] = {
      allSame: data.allSame,
      totalNodes,
      sameCount,
      syncLabel: data.allSame ? `${totalNodes}/${totalNodes} 同步` : `${sameCount}/${totalNodes} 有差异`
    }
  } catch {
    // ignore
  }
}

async function onProjectChange() {
  selectedFile.value = undefined
  editContent.value = ''
  contentError.value = ''
  distributeResult.value = undefined
  await loadProjectNodes()
  await loadConfigFiles()
  // 自动选中第一个文件
  if (configFiles.value.length > 0) {
    selectFile(configFiles.value[0])
  }
}

async function selectFile(file: ProjectConfigFileModel) {
  selectedFile.value = file
  editContent.value = ''
  contentError.value = ''
  contentSource.value = 'node'
  distributeResult.value = undefined
  // 加载节点配置状态
  await loadNodeConfigStatus(file.id!)
  // 自动从第一个在线节点读取
  await loadContentAuto()
}

async function loadNodeConfigStatus(fileId: number) {
  if (!selectedProjectId.value) return
  try {
    const res = await getConfigSnapshot(selectedProjectId.value, fileId)
    const snapData = res.data
    const nodes = snapData.nodes || []
    const hashes = nodes.map((n: any) => n.contentHash).filter(Boolean)
    const refHash = hashes.length > 0 ? hashes[0] : ''
    nodeConfigStatus.value = projectNodes.value.map((pn: any) => {
      const snap = nodes.find((n: any) => n.nodeId === pn.id)
      return {
        nodeId: pn.id,
        nodeName: pn.name,
        nodeIp: pn.ip,
        online: pn.status === 1,
        hash: snap?.contentHash || '',
        hashOk: snap?.contentHash ? snap.contentHash === refHash : false
      }
    })
  } catch {
    // 快照不可用时，只显示节点在线状态
    nodeConfigStatus.value = projectNodes.value.map((pn: any) => ({
      nodeId: pn.id, nodeName: pn.name, nodeIp: pn.ip,
      online: pn.status === 1, hashOk: false, hash: ''
    }))
  }
}

async function switchNode(n: { nodeId: number; online: boolean }) {
  if (!n.online) {
    message.warning('该节点离线，无法读取配置')
    return
  }
  editNodeId.value = n.nodeId
  await loadContent()
}

async function loadContentAuto() {
  if (!selectedProjectId.value || !selectedFile.value?.id) return
  contentLoading.value = true
  contentError.value = ''
  try {
    const res = await getConfigContentAuto(selectedProjectId.value, selectedFile.value.id)
    editContent.value = res.data.content || ''
    editNodeId.value = res.data.nodeId
    contentSource.value = 'node'
  } catch (e: any) {
    const status = e?.response?.status
    const msg = e?.response?.data?.message || e?.message || ''
    if (status === 404 || msg.includes('不存在') || msg.includes('not found') || msg.includes('无法读取')) {
      // 配置文件尚未部署到节点，不算报错，让用户直接编辑
      editContent.value = ''
      editNodeId.value = undefined
      contentSource.value = 'manual'
    } else {
      contentError.value = '读取失败: ' + (msg || '所有节点离线')
      editContent.value = ''
    }
  } finally {
    contentLoading.value = false
  }
}

async function loadContent() {
  if (!selectedProjectId.value || !selectedFile.value?.id || !editNodeId.value) return
  contentLoading.value = true
  contentError.value = ''
  try {
    const res = await getConfigContent(selectedProjectId.value, editNodeId.value, selectedFile.value.id)
    editContent.value = res.data || ''
    contentSource.value = 'node'
  } catch (e: any) {
    contentError.value = '读取失败: ' + (e?.response?.data?.message || e?.message || '节点离线')
  } finally {
    contentLoading.value = false
  }
}

function onContentEdit() {
  contentSource.value = 'manual'
}

async function handleDistribute() {
  if (!selectedProjectId.value || !selectedFile.value?.id || !editContent.value) return
  if (distributeNodeIds.value.length === 0) { message.warning('请选择至少一个目标节点'); return }

  distributing.value = true
  distributeResult.value = undefined
  try {
    const res = await distributeConfig({
      projectId: selectedProjectId.value,
      configFileId: selectedFile.value.id,
      content: editContent.value,
      targetNodeIds: distributeNodeIds.value,
      restartAfter: restartAfterDistribute.value
    })
    // 解析结果
    const resultDetail = res.data?.resultDetail
    if (typeof resultDetail === 'string') {
      try { distributeResult.value = JSON.parse(resultDetail) } catch { /* ignore */ }
    } else if (Array.isArray(resultDetail)) {
      distributeResult.value = resultDetail
    }
    message.success('配置分发完成')
    contentSource.value = 'node'
    // 刷新同步状态
    if (selectedFile.value.id) loadFileSyncStatus(selectedFile.value.id)
  } catch (e: any) {
    message.error('分发失败: ' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    distributing.value = false
  }
}

function showAddModal() {
  newFile.projectId = selectedProjectId.value || 0
  newFile.fileName = ''
  newFile.relativePath = ''
  addModalVisible.value = true
}

function applyPreset(preset: { name: string; path: string }) {
  newFile.relativePath = preset.path
  newFile.fileName = preset.name
}

async function handleAddFile() {
  if (!newFile.relativePath) { message.warning('请填写配置文件路径'); return }
  // 从路径中提取文件名
  const parts = newFile.relativePath.split('/')
  newFile.fileName = parts[parts.length - 1] || newFile.relativePath
  addLoading.value = true
  try {
    newFile.projectId = selectedProjectId.value!
    await createConfigFile(newFile)
    message.success('配置文件定义已创建')
    addModalVisible.value = false
    await loadConfigFiles()
  } catch (e: any) {
    message.error('创建失败: ' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    addLoading.value = false
  }
}

async function handleDeleteFile(fileId: number) {
  if (!selectedProjectId.value) return
  try {
    await deleteConfigFile(fileId, selectedProjectId.value)
    message.success('已删除')
    if (selectedFile.value?.id === fileId) {
      selectedFile.value = undefined
      editContent.value = ''
    }
    await loadConfigFiles()
  } catch (e: any) {
    message.error('删除失败: ' + (e?.message || ''))
  }
}

// ====== 初始化 ======
onMounted(loadProjects)
</script>

<style scoped>
.config-file-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.config-file-item:hover {
  background: #f5f5f5;
}
.config-file-item.active {
  background: #e6f7ff;
  border-color: #91d5ff;
}
.node-status-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid #e8e8e8;
  background: #fafafa;
  transition: all 0.15s;
}
.node-status-chip:hover {
  border-color: #91d5ff;
  background: #f0f8ff;
}
.node-status-chip.active {
  border-color: #1890ff;
  background: #e6f7ff;
  font-weight: 500;
}
.node-status-chip.offline {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
