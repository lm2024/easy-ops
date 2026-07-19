<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <database-outlined style="color: #52c41a" />
          <span style="font-weight: 600">H2 表结构维护</span>
        </a-space>
      </template>

      <a-row :gutter="16" style="min-height: 500px">
        <!-- 左侧：分类表列表 -->
        <a-col :span="6">
          <div style="border-right: 1px solid #f0f0f0; padding-right: 12px; height: 100%">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
              <span style="font-weight:500;color:#666">数据表 ({{ tables.length }})</span>
              <a-button size="small" type="link" @click="loadTables" :loading="tableLoading">
                <reload-outlined />
              </a-button>
            </div>
            <!-- 全量操作 -->
            <div style="display:flex;gap:6px;margin-bottom:8px">
              <a-button size="small" block @click="handleFullExport" :loading="fullExportLoading">
                <download-outlined /> 全量导出
              </a-button>
              <a-button size="small" block @click="fullImportVisible = true; fullImportResult = null">
                <upload-outlined /> 全量导入
              </a-button>
            </div>
            <!-- 搜索框 -->
            <a-input
              v-model:value="tableSearch"
              placeholder="搜索表名..."
              size="small"
              allow-clear
              style="margin-bottom:8px"
            >
              <template #prefix><search-outlined style="color:#bbb" /></template>
            </a-input>
            <div v-if="tableLoading" style="text-align:center;padding:40px 0"><a-spin /></div>
            <div v-else-if="tables.length === 0" style="text-align:center;padding:40px 0;color:#999">暂无数据表</div>
            <div v-else class="table-list-scroll">
              <div v-for="group in filteredTableGroups" :key="group.category">
                <!-- 分组标题 -->
                <div class="db-group-title" @click="toggleGroup(group.category)">
                  <span>{{ group.icon }} {{ group.category }}</span>
                  <span style="color:#bbb;font-size:11px">{{ group.tables.length }}</span>
                </div>
                <!-- 分组内容 -->
                <div v-show="!collapsedGroups.has(group.category)">
                  <div
                    v-for="t in group.tables" :key="t.tableName"
                    class="db-table-item"
                    :class="{ active: selectedTable === t.tableName }"
                    @click="selectTable(t.tableName)"
                  >
                    <div style="font-size:13px;line-height:1.4">{{ t.label }}</div>
                    <div style="font-size:11px;color:#999;margin-top:1px">{{ t.tableName }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </a-col>

        <!-- 右侧：内容 -->
        <a-col :span="18">
          <div v-if="!selectedTable" style="text-align:center;padding:80px 0;color:#999">
            <table-outlined style="font-size:48px;color:#d9d9d9" />
            <div style="margin-top:16px">请从左侧选择一张数据表</div>
          </div>

          <template v-else>
            <a-tabs v-model:activeKey="activeTab" type="card">
              <!-- Tab 1: 数据查看 -->
              <a-tab-pane key="data" tab="📊 数据查看">
                <!-- 搜索 + 操作栏 -->
                <div style="display:flex;justify-content:space-between;margin-bottom:12px">
                  <a-space>
                    <a-input-search
                      v-model:value="searchText"
                      placeholder="搜索所有字段..."
                      style="width:260px"
                      @search="loadData(1)"
                      enter-button
                    />
                    <a-button @click="loadData(1)"><reload-outlined /> 刷新</a-button>
                  </a-space>
                  <a-space>
                    <a-badge :count="dataTotal" :overflow-count="99999" style="margin-right:8px">
                      <span style="font-size:12px;color:#888">共</span>
                    </a-badge>
                    <a-button type="primary" @click="showAddModal"><plus-outlined /> 新增</a-button>
                  </a-space>
                </div>

                <!-- 数据表格 -->
                <a-table
                  :data-source="dataRows"
                  :columns="dataColumns"
                  :loading="dataLoading"
                  :pagination="{
                    current: dataPage,
                    pageSize: dataPageSize,
                    total: dataTotal,
                    showSizeChanger: true,
                    showTotal: showTotalFn
                  }"
                  size="small"
                  :scroll="{ x: 'max-content' }"
                  @change="handleTableChange"
                  row-key="__row_index"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === '__actions'">
                      <a-space>
                        <a-tooltip title="编辑">
                          <a-button size="small" type="link" @click="showEditModal(record)">
                            <edit-outlined />
                          </a-button>
                        </a-tooltip>
                        <a-popconfirm title="确认删除此记录？" @confirm="handleDelete(record)">
                          <a-tooltip title="删除">
                            <a-button size="small" type="link" danger>
                              <delete-outlined />
                            </a-button>
                          </a-tooltip>
                        </a-popconfirm>
                      </a-space>
                    </template>
                    <template v-else>
                      <span style="font-size:12px;max-width:300px;display:inline-block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                        {{ formatCellValue(record[column.dataIndex]) }}
                      </span>
                    </template>
                  </template>
                </a-table>
              </a-tab-pane>

              <!-- Tab 2: 表结构 -->
              <a-tab-pane key="structure" tab="📐 表结构">
                <div v-if="structureLoading" style="text-align:center;padding:40px"><a-spin /></div>
                <template v-else-if="structure">
                  <!-- DDL -->
                  <a-card title="DDL" size="small" style="margin-bottom:16px">
                    <pre class="ddl-pre">{{ structure.ddl }}</pre>
                    <a-space style="margin-top:8px">
                      <a-button size="small" @click="copyText(structure.ddl)"><copy-outlined /> 复制 DDL</a-button>
                      <span style="color:#888;font-size:12px">行数: {{ structure.rowCount }}</span>
                    </a-space>
                  </a-card>

                  <!-- 列信息 -->
                  <a-card title="列信息" size="small">
                    <a-table
                      :data-source="structure.columns"
                      :columns="colColumns"
                      :pagination="false"
                      size="small"
                      row-key="name"
                    />
                  </a-card>
                </template>
              </a-tab-pane>

              <!-- Tab 3: 导出 -->
              <a-tab-pane key="export" tab="📤 导出">
                <div style="padding:24px;text-align:center">
                  <h3>导出表 "{{ selectedTable }}" 数据</h3>
                  <div style="margin:16px 0;color:#888;font-size:13px">
                    <div>导出格式：JSON</div>
                    <div>包含表结构定义和全部数据行，可用于备份或导入到其他环境</div>
                  </div>
                  <a-button type="primary" size="large" @click="handleExport" :loading="exportLoading">
                    <download-outlined /> 导出为 JSON
                  </a-button>
                </div>
              </a-tab-pane>

              <!-- Tab 4: 导入 -->
              <a-tab-pane key="import" tab="📥 导入">
                <div style="padding:24px;max-width:600px;margin:0 auto">
                  <h3 style="text-align:center">导入数据到 "{{ selectedTable }}"</h3>

                  <a-form layout="vertical" style="margin-top:24px">
                    <a-form-item label="导入模式">
                      <a-radio-group v-model:value="importMode">
                        <a-radio value="append">
                          <span style="font-weight:500">追加导入</span>
                          <div style="font-size:12px;color:#888">保留现有数据，新增行追加到尾部</div>
                        </a-radio>
                        <a-radio value="truncate">
                          <span style="font-weight:500">清空后导入</span>
                          <div style="font-size:12px;color:#888">先清空表中所有数据，再导入新数据</div>
                        </a-radio>
                      </a-radio-group>
                    </a-form-item>

                    <a-form-item label="导入文件">
                      <a-upload-dragger
                        :before-upload="handleBeforeUpload"
                        :show-upload-list="false"
                        accept=".json"
                      >
                        <p class="ant-upload-drag-icon">
                          <inbox-outlined />
                        </p>
                        <p class="ant-upload-text">点击或拖拽 JSON 文件到此区域</p>
                        <p class="ant-upload-hint">支持从「导出」功能生成的 JSON 文件</p>
                      </a-upload-dragger>
                    </a-form-item>

                    <a-form-item v-if="importPreview">
                      <a-alert
                        :message="'已读取: ' + importPreview.length + ' 条数据'"
                        type="info"
                        show-icon
                      />
                    </a-form-item>

                    <a-form-item v-if="importResult">
                      <a-alert
                        :message="importResult.message"
                        :type="importResult.inserted > 0 ? 'success' : 'warning'"
                        show-icon
                      />
                      <div style="font-size:12px;color:#888;margin-top:4px">
                        当前表总行数: {{ importResult.totalRows }}
                      </div>
                    </a-form-item>

                    <a-form-item>
                      <a-space>
                        <a-button type="primary" @click="handleImport" :loading="importLoading" :disabled="!importPreview">
                          <upload-outlined /> 执行导入
                        </a-button>
                        <a-button @click="resetImport">重置</a-button>
                      </a-space>
                    </a-form-item>
                  </a-form>
                </div>
              </a-tab-pane>
            </a-tabs>
          </template>
        </a-col>
      </a-row>
    </a-card>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      :title="editMode === 'add' ? '新增记录' : '编辑记录'"
      @ok="handleSave"
      :confirm-loading="saveLoading"
      width="600px"
    >
      <a-form layout="vertical">
        <a-form-item
          v-for="col in dataColumns.filter(c => c.key !== '__actions')"
          :key="col.dataIndex"
          :label="col.title"
        >
          <a-input
            v-if="col.dataIndex !== editPrimaryKey"
            v-model:value="editForm[col.dataIndex]"
            :placeholder="'输入 ' + col.title"
          />
          <span v-else style="color:#888;font-size:12px">主键，不可修改</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 全量导入弹窗 -->
    <a-modal
      v-model:open="fullImportVisible"
      title="全量导入"
      @ok="handleFullImport"
      :confirm-loading="fullImportLoading"
      :ok-button-props="{ disabled: !fullImportResult }"
      width="520px"
    >
      <a-form layout="vertical">
        <a-form-item label="导入模式">
          <a-radio-group v-model:value="fullImportMode">
            <a-radio value="truncate">
              <span style="font-weight:500">清空后导入</span>
              <div style="font-size:12px;color:#888">先清空每张表的数据，再导入（全量恢复）</div>
            </a-radio>
            <a-radio value="append">
              <span style="font-weight:500">追加导入</span>
              <div style="font-size:12px;color:#888">保留现有数据，只追加新行</div>
            </a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="选择文件">
          <a-upload-dragger
            :before-upload="handleFullImportUpload"
            :show-upload-list="false"
            accept=".json"
          >
            <p class="ant-upload-drag-icon"><inbox-outlined /></p>
            <p class="ant-upload-text">点击或拖拽全量备份 JSON 文件到此区域</p>
          </a-upload-dragger>
        </a-form-item>
        <a-form-item v-if="fullImportResult">
          <a-alert
            :message="'已读取: ' + fullImportResult.tableCount + ' 张表, ' + fullImportResult.totalRows + ' 行数据'"
            type="info"
            show-icon
          />
          <div style="font-size:12px;color:#888;margin-top:4px">
            文件: {{ fullImportResult.fileName }}
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  listTables, getTableStructure, queryTableData,
  insertRow, updateRow, deleteRow,
  exportTableData, importTableData,
  exportAllData, importAllData
} from '../api/db'
import {
  DatabaseOutlined, TableOutlined, ReloadOutlined,
  PlusOutlined, EditOutlined, DeleteOutlined,
  DownloadOutlined, UploadOutlined, InboxOutlined,
  CopyOutlined, SearchOutlined
} from '@ant-design/icons-vue'

