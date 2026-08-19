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
        <!-- 左侧：分类表列表（分类元数据来自后端 easyops.data.table-meta） -->
        <a-col :span="6">
          <div style="border-right: 1px solid #f0f0f0; padding-right: 12px; height: 100%">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
              <span style="font-weight:500;color:#666">数据表 ({{ tables.length }})</span>
              <a-space size="4">
                <a-tooltip title="一键清空数据（推荐先清流水表，保护表除外）">
                  <a-button size="small" type="primary" danger ghost @click="openClearAll">
                    <clear-outlined /> 清空
                  </a-button>
                </a-tooltip>
                <a-button size="small" type="link" @click="loadTables(true)" :loading="tableLoading">
                  <reload-outlined />
                </a-button>
              </a-space>
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
                  <span>{{ group.icon }} {{ group.category }}
                    <a-tag v-if="group.flowCount > 0" color="orange" style="margin-left:4px;font-size:10px;line-height:16px">
                      流水 {{ group.flowCount }}
                    </a-tag>
                  </span>
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
                    <div style="font-size:13px;line-height:1.4;display:flex;align-items:center;gap:4px">
                      <span>{{ t.label }}</span>
                      <a-tooltip v-if="!t.recognized" title="未在 easyops.data.table-meta 中登记，已按命名规则自动归类。建议管理员补充登记。">
                        <warning-outlined style="color:#faad14;font-size:12px" />
                      </a-tooltip>
                      <a-tag v-if="t.type === 'FLOW'" color="orange" style="font-size:10px;line-height:14px;margin:0">流水</a-tag>
                      <a-tag v-else-if="t.type === 'AGENT_SYNC'" color="geekblue" style="font-size:10px;line-height:14px;margin:0">Agent同步</a-tag>
                      <a-tag v-if="!t.clearable" color="red" style="font-size:10px;line-height:14px;margin:0">保护</a-tag>
                    </div>
                    <div style="font-size:11px;color:#999;margin-top:1px;display:flex;justify-content:space-between;align-items:center">
                      <span>{{ t.tableName }}</span>
                      <span :class="['db-row-count', { 'db-row-count--large': t.rowCount > 100000 }]">
                        {{ formatCount(t.rowCount) }}
                      </span>
                    </div>
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
                <!-- 大表警告 -->
                <a-alert
                  v-if="currentMeta?.rowCount > 100000"
                  type="warning"
                  show-icon
                  style="margin-bottom:12px"
                  :message="`当前表约 ${formatNumber(currentMeta.rowCount)} 行，为大数据量表。建议通过「清空数据」处理而非全量浏览。`"
                />
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
                    <!-- 清空数据：保护表禁用，其余任意表可清 -->
                    <a-tooltip v-if="!currentMeta?.clearable" title="系统基础表受保护，禁止清空">
                      <a-button danger disabled><delete-outlined /> 清空</a-button>
                    </a-tooltip>
                    <a-popconfirm
                      v-else
                      :title="currentMeta?.flowType ? '确认清空整张表？此操作不可恢复！' : '该表为配置/基础数据表，清空可能影响业务，确认继续？'"
                      ok-text="继续"
                      cancel-text="取消"
                      @confirm="openClearTable"
                    >
                      <a-button danger><delete-outlined /> 清空</a-button>
                    </a-popconfirm>
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
                        <a-button size="small" type="link" @click="showEditModal(record)">
                          <edit-outlined />
                        </a-button>
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
                  <!-- 元数据卡片 -->
                  <a-card size="small" style="margin-bottom:16px">
                    <a-descriptions :column="4" size="small">
                      <a-descriptions-item label="分类">{{ currentMeta?.category || '-' }}</a-descriptions-item>
                      <a-descriptions-item label="类型">{{ typeLabel(currentMeta?.type) }}</a-descriptions-item>
                      <a-descriptions-item label="数据来源">{{ currentMeta?.source || '-' }}</a-descriptions-item>
                      <a-descriptions-item label="保留天数">{{ currentMeta?.retainDays ?? '-' }}</a-descriptions-item>
                      <a-descriptions-item label="行数">{{ currentMeta?.rowCount >= 0 ? formatNumber(currentMeta.rowCount) : '统计中...' }}</a-descriptions-item>
                      <a-descriptions-item label="可清空">
                        <a-tag :color="currentMeta?.clearable ? 'orange' : 'default'">
                          {{ currentMeta?.clearable ? '是（流水表）' : '否（基础/配置表）' }}
                        </a-tag>
                      </a-descriptions-item>
                      <a-descriptions-item label="识别状态">
                        <a-tag :color="currentMeta?.recognized ? 'green' : 'warning'">
                          {{ currentMeta?.recognized ? '已登记' : '未登记（自动归类）' }}
                        </a-tag>
                      </a-descriptions-item>
                    </a-descriptions>
                  </a-card>
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

    <!-- 清空单表二次确认弹窗 -->
    <a-modal
      v-model:open="clearModalVisible"
      title="清空表数据"
      :ok-button-props="{ danger: true, disabled: clearConfirmText !== selectedTable }"
      ok-text="确认清空"
      cancel-text="取消"
      @ok="handleClearTable"
      :confirm-loading="clearLoading"
    >
      <a-alert
        type="error"
        show-icon
        :message="`即将清空表 ${selectedTable}`"
        :description="`该操作将删除表中全部 ${currentMeta?.rowCount >= 0 ? formatNumber(currentMeta.rowCount) : ''} 行数据，不可恢复！`"
        style="margin-bottom:16px"
      />
      <p style="color:#888;font-size:13px">请输入表名 <b>{{ selectedTable }}</b> 以确认操作：</p>
      <a-input v-model:value="clearConfirmText" :placeholder="'请输入 ' + selectedTable" />
      <div v-if="clearResult" style="margin-top:12px">
        <a-alert :type="clearResult.success ? 'success' : 'error'" :message="clearResult.message" show-icon />
        <a-alert
          v-if="clearResult.success"
          type="info"
          style="margin-top:8px"
          message="提示：H2 数据文件不会立即缩小，建议在流量低谷重启 server 触发自动 COMPACT（或使用 scripts/rescue-h2.sh）"
        />
      </div>
    </a-modal>

    <!-- 一键清空弹窗 -->
    <a-modal
      v-model:open="clearAllVisible"
      title="一键清空数据"
      width="680px"
      :footer="null"
    >
      <a-alert
        type="error"
        show-icon
        message="危险操作"
        description="以下为全部可清空的表（sys_user 等系统保护表已排除）。建议优先清空「流水」类型表，清空后数据不可恢复！"
        style="margin-bottom:16px"
      />
      <div v-if="clearAllLoading" style="text-align:center;padding:30px"><a-spin /></div>
      <template v-else>
        <a-table
          :data-source="clearAllTables"
          :columns="clearAllColumns"
          :pagination="false"
          size="small"
          :row-selection="{ selectedRowKeys: clearAllSelected, onChange: (keys: any) => clearAllSelected = keys }"
          row-key="tableName"
        />
        <div style="margin-top:12px;display:flex;justify-content:space-between;align-items:center">
          <span style="color:#888;font-size:12px">
            已选 {{ clearAllSelected.length }} 张表
          </span>
          <a-space>
            <a-button @click="clearAllSelected = clearAllTables.filter((t: any) => t.flowType).map((t: any) => t.tableName)">选流水表</a-button>
            <a-button @click="clearAllSelected = clearAllTables.map((t: any) => t.tableName)">全选</a-button>
            <a-popconfirm
              title="确认清空所选表？此操作不可恢复！"
              ok-text="确认"
              cancel-text="取消"
              @confirm="handleClearAll"
            >
              <a-button type="primary" danger :disabled="clearAllSelected.length === 0">
                <clear-outlined /> 清空所选 ({{ clearAllSelected.length }})
              </a-button>
            </a-popconfirm>
          </a-space>
        </div>
        <div v-if="clearAllResult" style="margin-top:16px">
          <a-alert
            :type="clearAllResult.success ? 'success' : 'warning'"
            :message="clearAllResult.message"
            show-icon
          />
          <a-table
            v-if="clearAllResult.details"
            :data-source="Object.entries(clearAllResult.details).map(([k, v]: any) => ({ table: k, result: v }))"
            :columns="[{ title: '表', dataIndex: 'table' }, { title: '结果', dataIndex: 'result' }]"
            :pagination="false"
            size="small"
            style="margin-top:8px"
            row-key="table"
          />
        </div>
      </template>
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
  exportAllData, importAllData,
  listClearableTables, clearTable, clearBatch, clearAllFlow
} from '../api/db'
import {
  DatabaseOutlined, TableOutlined, ReloadOutlined,
  PlusOutlined, EditOutlined, DeleteOutlined,
  DownloadOutlined, UploadOutlined, InboxOutlined,
  CopyOutlined, SearchOutlined, ClearOutlined, WarningOutlined
} from '@ant-design/icons-vue'

