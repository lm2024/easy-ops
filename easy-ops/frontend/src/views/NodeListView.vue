<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <cluster-outlined style="color: #e8ff59" />
          <span style="font-weight: 600">节点管理</span>
          <a-tooltip title="Agent 运行在目标服务器上，通过心跳向平台自动上报。在线节点会被自动识别并显示在这里。">
            <info-circle-outlined style="color: #999; cursor: help" />
          </a-tooltip>
        </a-space>
      </template>

      <template #extra>
        <a-space>
          <a-tooltip title="按节点名称、IP 地址或标签搜索">
            <a-input
              v-model:value="keyword"
              placeholder="🔍 搜索名称 / IP / 标签..."
              style="width: 260px"
              allow-clear
              @press-enter="fetchNodes"
            >
              <template #prefix><search-outlined /></template>
            </a-input>
          </a-tooltip>
          <a-select v-model:value="filterStatus" style="width: 120px" placeholder="节点状态" allow-clear @change="fetchNodes">
            <a-select-option value="">全部状态</a-select-option>
            <a-select-option value="1">🟢 在线</a-select-option>
            <a-select-option value="0">🔴 离线</a-select-option>
          </a-select>
          <a-button @click="fetchNodes"><search-outlined /> 搜索</a-button>
          <a-button @click="handleExport"><download-outlined /> 导出CSV</a-button>
          <a-button @click="handleImportClick"><upload-outlined /> 导入CSV</a-button>
          <input ref="fileInputRef" type="file" accept=".csv" style="display:none" @change="handleImportFile" />
          <a-button @click="agentPkgInputRef?.click()"><cloud-upload-outlined /> 上传Agent包</a-button>
          <input ref="agentPkgInputRef" type="file" accept=".jar" style="display:none" @change="handleAgentPackageUpload" />
          <a-button type="primary" ghost :loading="upgrading" @click="handleBatchUpgrade">
            <rocket-outlined /> 批量升级Agent
          </a-button>
          <a-tooltip title="新增一个 Agent 节点。如果 Agent 已启动并通过心跳注册，会自动显示在这里。">
            <a-button type="primary" @click="$router.push('/nodes/add')">
              <plus-outlined /> 新增节点
            </a-button>
          </a-tooltip>
        </a-space>
      </template>

      <a-table :columns="columns" :data-source="nodes" :loading="loading" :pagination="pagination"
               :row-key="rowKey" @change="handleTableChange" v-model:expanded-row-keys="expandRowKeys"
               @expand="onRowExpand" :row-selection="rowSelection">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-space>
              <a-badge :status="record.status === 1 ? 'success' : 'error'" />
              <a style="font-weight:600">{{ record.name }}</a>
              <a-tag color="blue" v-if="record.lastHeartbeat" style="font-size:11px">🤖 自动识别</a-tag>
            </a-space>
          </template>
          <template v-if="column.key === 'tags'">
            <a-space wrap>
              <template v-if="editingTags === record.id">
                <a-select
                  v-model:value="editTagsArray"
                  mode="tags"
                  style="min-width:220px"
                  placeholder="输入标签后按回车添加"
                  size="small"
                  open
                  @blur="saveTags(record)"
                  @keydown.enter.prevent
                  @compositionstart="()=>{}"
                  @compositionend="()=>{}"
                >
                </a-select>
                <a-button size="small" type="link" @click="cancelEditTags"><close-outlined /></a-button>
              </template>
              <template v-else>
                <a-tag v-for="tag in getTagList(record.tags)" :key="tag" :color="getTagColor(tag)"
                       style="cursor:pointer" @click="startEditTags(record)">{{ tag }}</a-tag>
                <a-tooltip title="点击添加多个标签，支持标签搜索过滤">
                  <a-tag style="border:dashed;cursor:pointer" @click="startEditTags(record)"><plus-outlined /> 标签</a-tag>
                </a-tooltip>
              </template>
            </a-space>
          </template>
          <template v-if="column.key === 'status'">
            <a-tooltip :title="record.status === 1 ? '在线，心跳正常' : '离线，请检查 Agent 进程'">
              <a-badge :status="record.status === 1 ? 'success' : 'error'"
                       :text="record.status === 1 ? '🟢 在线' : '🔴 离线'" />
            </a-tooltip>
          </template>
          <template v-if="column.key === 'systemInfo'">
            <a-tooltip title="Agent 宿主机硬件规格 — 用于自动计算 JVM 参数">
              <a-space size="small" wrap>
                <a-tag style="font-size:11px">🧠 {{ record.cpuCores||'?' }}核</a-tag>
                <a-tag style="font-size:11px">💾 {{ fmtSize(record.totalMemoryMb) }}</a-tag>
                <a-tag style="font-size:11px" v-if="record.osInfo">{{ record.osInfo.substring(0,14) }}</a-tag>
              </a-space>
            </a-tooltip>
          </template>
          <template v-if="column.key === 'lastHeartbeat'">
            <a-tooltip :title="record.lastHeartbeat ? fmtDate(record.lastHeartbeat) : '未收到'">
              <span style="color:#8c8c8c;font-size:13px">{{ record.lastHeartbeat ? fmtDate(record.lastHeartbeat) : '-' }}</span>
            </a-tooltip>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-tooltip title="编辑节点名称、IP、端口等基本信息">
                <a-button type="link" size="small" @click="editNode(record)"><edit-outlined /> 编辑</a-button>
              </a-tooltip>
              <a-tooltip title="展开查看节点完整系统信息（CPU / 内存 / 磁盘 / 系统环境）">
                <a-button type="link" size="small" @click="toggleExpand(record)"><eye-outlined /> 详情</a-button>
              </a-tooltip>
              <a-popconfirm title="确定删除此节点?" ok-text="确定" cancel-text="取消" @confirm="deleteNodeAction(record.id)">
                <a-tooltip title="删除后 Agent 重新注册时会自动添加回来">
                  <a-button type="link" size="small" danger><delete-outlined /> 删除</a-button>
                </a-tooltip>
              </a-popconfirm>
            </a-space>
          </template>
        </template>

        <!-- ====== 展开详情面板：完整系统信息 ====== -->
        <template #expandedRowRender="{ record }">
          <div style="padding: 8px 0">
            <!-- 加载状态 -->
            <a-skeleton v-if="nodeDetailLoading[rowKey(record)]" active :paragraph="{ rows: 12 }" />
            <!-- 错误状态 -->
            <a-result v-else-if="nodeDetailError[rowKey(record)]" status="warning" :title="'无法获取节点详情'"
                      :sub-title="nodeDetailError[rowKey(record)]">
              <template #extra>
                <a-button size="small" @click="fetchNodeDetail(rowKey(record))">🔄 重试</a-button>
              </template>
            </a-result>
            <!-- 数据展示 -->
            <template v-else-if="nodeDetailData[rowKey(record)]">
              <a-card size="small" class="node-detail-card">
                <a-row :gutter="[24, 16]">
                  <!-- 第一行：基本 + 系统 -->
                  <a-col :span="12">
                    <a-descriptions title="📋 基本信息" size="small" :column="1" bordered>
                      <a-descriptions-item label="节点名称"><a-tooltip title="AGENT_NODE_NAME 配置值">{{ record.name }}</a-tooltip></a-descriptions-item>
                      <a-descriptions-item label="主机名"><a-tooltip title="服务器主机名">{{ nodeDetailData[rowKey(record)].hostname || '-' }}</a-tooltip></a-descriptions-item>
                      <a-descriptions-item label="IP 地址"><a-tooltip title="Agent 网络地址">{{ record.ip }}</a-tooltip></a-descriptions-item>
                      <a-descriptions-item label="认证 Token"><code>{{ (record.token||'').substring(0,16) }}...</code></a-descriptions-item>
                      <a-descriptions-item label="自定义标签"><a-tooltip title="用逗号分隔多个标签，支持搜索">{{ record.tags || '(无)' }}</a-tooltip></a-descriptions-item>
                    </a-descriptions>
                  </a-col>
                  <a-col :span="12">
                    <a-descriptions title="💻 系统环境" size="small" :column="1" bordered>
                      <a-descriptions-item label="操作系统"><a-tooltip title="系统名称和版本">{{ nodeDetailData[rowKey(record)].osName }} {{ nodeDetailData[rowKey(record)].osVersion }}</a-tooltip></a-descriptions-item>
                      <a-descriptions-item label="系统架构">{{ nodeDetailData[rowKey(record)].osArch || record.osArch || '-' }}</a-descriptions-item>
                      <a-descriptions-item label="运行时长"><a-tooltip title="系统已持续运行时间">{{ nodeDetailData[rowKey(record)].uptime || '-' }}</a-tooltip></a-descriptions-item>
                      <a-descriptions-item label="Java 版本"><code>{{ record.javaVersion || '-' }}</code></a-descriptions-item>
                      <a-descriptions-item label="JVM 堆内存">
                        <a-tooltip title="JVM 最大可用堆 / 已分配 / 空闲（影响 GC 频率）">
                          <span>最大 {{ fmtSize(nodeDetailData[rowKey(record)].jvmMaxHeapMB) }}</span>
                          <span v-if="nodeDetailData[rowKey(record)].jvmTotalMemoryMB"> | 已用 {{ fmtSize((nodeDetailData[rowKey(record)].jvmMaxHeapMB||0) - (nodeDetailData[rowKey(record)].jvmFreeMemoryMB||0)) }}</span>
                        </a-tooltip>
                      </a-descriptions-item>
                      <a-descriptions-item label="心跳时间">{{ record.lastHeartbeat ? new Date(record.lastHeartbeat).toLocaleString() : '-' }}</a-descriptions-item>
                    </a-descriptions>
                  </a-col>
                </a-row>

                <!-- 第二行：CPU + 内存 -->
                <a-row :gutter="[24, 16]" style="margin-top:16px">
                  <a-col :span="12">
                    <a-descriptions title="🧠 CPU 信息" size="small" :column="1" bordered>
                      <a-descriptions-item label="CPU 型号">
                        <span style="word-break:break-all">{{ nodeDetailData[rowKey(record)].cpuModel || '未能识别' }}</span>
                      </a-descriptions-item>
                      <a-descriptions-item label="物理 / 逻辑核数">
                        <a-space>
                          <a-tag color="purple">{{ nodeDetailData[rowKey(record)].cpuPhysicalCores || '?' }} 物理核</a-tag>
                          <a-tag color="blue">{{ nodeDetailData[rowKey(record)].cpuLogicalCores || nodeDetailData[rowKey(record)].cpuCores || record.cpuCores || '?' }} 逻辑核</a-tag>
                        </a-space>
                        <div class="sys-hint">物理核=真实 CPU 核心；逻辑核=含超线程后的可调度核心数</div>
                      </a-descriptions-item>
                      <a-descriptions-item label="当前使用率">
                        <div class="cpu-usage-row">
                          <span class="cpu-usage-value">{{ nodeDetailData[rowKey(record)].cpuUsagePercent ?? '?' }}%</span>
                          <a-progress
                            :percent="Number(nodeDetailData[rowKey(record)].cpuUsagePercent) || 0"
                            :show-info="false"
                            :status="(nodeDetailData[rowKey(record)].cpuUsagePercent||0) > 80 ? 'exception' : 'active'"
                          />
                        </div>
                        <div class="sys-hint">{{ nodeDetailData[rowKey(record)].cpuUsageDescription || '反映 CPU 当前忙碌程度' }}</div>
                      </a-descriptions-item>
                      <a-descriptions-item label="系统负载">
                        {{ nodeDetailData[rowKey(record)].loadAverage1m || '?' }} / {{ nodeDetailData[rowKey(record)].loadAverage5m || '?' }} / {{ nodeDetailData[rowKey(record)].loadAverage15m || '?' }}
                        <div class="sys-hint">{{ nodeDetailData[rowKey(record)].loadDescription || '1/5/15 分钟平均等待 CPU 的任务数' }}</div>
                      </a-descriptions-item>
                    </a-descriptions>
                  </a-col>
                  <a-col :span="12">
                    <a-descriptions title="💾 内存信息" size="small" :column="1" bordered>
                      <a-descriptions-item label="总内存">
                        <a-tag color="blue">{{ fmtSizeSup(nodeDetailData[rowKey(record)].totalMemoryMB) }}</a-tag>
                      </a-descriptions-item>
                      <a-descriptions-item label="已使用（应用）">
                        <a-space direction="vertical" style="width:100%">
                          <a-space>
                            <a-tag color="orange">{{ fmtSizeSup(nodeDetailData[rowKey(record)].usedMemoryMB) }}</a-tag>
                            <span v-if="nodeDetailData[rowKey(record)].memoryUsagePercent != null">{{ nodeDetailData[rowKey(record)].memoryUsagePercent }}%</span>
                          </a-space>
                          <a-progress
                            :percent="Number(nodeDetailData[rowKey(record)].memoryUsagePercent) || 0"
                            :show-info="false"
                            :status="(nodeDetailData[rowKey(record)].memoryUsagePercent||0) > 85 ? 'exception' : 'active'"
                          />
                          <div class="sys-hint">{{ nodeDetailData[rowKey(record)].memoryUsedDescription }}</div>
                        </a-space>
                      </a-descriptions-item>
                      <a-descriptions-item label="文件缓存/缓冲">
                        <a-tag color="geekblue">{{ fmtSizeSup(nodeDetailData[rowKey(record)].buffersCachedMB) }}</a-tag>
                        <div class="sys-hint">{{ nodeDetailData[rowKey(record)].memoryBuffersCachedDescription }}</div>
                      </a-descriptions-item>
                      <a-descriptions-item label="仍可分配">
                        <a-tag color="cyan">{{ fmtSizeSup(nodeDetailData[rowKey(record)].availableMemoryMB) }}</a-tag>
                        <div class="sys-hint">{{ nodeDetailData[rowKey(record)].memoryAvailableDescription }}</div>
                      </a-descriptions-item>
                    </a-descriptions>
                    <a-alert
                      v-if="nodeDetailData[rowKey(record)].memorySummary"
                      type="info"
                      show-icon
                      style="margin-top:12px;font-size:12px"
                      :message="nodeDetailData[rowKey(record)].memorySummary"
                    />
                  </a-col>
                </a-row>

                <!-- 第三行：磁盘信息（可多个） -->
                <a-row style="margin-top:16px">
                  <a-col :span="24">
                    <a-descriptions title="💽 磁盘信息" size="small" bordered :column="1">
                      <template v-if="!nodeDetailData[rowKey(record)].disks || nodeDetailData[rowKey(record)].disks.length === 0">
                        <a-descriptions-item label="磁盘列表">暂未获取到磁盘信息</a-descriptions-item>
                      </template>
                      <a-descriptions-item v-for="(disk, di) in nodeDetailData[rowKey(record)].disks" :key="di"
                        :label="'📂 ' + (disk.mountPoint||'未知')">
                        <a-space direction="vertical" style="width:100%">
                          <a-row :gutter="16">
                            <a-col :span="6"><span style="color:#888">总容量:</span> <strong>{{ disk.totalGB || '-' }}GB</strong></a-col>
                            <a-col :span="6"><span style="color:#888">已用:</span> <strong style="color:#fa8c16">{{ disk.usedGB || '-' }}GB</strong></a-col>
                            <a-col :span="6"><span style="color:#888">剩余:</span> <strong style="color:#52c41a">{{ disk.freeGB || '-' }}GB</strong></a-col>
                            <a-col :span="6"><span style="color:#888">使用率:</span>
                              <a-tag :color="(disk.usagePercent||0) > 85 ? 'red' : (disk.usagePercent||0) > 70 ? 'orange' : 'green'">
                                {{ disk.usagePercent || '?' }}%
                              </a-tag>
                            </a-col>
                          </a-row>
                          <a-progress :percent="parseInt(disk.usagePercent) || 0" size="small"
                                      :status="(parseInt(disk.usagePercent)||0) > 85 ? 'exception' : (parseInt(disk.usagePercent)||0) > 70 ? 'active' : 'success'" />
                          <a-alert v-if="(parseInt(disk.usagePercent)||0) > 85" type="warning" show-icon style="font-size:12px;margin-top:4px"
                                   message="⚠️ 磁盘即将写满！请及时清理日志或扩容" />
                          <a-alert v-else-if="(parseInt(disk.usagePercent)||0) > 70" type="info" show-icon style="font-size:12px;margin-top:4px"
                                   message="💡 磁盘使用率偏高，建议关注" />
                        </a-space>
                      </a-descriptions-item>
                    </a-descriptions>
                  </a-col>
                </a-row>

                <!-- 节点状态警报 -->
                <a-row style="margin-top:16px">
                  <a-col :span="24">
                    <a-alert v-if="record.status === 0" type="warning" show-icon message="🔴 节点离线"
                             description="Agent 可能已停止运行、网络中断或服务器关机。请检查目标服务器上 Agent 进程是否正常运行，以及网络连通性。" />
                    <a-alert v-else type="success" show-icon message="🟢 节点在线 — Agent 运行正常"
                             :description="`Agent 心跳约 ${AGENT_HEARTBEAT_SEC} 秒上报一次；CPU/内存每 ${DETAIL_REFRESH_SEC} 秒自动刷新。当前 CPU ${nodeDetailData[rowKey(record)].cpuUsagePercent??'?'}% | 内存 ${nodeDetailData[rowKey(record)].memoryUsagePercent??'?'}% | 最后心跳 ${record.lastHeartbeat ? new Date(record.lastHeartbeat).toLocaleString() : '未知'}`" />
                  </a-col>
                </a-row>
              </a-card>
            </template>
          </div>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { NodeModel } from '../types'