// ====== 表分类元数据 ======
const TABLE_META: Record<string, { label: string; category: string; icon: string }> = {
  node_info:               { label: '节点信息',       category: '节点管理', icon: '🖥️' },
  project_info:            { label: '项目信息',       category: '项目部署', icon: '🚀' },
  version_package:         { label: '版本包',         category: '项目部署', icon: '🚀' },
  deploy_record:           { label: '部署记录',       category: '项目部署', icon: '🚀' },
  alarm_config:            { label: '告警配置',       category: '告警管理', icon: '🔔' },
  alarm_record:            { label: '告警记录',       category: '告警管理', icon: '🔔' },
  sys_user:                { label: '系统用户',       category: '用户权限', icon: '👤' },
  user_project_relation:   { label: '用户项目关系',   category: '用户权限', icon: '👤' },
  operation_log:           { label: '操作日志',       category: '审计日志', icon: '📋' },
  file_access_log:         { label: '文件访问日志',   category: '审计日志', icon: '📋' },
  sys_config:              { label: '系统配置',       category: '系统配置', icon: '⚙️' },
  scheduler_lock:          { label: '调度锁',         category: '系统配置', icon: '⚙️' },
  project_config_file:     { label: '项目配置文件',   category: '配置管理', icon: '📁' },
  node_config_snapshot:    { label: '节点配置快照',   category: '配置管理', icon: '📁' },
  config_distribute_record:{ label: '配置分发记录',   category: '配置管理', icon: '📁' },
  project_log_profile:     { label: '日志配置',       category: '日志管理', icon: '📝' },
  project_health_probe:    { label: '健康探针',       category: '监控诊断', icon: '📊' },
  monitor_snapshot:        { label: '监控快照',       category: '监控诊断', icon: '📊' },
  ai_diagnosis_record:     { label: 'AI诊断记录',     category: '监控诊断', icon: '📊' },
  kb_category:             { label: '知识分类',       category: '知识库',   icon: '📚' },
  kb_document:             { label: '知识文档',       category: '知识库',   icon: '📚' },
  kb_document_version:     { label: '文档版本',       category: '知识库',   icon: '📚' },
  kb_comment:              { label: '文档评论',       category: '知识库',   icon: '📚' },
  kb_document_lock:        { label: '文档锁',         category: '知识库',   icon: '📚' },
  kb_image:                { label: '知识库图片',     category: '知识库',   icon: '📚' },
  kb_tag:                  { label: '知识标签',       category: '知识库',   icon: '📚' },
  kb_document_tag:         { label: '文档标签关联',   category: '知识库',   icon: '📚' },
  kb_document_permission:  { label: '文档权限',       category: '知识库',   icon: '📚' },
  kb_template:             { label: '知识模板',       category: '知识库',   icon: '📚' },
  kb_favorite:             { label: '知识收藏',       category: '知识库',   icon: '📚' },
  kb_recent_access:        { label: '最近访问',       category: '知识库',   icon: '📚' },
  kb_share_link:           { label: '分享链接',       category: '知识库',   icon: '📚' },
  self_heal_policy:        { label: '自愈策略',       category: '自愈通知', icon: '🔧' },
  self_heal_event:         { label: '自愈事件',       category: '自愈通知', icon: '🔧' },
  notification_record:     { label: '通知记录',       category: '自愈通知', icon: '🔧' },
  user_notification_state: { label: '用户通知状态',   category: '自愈通知', icon: '🔧' },
}
const CATEGORY_ORDER = ['节点管理','项目部署','告警管理','用户权限','审计日志','系统配置','配置管理','日志管理','监控诊断','知识库','自愈通知']