// ====== 状态 ======
const tables = ref<any[]>([])
const selectedTable = ref<string>('')
const tableLoading = ref(false)
const activeTab = ref('data')
const tableSearch = ref('')
const collapsedGroups = ref<Set<string>>(new Set())

// 当前选中表的元数据
const currentMeta = computed(() => {
  if (!selectedTable.value) return null
  return tables.value.find((t: any) => t.tableName === selectedTable.value) || null
})

// ====== 分组后的表列表（分类元数据来自后端） ======
const filteredTableGroups = computed(() => {
  const keyword = tableSearch.value.toLowerCase().trim()
  const groups: Record<string, any> = {}
  for (const t of tables.value) {
    const name = t.tableName || ''
    const category = t.category || '其他'
    const label = t.label || name
    // 搜索过滤：匹配英文表名或中文别名
    if (keyword && !name.toLowerCase().includes(keyword) && !label.includes(keyword)) continue
    if (!groups[category]) groups[category] = { tables: [], icon: t.icon || '📄', flowCount: 0 }
    groups[category].tables.push({ ...t, label })
    if (t.flowType) groups[category].flowCount++
  }
  // 排序：保留后端分类顺序，未识别归"其他"放最后
  const ordered: any[] = []
  const seen = new Set<string>()
  for (const t of tables.value) {
    const cat = t.category || '其他'
    if (!seen.has(cat) && groups[cat]) {
      seen.add(cat)
      ordered.push({ category: cat, ...groups[cat] })
    }
  }
  return ordered
})