import { getNodes, deleteNode, updateNodeTags, exportNodesCsv, importNodesCsv, uploadAgentPackage, batchUpgradeAgents } from '../api/node'
import { getNodeSysInfo } from '../api/agent'
import {
  SearchOutlined, PlusOutlined, EditOutlined, DeleteOutlined, ClusterOutlined,
  DownloadOutlined, UploadOutlined, EyeOutlined, InfoCircleOutlined, CloseOutlined,
  CloudUploadOutlined, RocketOutlined
} from '@ant-design/icons-vue'

/** Agent 向 Server 上报心跳间隔（秒），与 backend agent.check-interval 一致 */
const AGENT_HEARTBEAT_SEC = 30
/** 节点详情 CPU/内存 等指标的前端自动刷新间隔（秒），仅影响展示，不增加 Agent 心跳频率 */
const DETAIL_REFRESH_SEC = 10
const DETAIL_REFRESH_MS = DETAIL_REFRESH_SEC * 1000

function fmtDate(ts: any): string {
  if (!ts) return '-'
  const d = new Date(typeof ts === 'string' ? Number(ts) : ts)
  if (isNaN(d.getTime())) return String(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const router = useRouter()
const nodes = ref<NodeModel[]>([])
const loading = ref(false)
const keyword = ref('')
const filterStatus = ref<string | undefined>(undefined)
const pagination = ref({ current: 1, pageSize: 20, total: 0 })
const fileInputRef = ref<HTMLInputElement>()
const agentPkgInputRef = ref<HTMLInputElement>()
const upgrading = ref(false)
const selectedRowKeys = ref<string[]>([])
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => { selectedRowKeys.value = keys.map(String) }
}))
const editingTags = ref<string | null>(null)
const editTagsArray = ref<string[]>([])
type RowKey = string | number
const expandRowKeys = ref<RowKey[]>([])