// ====== 状态 ======
const tables = ref<any[]>([])
const selectedTable = ref<string>('')
const tableLoading = ref(false)
const activeTab = ref('data')
const tableSearch = ref('')
const collapsedGroups = ref<Set<string>>(new Set())

// 分组后的表列表（带搜索过滤）
const filteredTableGroups = computed(() => {
  const keyword = tableSearch.value.toLowerCase().trim()
  const groups: Record<string, any[]> = {}
  for (const t of tables.value) {
    const name = t.tableName || ''
    const meta = TABLE_META[name.toLowerCase()]
    const label = meta?.label || name
    const category = meta?.category || '其他'
    const icon = meta?.icon || '📄'
    // 搜索过滤：匹配英文表名或中文别名
    if (keyword && !name.toLowerCase().includes(keyword) && !label.includes(keyword)) continue
    if (!groups[category]) groups[category] = []
    groups[category].push({ tableName: name, label, category, icon })
  }
  // 按预设顺序排列，未知分类放最后
  const result: any[] = []
  for (const cat of CATEGORY_ORDER) {
    if (groups[cat]) result.push({ category: cat, icon: groups[cat][0].icon, tables: groups[cat] })
  }
  if (groups['其他']) result.push({ category: '其他', icon: '📄', tables: groups['其他'] })
  return result
})