function toggleGroup(category: string) {
  const s = new Set(collapsedGroups.value)
  if (s.has(category)) s.delete(category)
  else s.add(category)
  collapsedGroups.value = s
}

function typeLabel(type?: string) {
  const map: Record<string, string> = {
    BASE: '基础表',
    CONFIG: '配置表',
    FLOW: '流水表',
    AGENT_SYNC: 'Agent 同步表'
  }
  return type ? (map[type] || type) : '-'
}

function formatNumber(n: number) {
  if (n === null || n === undefined) return '-'
  return n.toLocaleString()
}

/** 行数缩写：≥1万 显示 x.x万，≥1亿 显示 x.x亿；否则千分位。rowCount=-1 显示 '-' */
function formatCount(n: number) {
  if (n === null || n === undefined || n < 0) return '-'
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return n.toLocaleString()
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

// ====== 清空数据 ======
const clearModalVisible = ref(false)
const clearConfirmText = ref('')
const clearLoading = ref(false)
const clearResult = ref<any>(null)

const clearAllVisible = ref(false)
const clearAllLoading = ref(false)
const clearAllTables = ref<any[]>([])
const clearAllSelected = ref<any[]>([])
const clearAllResult = ref<any>(null)

// 列定义（表结构展示用）
const colColumns = [
  { title: '列名', dataIndex: 'name', key: 'name', width: 150 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 120 },
  { title: '最大长度', dataIndex: 'maxLen', key: 'maxLen', width: 100 },
  { title: '可空', dataIndex: 'nullable', key: 'nullable', width: 80 },
  { title: '默认值', dataIndex: 'defaultValue', key: 'defaultValue', width: 150 },
  { title: '自增', dataIndex: 'autoInc', key: 'autoInc', width: 80 }
]

const clearAllColumns = [
  { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 180 },
  { title: '别名', dataIndex: 'label', key: 'label', width: 120 },
  { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
  { title: '类型', key: 'type', width: 90, customRender: ({ record }: any) => typeLabel(record.type) },
  { title: '行数', key: 'rowCount', width: 90, customRender: ({ record }: any) => formatNumber(record.rowCount) },
  { title: '推荐', key: 'flowType', width: 70, customRender: ({ record }: any) => record.flowType ? '流水' : '-' }
]

// ====== 方法 ======

function showTotalFn(total: number) { return "共 " + total + " 条" }

async function loadTables(withRowCount = true) {
  tableLoading.value = true
  try {
    const res = await listTables(withRowCount)
    tables.value = res.data || []
  } catch (e: any) {
    console.error('[DbManage] 加载表列表失败:', e?.message, e)
    message.error('加载表列表失败: ' + (e?.message || ''))
  } finally {
    tableLoading.value = false
  }
}

async function selectTable(name: string) {
  selectedTable.value = name
  activeTab.value = 'data'
  clearResult.value = null
  await Promise.all([loadStructure(), loadData(1)])
}

async function loadStructure() {
  if (!selectedTable.value) return
  structureLoading.value = true
  try {
    const res = await getTableStructure(selectedTable.value)
    structure.value = res.data
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
  try {
    const res = await queryTableData(selectedTable.value, dataPage.value, dataPageSize.value, searchText.value)
    const d = res.data
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
// columnNames 是后端返回的原始大写下划线列名；模板 v-model 绑定的 col.dataIndex 是驼峰。
// 这里统一转驼峰初始化 editForm，避免大写下划线空串被错误地随请求发出
// （camelToUpperSnake 对已经是大写下划线的字符串会破坏成 U_S_E_R_N_A_M_E）。
function toCamelName(s: string) {
  return s.toLowerCase().replace(/_([a-z])/g, (_, c) => c.toUpperCase())
}

function showAddModal() {
  editMode.value = 'add'
  editForm.value = {}
  columnNames.value.forEach((col: string) => {
    editForm.value[toCamelName(col)] = ''
  })
  editModalVisible.value = true
}

function showEditModal(record: any) {
  editMode.value = 'edit'
  // 仅保留模板实际渲染的列（即 dataColumns 里的驼峰 key），防止 __row_index 等内部字段被发送
  const allowed = new Set(dataColumns.value.map((c: any) => c.dataIndex).filter((k: string) => k !== '__actions'))
  const filtered: Record<string, any> = {}
  for (const k of Object.keys(record)) {
    if (k === '__row_index') continue
    if (allowed.has(k)) filtered[k] = record[k]
  }
  editForm.value = filtered
  // 找到主键值 - 主键列名需要转驼峰以匹配行数据 key（structure 的 primaryKey 是大写，行数据 key 是驼峰）
  const rawPk = structure.value?.primaryKey?.[0]
  const pk = rawPk ? toCamelName(rawPk) : ''
  editPrimaryKey.value = pk || ''
  editRowId.value = pk ? String(record[pk] ?? '') : ''
  editModalVisible.value = true
}

async function handleSave() {
  if (!selectedTable.value) return
  saveLoading.value = true
  try {
    const data: Record<string, any> = {}
    for (const [k, v] of Object.entries(editForm.value)) {
      if (k === '__row_index' || k === '__actions') continue
      if (v === '' || v === undefined) continue  // 空串视为"不修改"，与后端默认值/可空兼容
      data[k] = v
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
  const rawPk = structure.value?.primaryKey?.[0]
  const pk = rawPk ? toCamelName(rawPk) : ''
  // 过滤掉 __row_index，避免无主键表 fallback 时拿到行号
  const dataKeys = Object.keys(record).filter(k => k !== '__row_index')
  let id: string
  if (pk && record[pk] !== undefined && record[pk] !== null) {
    id = String(record[pk])
  } else if (dataKeys.length > 0) {
    id = String(record[dataKeys[0]] ?? '')
  } else {
    message.error('记录缺少主键字段，无法删除')
    return
  }
  if (!id) {
    message.error('主键值为空，无法删除')
    return
  }
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
  try {
    const res = await exportTableData(selectedTable.value)
    const data = res.data
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
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const json = JSON.parse(e.target?.result as string)
      const rows = json.rows || json
      if (Array.isArray(rows)) {
        importPreview.value = rows
        importRawData.value = json
        message.success('已读取 ' + rows.length + ' 条数据')
      } else {
        message.error('JSON 格式错误：需要 rows 数组')
      }
    } catch (err) {
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
  try {
    const res = await importTableData(selectedTable.value, importMode.value, rows)
    importResult.value = res.data
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

// ====== 清空单表 ======
function openClearTable() {
  clearModalVisible.value = true
  clearConfirmText.value = ''
  clearResult.value = null
}

async function handleClearTable() {
  if (!selectedTable.value) return
  clearLoading.value = true
  try {
    const res = await clearTable(selectedTable.value, true)
    const data = res.data
    clearResult.value = {
      success: true,
      message: `已清空表 ${selectedTable.value}，删除 ${data?.deleted ?? 0} 行`
    }
    message.success('清空成功')
    await Promise.all([loadData(1), loadStructure(), loadTables(true)])
  } catch (e: any) {
    clearResult.value = {
      success: false,
      message: '清空失败: ' + (e?.response?.data?.message || e?.message || '')
    }
    message.error('清空失败')
  } finally {
    clearLoading.value = false
  }
}

// ====== 一键清空 ======
async function openClearAll() {
  clearAllVisible.value = true
  clearAllLoading.value = true
  clearAllResult.value = null
  clearAllSelected.value = []
  try {
    const res = await listClearableTables()
    clearAllTables.value = res.data || []
    // 默认勾选"推荐清空"的流水表/Agent同步表
    clearAllSelected.value = clearAllTables.value.filter((t: any) => t.flowType).map((t: any) => t.tableName)
  } catch (e: any) {
    message.error('加载可清空表失败: ' + (e?.message || ''))
  } finally {
    clearAllLoading.value = false
  }
}

async function handleClearAll() {
  if (clearAllSelected.value.length === 0) return
  clearAllResult.value = null
  try {
    if (clearAllSelected.value.length === clearAllTables.value.length) {
      const res = await clearAllFlow(true)
      const data = res.data
      clearAllResult.value = {
        success: true,
        message: `已一键清空全部可清空表，共删除 ${data?.totalDeleted ?? 0} 行`,
        details: data?.details
      }
    } else {
      const res = await clearBatch(clearAllSelected.value, true)
      const data = res.data
      clearAllResult.value = {
        success: true,
        message: `已清空 ${clearAllSelected.value.length} 张表，共删除 ${data?.totalDeleted ?? 0} 行`,
        details: data?.details
      }
    }
    message.success('清空完成')
    await Promise.all([loadTables(true), loadStructure()])
  } catch (e: any) {
    clearAllResult.value = {
      success: false,
      message: '清空失败: ' + (e?.response?.data?.message || e?.message || '')
    }
    message.error('清空失败')
  }
}

// ====== 全量导入导出 ======
async function handleFullExport() {
  fullExportLoading.value = true
  try {
    const res = await exportAllData()
    const data = res.data
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
      fullImportResult.value = { tableCount, totalRows, fileName: file.name, json }
      message.success(`已读取 ${tableCount} 张表, ${totalRows} 行数据`)
    } catch (err) {
      message.error('JSON 解析失败，请检查文件格式')
    }
  }
  reader.readAsText(file)
  return false
}

async function handleFullImport() {
  if (!fullImportResult.value?.json) return
  fullImportLoading.value = true
  try {
    const res = await importAllData(fullImportMode.value, fullImportResult.value.json.tables)
    message.success(res.data?.message || '全量导入完成')
    fullImportVisible.value = false
    fullImportResult.value = null
    await loadTables(true)
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
  loadTables(true)
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
.db-row-count {
  color: #bbb;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.db-row-count--large {
  color: #fa8c16;
  font-weight: 600;
}
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