/** 统一行主键，避免 number/string 混用导致展开失效 */
function rowKey(record: NodeModel): string {
  return String(record.id)
}
const nodeDetailData = reactive<Record<string, any>>({})
const nodeDetailLoading = reactive<Record<string, boolean>>({})
const nodeDetailError = reactive<Record<string, string>>({})
let detailRefreshTimer: ReturnType<typeof setInterval> | null = null

const columns = [
  { title: '节点名称', dataIndex: 'name', key: 'name', width: 200 },
  { title: '标签', dataIndex: 'tags', key: 'tags', width: 220 },
  { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
  { title: 'Agent版本', dataIndex: 'agentVersion', key: 'agentVersion', width: 130 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '系统信息', dataIndex: 'systemInfo', key: 'systemInfo', width: 270 },
  { title: '最后心跳', dataIndex: 'lastHeartbeat', key: 'lastHeartbeat', width: 150 },
  { title: '操作', key: 'action', width: 210, fixed: 'right' as const }
]

function fmtSize(mb?: number | null): string {
  if (!mb) return '-'
  const n = Number(mb)
  if (n >= 1048576) return (n / 1048576).toFixed(1) + 'TB'
  if (n >= 1024) return (n / 1024).toFixed(1) + 'GB'
  return n + 'MB'
}
function fmtSizeSup(mb?: number | null): string {
  if (!mb) return '-'
  const n = Number(mb)
  if (n >= 1024) return (n / 1024).toFixed(1) + ' GB'
  return n + ' MB'
}
function getTagList(tags?: string): string[] {
  if (!tags?.trim()) return []
  return tags.split(',').map(t => t.trim()).filter(Boolean)
}
function getTagColor(tag: string): string {
  const colors = ['blue','green','orange','purple','cyan','geekblue','magenta','gold','lime']
  let h = 0; for (let i = 0; i < tag.length; i++) h = tag.charCodeAt(i) + ((h << 5) - h)
  return colors[Math.abs(h) % colors.length]
}

function startEditTags(r: NodeModel) {
  editingTags.value = r.id
  editTagsArray.value = getTagList(r.tags)
}

function cancelEditTags() {
  editingTags.value = null
  editTagsArray.value = []
}

async function saveTags(r: NodeModel) {
  if (!editingTags.value) return
  const tagStr = editTagsArray.value.filter(t => t.trim()).join(',')
  try {
    await updateNodeTags(r.id, tagStr)
    r.tags = tagStr
    message.success('✅ 标签已更新')
  } catch {
    message.error('标签更新失败')
  }
  editingTags.value = null
  editTagsArray.value = []
}

async function onRowExpand(expanded: boolean, record: NodeModel) {
  const key = rowKey(record)
  if (expanded && !nodeDetailData[key]) {
    await fetchNodeDetail(key)
  }
}

async function toggleExpand(record: NodeModel) {
  const key = rowKey(record)
  if (expandRowKeys.value.includes(key)) {
    expandRowKeys.value = expandRowKeys.value.filter(k => k !== key)
    return
  }
  expandRowKeys.value = [...expandRowKeys.value, key]
  if (!nodeDetailData[key]) await fetchNodeDetail(key)
}

async function fetchNodeDetail(nodeId: string, silent = false) {
  if (!silent) {
    nodeDetailLoading[nodeId] = true
    nodeDetailError[nodeId] = ''
  }
  try {
    const res = await getNodeSysInfo(nodeId)
    nodeDetailData[nodeId] = res.data
    if (silent) nodeDetailError[nodeId] = ''
  } catch (e: any) {
    if (!silent) {
      nodeDetailError[nodeId] = e?.message || '无法连接到 Agent，请检查节点是否在线'
    }
  } finally {
    if (!silent) nodeDetailLoading[nodeId] = false
  }
}

async function fetchNodesSilent() {
  try {
    const res = await getNodes(pagination.value.current, pagination.value.pageSize, keyword.value, filterStatus.value)
    nodes.value = res.data.list
    pagination.value.total = res.data.total
  } catch {
    // 后台刷新失败时静默忽略，避免打断用户操作
  }
}

async function refreshExpandedDetails() {
  if (expandRowKeys.value.length === 0) return
  await Promise.all(expandRowKeys.value.map(key => fetchNodeDetail(String(key), true)))
  await fetchNodesSilent()
}

function startDetailRefresh() {
  if (detailRefreshTimer) return
  detailRefreshTimer = setInterval(refreshExpandedDetails, DETAIL_REFRESH_MS)
}

function stopDetailRefresh() {
  if (detailRefreshTimer) {
    clearInterval(detailRefreshTimer)
    detailRefreshTimer = null
  }
}

async function fetchNodes() {
  try {
    loading.value = true
    const res = await getNodes(pagination.value.current, pagination.value.pageSize, keyword.value, filterStatus.value)
    nodes.value = res.data.list; pagination.value.total = res.data.total
  } finally { loading.value = false }
}

function handleTableChange(pag: any) { pagination.value.current = pag.current; pagination.value.pageSize = pag.pageSize; fetchNodes() }
function editNode(r: NodeModel) { router.push(`/nodes/${r.id}/edit`) }
async function deleteNodeAction(id: string) { await deleteNode(id); fetchNodes(); message.success('节点已删除') }
async function handleExport() { try { await exportNodesCsv(); message.success('导出成功') } catch { message.error('导出失败') } }
function handleImportClick() { fileInputRef.value?.click() }
async function handleImportFile(e: Event) {
  const t = e.target as HTMLInputElement; const f = t.files?.[0]; if (!f) return
  try { const r = await importNodesCsv(f); message.success(`成功导入 ${r.data.imported} 个节点`); fetchNodes() }
  catch { message.error('导入失败，请检查CSV格式') } finally { t.value = '' }
}

async function handleAgentPackageUpload(e: Event) {
  const t = e.target as HTMLInputElement
  const f = t.files?.[0]
  if (!f) return
  try {
    const r = await uploadAgentPackage(f)
    const sizeMb = ((r.data.size || 0) / 1024 / 1024).toFixed(1)
    message.success('Agent 升级包已上传 (' + sizeMb + ' MB)')
  } catch {
    message.error('Agent 升级包上传失败')
  } finally {
    t.value = ''
  }
}

async function handleBatchUpgrade() {
  try {
    upgrading.value = true
    const ids = selectedRowKeys.value.length > 0 ? selectedRowKeys.value : undefined
    const r = await batchUpgradeAgents(ids)
    message.success(`升级完成：成功 ${r.data.success}，失败 ${r.data.failed}`)
    fetchNodes()
  } catch (e: any) {
    message.error(e?.message || '批量升级失败，请先上传 Agent 升级包')
  } finally {
    upgrading.value = false
  }
}

onMounted(fetchNodes)

watch(expandRowKeys, (keys) => {
  if (keys.length > 0) startDetailRefresh()
  else stopDetailRefresh()
}, { deep: true })

onUnmounted(stopDetailRefresh)
</script>

<style scoped>
.node-detail-card {
  background: var(--eo-bg-muted);
  border-radius: 8px;
}

.sys-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #8c8c8c;
}
.cpu-usage-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.cpu-usage-row :deep(.ant-progress) {
  flex: 1;
  min-width: 120px;
}
.cpu-usage-value {
  min-width: 48px;
  font-size: 20px;
  font-weight: 600;
  color: #e8ff59;
}
</style>