function toggleGroup(category: string) {
  const s = new Set(collapsedGroups.value)
  if (s.has(category)) s.delete(category)
  else s.add(category)
  collapsedGroups.value = s
}

// ====== 数据查看 ======
const dataRows = ref<any[]>([])
const dataColumns = ref<any[]>([])
const dataLoading = ref(false)
const dataPage = ref(1)
const dataPageSize = ref(50)
const dataTotal = ref(0)
const searchText = ref('')
const columnNames = ref<string[]>([])

// ====== 表结构 ======
const structure = ref<any>(null)
const structureLoading = ref(false)

// ====== 导出 ======
const exportLoading = ref(false)

// ====== 导入 ======
const importMode = ref('append')
const importPreview = ref<any[] | null>(null)
const importRawData = ref<any>(null)
const importLoading = ref(false)
const importResult = ref<any>(null)

// ====== 编辑 ======
const editModalVisible = ref(false)
const editMode = ref<'add' | 'edit'>('add')
const editForm = ref<Record<string, any>>({})
const editRowId = ref<string>('')
const editPrimaryKey = ref<string>('')
const saveLoading = ref(false)

// ====== 全量导入导出 ======
const fullExportLoading = ref(false)
const fullImportVisible = ref(false)
const fullImportMode = ref('truncate')
const fullImportLoading = ref(false)
const fullImportResult = ref<any>(null)

