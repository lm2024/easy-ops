<template>
  <div class="search-panel" v-if="visible">
    <!-- 面板头部 -->
    <div class="search-panel-header">
      <span class="panel-title">高级搜索</span>
      <close-outlined class="close-btn" @click="emit('close')" />
    </div>

    <!-- 搜索输入 -->
    <div class="search-input-area">
      <a-input-search
        v-model:value="searchQuery"
        placeholder="输入关键词搜索文档..."
        size="large"
        @search="handleSearch"
      />
    </div>

    <!-- 高级筛选 -->
    <div class="filter-section">
      <div class="filter-title">筛选条件</div>
      <div class="filter-row">
        <label class="filter-label">分类：</label>
        <a-select
          v-model:value="filterCategoryId"
          placeholder="选择分类"
          allow-clear
          style="width: 100%"
          :options="categoryOptions"
          size="small"
        />
      </div>
      <div class="filter-row">
        <label class="filter-label">标签：</label>
        <a-select
          v-model:value="filterTags"
          mode="multiple"
          placeholder="选择标签"
          allow-clear
          style="width: 100%"
          :options="tagOptions"
          size="small"
        />
      </div>
      <div class="filter-row">
        <label class="filter-label">时间：</label>
        <a-range-picker
          v-model:value="filterDateRange"
          size="small"
          style="width: 100%"
          :placeholder="['开始日期', '结束日期']"
        />
      </div>
      <div class="filter-actions">
        <a-button size="small" type="primary" @click="handleAdvancedSearch">
          <search-outlined /> 搜索
        </a-button>
        <a-button size="small" @click="resetFilters">
          重置
        </a-button>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div class="search-results">
      <div v-if="loading" class="loading-state">
        <a-spin />
      </div>
      <div v-else-if="results.length === 0" class="empty-results">
        <p v-if="hasSearched">未找到匹配的文档</p>
        <p v-else>输入关键词或设置筛选条件开始搜索</p>
      </div>
      <div v-else>
        <div class="results-header">
          <span>找到 {{ total }} 个结果</span>
        </div>
        <div
          v-for="doc in results"
          :key="doc.id"
          class="result-card"
          @click="doc.id && emit('selectDocument', doc.id)"
        >
          <div class="result-title" v-html="highlightText(doc.title || '无标题')"></div>
          <div class="result-summary" v-if="doc.summary" v-html="highlightText(doc.summary)"></div>
          <div class="result-meta">
            <span v-if="doc.categoryId" class="result-category">
              {{ getCategoryName(doc.categoryId) }}
            </span>
            <span class="result-time">{{ formatTime(doc.updateTime) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import { CloseOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useKnowledgeStore } from '../../stores/knowledgeStore'
import { fullTextSearch, advancedSearch } from '../../api/knowledge-search'
import type { KbDocumentModel } from '../../types'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  selectDocument: [docId: number]
}>()

const store = useKnowledgeStore()

const searchQuery = ref('')
const filterCategoryId = ref<number | undefined>(undefined)
const filterTags = ref<string[]>([])
const filterDateRange = ref<[Dayjs, Dayjs] | null>(null)
const loading = ref(false)
const hasSearched = ref(false)
const results = ref<KbDocumentModel[]>([])
const total = ref(0)

/** 分类选项 */
const categoryOptions = computed(() => {
  return store.categories.map(cat => ({
    value: cat.id,
    label: cat.name,
  }))
})

/** 标签选项 */
const tagOptions = computed(() => {
  return store.tags.map(tag => ({
    value: tag.name,
    label: tag.name,
  }))
})

/** 全文搜索 */
async function handleSearch(value: string) {
  if (!value.trim()) return
  loading.value = true
  hasSearched.value = true
  try {
    const res = await fullTextSearch(value, 1, 20)
    results.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    message.error('搜索失败: ' + (e.message || '未知错误'))
    results.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 高级搜索 */
async function handleAdvancedSearch() {
  loading.value = true
  hasSearched.value = true
  try {
    const params: {
      query?: string
      categoryId?: number
      tags?: string
      page?: number
      size?: number
    } = {}
    if (searchQuery.value.trim()) {
      params.query = searchQuery.value.trim()
    }
    if (filterCategoryId.value !== undefined) {
      params.categoryId = filterCategoryId.value
    }
    if (filterTags.value.length > 0) {
      params.tags = filterTags.value.join(',')
    }
    const res = await advancedSearch(params)
    results.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    message.error('高级搜索失败: ' + (e.message || '未知错误'))
    results.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 重置筛选 */
function resetFilters() {
  filterCategoryId.value = undefined
  filterTags.value = []
  filterDateRange.value = null
}

/** 高亮关键词 */
function highlightText(text: string): string {
  if (!searchQuery.value.trim()) return text
  const keywords = searchQuery.value.trim().split(/\s+/)
  let result = text
  for (const kw of keywords) {
    const escaped = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    result = result.replace(
      new RegExp(escaped, 'gi'),
      (match) => `<span class="search-highlight">${match}</span>`
    )
  }
  return result
}

/** 获取分类名称 */
function getCategoryName(categoryId: number): string {
  const cat = store.categories.find(c => c.id === categoryId)
  return cat ? cat.name : `分类#${categoryId}`
}

/** 时间格式化 */
function formatTime(ms: number | undefined): string {
  if (!ms) return ''
  return dayjs(ms).format('YYYY-MM-DD HH:mm')
}

/** 面板打开时加载分类和标签 */
watch(() => props.visible, (val) => {
  if (val) {
    store.fetchCategories()
    store.fetchTags()
  }
})

onMounted(() => {
  store.fetchCategories()
  store.fetchTags()
})
</script>

<style scoped>
.search-panel {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: 400px;
  background: #0a0a0b;
  border-left: 1px solid #2a2a2a;
  z-index: 100;
  display: flex;
  flex-direction: column;
  color: #f4f4f5;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.3);
}

.search-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #f4f4f5;
}

.close-btn {
  cursor: pointer;
  color: #a1a1aa;
  font-size: 16px;
  transition: color 0.15s;
}

.close-btn:hover {
  color: #f4f4f5;
}

.search-input-area {
  padding: 12px 16px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
}

.filter-section {
  padding: 12px 16px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
}

.filter-title {
  font-size: 13px;
  font-weight: 600;
  color: #722ed1;
  margin-bottom: 8px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.filter-label {
  font-size: 12px;
  color: #a1a1aa;
  width: 50px;
  flex-shrink: 0;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.search-results {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.empty-results {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #71717a;
  font-size: 13px;
}

.results-header {
  font-size: 12px;
  color: #a1a1aa;
  margin-bottom: 12px;
}

.result-card {
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.result-card:hover {
  border-color: #722ed1;
  background: rgba(114, 46, 209, 0.06);
}

.result-title {
  font-size: 14px;
  font-weight: 600;
  color: #f4f4f5;
  margin-bottom: 4px;
}

.result-summary {
  font-size: 12px;
  color: #a1a1aa;
  margin-bottom: 6px;
  max-height: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: #71717a;
}

.result-category {
  color: #722ed1;
}

/* 搜索关键词高亮 */
:deep(.search-highlight) {
  background: rgba(245, 158, 11, 0.3);
  color: #F59E0B;
  border-radius: 2px;
  padding: 0 2px;
}

/* 暗色主题覆盖 */
.search-panel :deep(.ant-input) {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.search-panel :deep(.ant-input:focus) {
  border-color: #722ed1;
}

.search-panel :deep(.ant-select) {
  color: #f4f4f5;
}

.search-panel :deep(.ant-select-selector) {
  background: #1a1a1b;
  border-color: #2a2a2a;
  color: #f4f4f5;
}

.search-panel :deep(.ant-picker) {
  background: #1a1a1b;
  border-color: #2a2a2a;
}

.search-panel :deep(.ant-picker-input > input) {
  color: #f4f4f5;
}

.search-panel :deep(.ant-btn) {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.search-panel :deep(.ant-btn-primary) {
  background: #722ed1;
  border-color: #722ed1;
  color: #fff;
}
</style>