// 列定义（表结构展示用）
const colColumns = [
  { title: '列名', dataIndex: 'name', key: 'name', width: 150 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 120 },
  { title: '最大长度', dataIndex: 'maxLen', key: 'maxLen', width: 100 },
  { title: '可空', dataIndex: 'nullable', key: 'nullable', width: 80 },
  { title: '默认值', dataIndex: 'defaultValue', key: 'defaultValue', width: 150 },
  { title: '自增', dataIndex: 'autoInc', key: 'autoInc', width: 80 }
]

// ====== 方法 ======

function showTotalFn(total: number) { return "共 " + total + " 条" }
async function loadTables() {
  tableLoading.value = true
  console.log('[DbManage] 加载表列表...')
  try {
    const res = await listTables()
    tables.value = res.data || []
    console.log('[DbManage] 表列表加载成功: 共', tables.value.length, '张表')
    if (tables.value.length > 0) {
      console.log('[DbManage] 首张表字段:', Object.keys(tables.value[0]))
      console.log('[DbManage] 首张表数据:', JSON.stringify(tables.value[0]))
    }
  } catch (e: any) {
    console.error('[DbManage] 加载表列表失败:', e?.message, e)
    message.error('加载表列表失败: ' + (e?.message || ''))
  } finally {
    tableLoading.value = false
  }
}

async function selectTable(name: string) {
  console.log('[DbManage] 选择表:', name)
  selectedTable.value = name
  activeTab.value = 'data'
  console.log('[DbManage] 开始并行加载表结构和数据...')
  await Promise.all([loadStructure(), loadData(1)])
  console.log('[DbManage] 表结构和数据加载完成')
}

async function loadStructure() {
  if (!selectedTable.value) return
  console.log('[DbManage] 加载表结构: 表名=', selectedTable.value)
  structureLoading.value = true
  try {
    const res = await getTableStructure(selectedTable.value)
    structure.value = res.data
    console.log('[DbManage] 表结构加载成功:', JSON.stringify(structure.value).slice(0, 500))
    console.log('[DbManage] 主键列:', structure.value?.primaryKey)
    console.log('[DbManage] 列数:', structure.value?.columns?.length)
    console.log('[DbManage] 行数:', structure.value?.rowCount)
  } catch (e: any) {
    console.error('[DbManage] 表结构加载失败:', e?.message, e)
    message.error('加载表结构失败: ' + (e?.message || ''))
  } finally {
    structureLoading.value = false
  }
}

async function loadData(page?: number) {
  if (!selectedTable.value) return
  if (page) dataPage.value = page
  dataLoading.value = true
  console.log('[DbManage] 加载数据: 表=', selectedTable.value, '页码=', dataPage.value, '每页=', dataPageSize.value, '搜索=', searchText.value)
  try {
    const res = await queryTableData(selectedTable.value, dataPage.value, dataPageSize.value, searchText.value)
    const d = res.data
    console.log('[DbManage] 查询数据返回:', d)
    console.log('[DbManage] 列定义:', d.columns)
    console.log('[DbManage] 行数:', d.rows?.length, '总行数:', d.total)
    if (d.rows?.length > 0) {
      console.log('[DbManage] 首行数据:', d.rows[0])
      console.log('[DbManage] 首行字段:', Object.keys(d.rows[0]))
    }
    // 将列名转为驼峰以匹配行数据的驼峰 key（后端 toCamelCaseKeys 会把 ID→id, SMTP_HOST→smtpHost）
    const toCamel = (s: string) => s.toLowerCase().replace(/_([a-z])/g, (_, c) => c.toUpperCase())
    dataColumns.value = [
      ...(d.columns || []).map((c: any) => {
        const camelName = toCamel(c.name)
        return {
          title: c.name,
          dataIndex: camelName,
          key: camelName,
          ellipsis: true,
          width: 150
        }
      }),
      { title: '操作', key: '__actions', width: 100, fixed: 'right' as const }
    ]
    columnNames.value = (d.columns || []).map((c: any) => c.name)
    dataRows.value = (d.rows || []).map((row: any, idx: number) => ({ ...row, __row_index: idx }))
    dataTotal.value = d.total || 0
    console.log('[DbManage] 表格渲染: 列数=', dataColumns.value.length, '行数=', dataRows.value.length, '总行数=', dataTotal.value)
    if (dataRows.value.length > 0) {
      console.log('[DbManage] 渲染首行:', dataRows.value[0])
    }
  } catch (e: any) {
    console.error('[DbManage] 查询数据失败:', e?.message, e)
    message.error('查询数据失败: ' + (e?.message || ''))
  } finally {
    dataLoading.value = false
  }
}

function handleTableChange(pagination: any) {
  dataPage.value = pagination.current
  dataPageSize.value = pagination.pageSize
  loadData(dataPage.value)
}

function formatCellValue(val: any): string {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

// ====== 新增/编辑 ======
function showAddModal() {
  editMode.value = 'add'
  editForm.value = {}
  columnNames.value.forEach((col: string) => {
    editForm.value[col] = ''
  })
  editModalVisible.value = true
}

function showEditModal(record: any) {
  editMode.value = 'edit'
  editForm.value = { ...record }
  // 找到主键值 - 主键列名需要转驼峰以匹配行数据 key（structure 的 primaryKey 是大写，行数据 key 是驼峰）
  const toCamel = (s: string) => s.toLowerCase().replace(/_([a-z])/g, (_, c) => c.toUpperCase())
  const rawPk = structure.value?.primaryKey?.[0]
  const pk = rawPk ? toCamel(rawPk) : ''
  editPrimaryKey.value = pk || ''
  editRowId.value = pk ? String(record[pk] ?? '') : ''
  console.log('[DbManage] 编辑记录: 原始主键列=', rawPk, '转驼峰=', pk, '主键值=', editRowId.value)
  console.log('[DbManage] 编辑记录: record 字段=', Object.keys(record))
  editModalVisible.value = true
}

async function handleSave() {
  if (!selectedTable.value) return
  saveLoading.value = true
  try {
    const data = { ...editForm.value }
    // 清理空字符串值
    Object.keys(data).forEach(k => {
      if (data[k] === '' || data[k] === undefined) delete data[k]
    })
    delete data.__row_index
    console.log('[DbManage] 保存: 模式=', editMode.value, '表=', selectedTable.value, '数据=', data)
    if (editMode.value === 'edit') {
      console.log('[DbManage] 保存: 主键列=', editPrimaryKey.value, '主键值=', editRowId.value)
    }

    if (editMode.value === 'add') {
      await insertRow(selectedTable.value, data)
      message.success('新增成功')
    } else {
      await updateRow(selectedTable.value, editRowId.value, data)
      message.success('更新成功')
    }
    editModalVisible.value = false
    await loadData(dataPage.value)
  } catch (e: any) {
    console.error('[DbManage] 保存失败:', e?.response?.data || e?.message, e)
    message.error('保存失败: ' + (e?.response?.data?.message || e?.message || ''))
  } finally {
    saveLoading.value = false
  }
}

async function handleDelete(record: any) {
  if (!selectedTable.value) return
  const toCamel = (s: string) => s.toLowerCase().replace(/_([a-z])/g, (_, c) => c.toUpperCase())
  const rawPk = structure.value?.primaryKey?.[0]
  const pk = rawPk ? toCamel(rawPk) : ''
  const id = pk ? String(record[pk] ?? '') : String(record[Object.keys(record)[0]] ?? '')
  console.log('[DbManage] 删除: 表=', selectedTable.value, '原始主键=', rawPk, '转驼峰=', pk, 'id=', id)
  try {
    await deleteRow(selectedTable.value, id)
    message.success('删除成功')
    await loadData(dataPage.value)
  } catch (e: any) {
    console.error('[DbManage] 删除失败:', e?.response?.data || e?.message, e)
    message.error('删除失败: ' + (e?.response?.data?.message || e?.message || ''))
  }
}

// ====== 导出 ======
async function handleExport() {
  if (!selectedTable.value) return
  exportLoading.value = true
  console.log('[DbManage] 导出数据: 表=', selectedTable.value)
  try {
    const res = await exportTableData(selectedTable.value)
    const data = res.data
    console.log('[DbManage] 导出成功: 行数=', data?.rowCount, '列数=', data?.columns?.length)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${selectedTable.value}_${new Date().toISOString().slice(0,10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (e: any) {
    message.error('导出失败: ' + (e?.message || ''))
  } finally {
    exportLoading.value = false
  }
}

// ====== 导入 ======
function handleBeforeUpload(file: File): boolean {
  console.log('[DbManage] 读取导入文件:', file.name, '大小:', file.size)
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const json = JSON.parse(e.target?.result as string)
      const rows = json.rows || json
      if (Array.isArray(rows)) {
        importPreview.value = rows
        importRawData.value = json
        console.log('[DbManage] 导入文件解析成功: 行数=', rows.length, '首行=', rows[0])
        message.success('已读取 ' + rows.length + ' 条数据')
      } else {
        console.error('[DbManage] 导入文件格式错误: 缺少 rows 数组')
        message.error('JSON 格式错误：需要 rows 数组')
      }
    } catch (err) {
      console.error('[DbManage] 导入文件解析失败:', err)
      message.error('JSON 解析失败，请检查文件格式')
    }
  }
  reader.readAsText(file)
  return false // 阻止自动上传
}

async function handleImport() {
  if (!selectedTable.value || !importRawData.value) return
  importLoading.value = true
  importResult.value = null
  const rows = importRawData.value.rows || importRawData.value
  console.log('[DbManage] 导入数据: 表=', selectedTable.value, '模式=', importMode.value, '行数=', rows.length)
  try {
    const res = await importTableData(selectedTable.value, importMode.value, rows)
    importResult.value = res.data
    console.log('[DbManage] 导入成功:', res.data)
    message.success(res.data?.message || '导入完成')
    await loadData(1)
  } catch (e: any) {
    console.error('[DbManage] 导入失败:', e?.response?.data || e?.message, e)
    message.error('导入失败: ' + (e?.response?.data?.message || e?.message || ''))
  } finally {
    importLoading.value = false
  }
}

function resetImport() {
  importPreview.value = null
  importRawData.value = null
  importResult.value = null
}

// ====== 全量导入导出 ======
async function handleFullExport() {
  fullExportLoading.value = true
  console.log('[DbManage] 全量导出: 开始')
  try {
    const res = await exportAllData()
    const data = res.data
    console.log('[DbManage] 全量导出成功: 表数=', data?.tableCount, '总行数=', data?.totalRows)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `easy-ops-full-backup_${new Date().toISOString().slice(0,10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    message.success(`全量导出成功: ${data?.tableCount} 张表, ${data?.totalRows} 行数据`)
  } catch (e: any) {
    console.error('[DbManage] 全量导出失败:', e?.message, e)
    message.error('全量导出失败: ' + (e?.message || ''))
  } finally {
    fullExportLoading.value = false
  }
}

function handleFullImportUpload(file: File): boolean {
  console.log('[DbManage] 全量导入: 读取文件', file.name, '大小:', file.size)
  const reader = new FileReader()
  reader.onload = async (e) => {
    try {
      const json = JSON.parse(e.target?.result as string)
      if (!json.tables || typeof json.tables !== 'object') {
        message.error('JSON 格式错误：需要 tables 对象')
        return
      }
      const tableCount = Object.keys(json.tables).length
      const totalRows = Object.values(json.tables).reduce((sum: number, t: any) => sum + (t.rows?.length || 0), 0)
      console.log('[DbManage] 全量导入: 解析成功 表数=', tableCount, '总行数=', totalRows)
      fullImportResult.value = { tableCount, totalRows, fileName: file.name, json }
      message.success(`已读取 ${tableCount} 张表, ${totalRows} 行数据`)
    } catch (err) {
      console.error('[DbManage] 全量导入: 解析失败', err)
      message.error('JSON 解析失败，请检查文件格式')
    }
  }
  reader.readAsText(file)
  return false
}

async function handleFullImport() {
  if (!fullImportResult.value?.json) return
  const tableCount = Object.keys(fullImportResult.value.json.tables).length
  fullImportLoading.value = true
  console.log('[DbManage] 全量导入: 模式=', fullImportMode.value, '表数=', tableCount)
  try {
    const res = await importAllData(fullImportMode.value, fullImportResult.value.json.tables)
    console.log('[DbManage] 全量导入成功:', res.data)
    message.success(res.data?.message || '全量导入完成')
    fullImportVisible.value = false
    fullImportResult.value = null
    await loadTables()
  } catch (e: any) {
    console.error('[DbManage] 全量导入失败:', e?.response?.data || e?.message, e)
    message.error('全量导入失败: ' + (e?.response?.data?.message || e?.message || ''))
  } finally {
    fullImportLoading.value = false
  }
}

function copyText(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    message.success('已复制到剪贴板')
  })
}

onMounted(() => {
  loadTables()
})
</script>

<style scoped>
.table-list-scroll {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  overflow-x: hidden;
}
.table-list-scroll::-webkit-scrollbar { width: 4px; }
.table-list-scroll::-webkit-scrollbar-thumb { background: #d9d9d9; border-radius: 2px; }
.db-group-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  margin: 8px 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: #666;
  cursor: pointer;
  border-radius: 4px;
  user-select: none;
}
.db-group-title:first-child { margin-top: 0; }
.db-group-title:hover { background: #fafafa; }
.db-table-item {
  padding: 7px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.db-table-item:hover {
  background: #f1f8e9;
  border-color: #c8e6c9;
}
.db-table-item.active {
  background: linear-gradient(135deg, #e8f5e9 0%, #f1f8e9 100%);
  border-color: #81c784;
  font-weight: 600;
  color: #2e7d32;
  box-shadow: 0 1px 3px rgba(76, 175, 80, 0.15);
}
.ddl-pre {
  background: #f6f8fa;
  color: #24292f;
  border: 1px solid #e8e8e8;
  padding: 14px;
  border-radius: 8px;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  line-height: 1.6;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  margin: 0;
}
</style>
