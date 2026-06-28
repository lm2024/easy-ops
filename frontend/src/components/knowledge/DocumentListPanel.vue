<template>
  <div class="document-list-panel">
    <!-- 顶部 -->
    <div class="list-header">
      <a-input-search
        v-model:value="localSearchQuery"
        placeholder="搜索文档..."
        size="small"
        style="flex: 1"
        @search="handleSearch"
      />
      <a-select v-model:value="sortBy" size="small" style="width: 120px; margin-left: 8px">
        <a-select-option value="updateTime">最近编辑</a-select-option>
        <a-select-option value="title">标题</a-select-option>
        <a-select-option value="createTime">创建时间</a-select-option>
      </a-select>
    </div>

    <!-- 文档列表 -->
    <div class="doc-list">
      <!-- 收藏视图 -->
      <template v-if="store.currentView === 'favorites'">
        <div
          v-for="fav in favoriteDocs"
          :key="fav.documentId"
          class="doc-card"
          :class="{ active: store.currentDocument?.id === fav.documentId }"
          @click="openFavoriteDoc(fav)"
        >
          <div class="doc-title">
            <star-outlined style="color: #722ed1; margin-right: 4px" />
            文档 #{{ fav.documentId }}
          </div>
          <div class="doc-meta">
            <span class="doc-time">{{ formatTime(fav.createTime) }}</span>
          </div>
        </div>
        <a-empty v-if="!favoriteDocs.length" description="暂无收藏" :image-style="{ height: '40px' }" />
      </template>

      <!-- 最近访问视图 -->
      <template v-else-if="store.currentView === 'recent'">
        <div
          v-for="recent in store.recentAccessList"
          :key="recent.documentId"
          class="doc-card"
          :class="{ active: store.currentDocument?.id === recent.documentId }"
          @click="openRecentDoc(recent)"
        >
          <div class="doc-title">
            <clock-circle-outlined style="color: #a1a1aa; margin-right: 4px" />
            文档 #{{ recent.documentId }}
          </div>
          <div class="doc-meta">
            <span class="doc-time">{{ formatTime(recent.createTime) }}</span>
          </div>
        </div>
        <a-empty v-if="!store.recentAccessList.length" description="暂无最近访问" :image-style="{ height: '40px' }" />
      </template>

      <!-- 分类文档视图 -->
      <template v-else>
        <div
          v-for="doc in sortedDocuments"
          :key="doc.id"
          class="doc-card"
          :class="{ active: store.currentDocument?.id === doc.id }"
          @click="selectDocument(doc)"
        >
          <div class="doc-title-row">
            <span class="doc-title">{{ doc.title }}</span>
            <star-filled
              v-if="isFavorite(doc.id!)"
              style="color: #722ed1; cursor: pointer"
              @click.stop="store.toggleFavorite(doc.id!)"
            />
            <star-outlined
              v-else
              style="color: #a1a1aa; cursor: pointer"
              @click.stop="store.toggleFavorite(doc.id!)"
            />
          </div>
          <div class="doc-summary">{{ doc.summary || '无摘要' }}</div>
          <div class="doc-meta">
            <span class="doc-time">{{ formatTime(doc.updateTime) }}</span>
            <a-tag
              v-for="docTag in (doc as any).tagNames"
              :key="docTag"
              size="small"
              :color="getTagColor(docTag)"
            >
              {{ docTag }}
            </a-tag>
          </div>
        </div>
        <a-empty v-if="!sortedDocuments.length" description="暂无文档" :image-style="{ height: '40px' }" />
      </template>
    </div>

    <!-- 底部操作 -->
    <div class="list-footer" v-if="store.currentView === 'category'">
      <a-button type="primary" block @click="createNewDoc" :disabled="!store.currentCategoryId">
        <plus-outlined /> 新建文档
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useKnowledgeStore } from '../../stores/knowledgeStore'
import { useSearch } from '../../composables/useSearch'
import type { KbDocumentModel, KbFavoriteModel, KbRecentAccessModel } from '../../types'
import dayjs from 'dayjs'
import {
  StarOutlined, StarFilled, ClockCircleOutlined, PlusOutlined
} from '@ant-design/icons-vue'

const store = useKnowledgeStore()
const search = useSearch()

const localSearchQuery = ref('')
const sortBy = ref<string>('updateTime')

/** 时间格式化 */
function formatTime(ms: number | undefined): string {
  if (!ms) return ''
  return dayjs(ms).format('YYYY-MM-DD HH:mm')
}

/** 获取标签颜色 */
function getTagColor(tagName: string): string {
  const tag = store.tags.find(t => t.name === tagName)
  return tag?.color || '#722ed1'
}

/** 排序后的文档列表 */
const sortedDocuments = computed(() => {
  const docs = [...store.documents]
  switch (sortBy.value) {
    case 'updateTime':
      return docs.sort((a, b) => (b.updateTime || 0) - (a.updateTime || 0))
    case 'title':
      return docs.sort((a, b) => a.title.localeCompare(b.title))
    case 'createTime':
      return docs.sort((a, b) => (b.createTime || 0) - (a.createTime || 0))
    default:
      return docs
  }
})

/** 收藏视图的文档列表 */
const favoriteDocs = computed(() => store.favorites)

/** 是否收藏 */
function isFavorite(docId: number): boolean {
  return store.favorites.some(f => f.documentId === docId)
}

/** 选择文档 */
async function selectDocument(doc: KbDocumentModel) {
  if (doc.id) {
    await store.fetchDocument(doc.id)
  } else {
    store.setCurrentDocument(doc)
  }
}

/** 打开收藏文档 */
async function openFavoriteDoc(fav: KbFavoriteModel) {
  await store.fetchDocument(fav.documentId)
}

/** 打开最近访问文档 */
async function openRecentDoc(recent: KbRecentAccessModel) {
  await store.fetchDocument(recent.documentId)
}

/** 搜索 */
async function handleSearch(value: string) {
  if (!value) return
  await search.search(value)
  // 将搜索结果临时替换文档列表
  if (search.results.value.length > 0) {
    store.setDocuments(search.results.value)
  }
}

/** 新建文档 */
function createNewDoc() {
  if (!store.currentCategoryId) return
  store.setCurrentDocument({
    categoryId: store.currentCategoryId,
    title: '新文档',
    content: '',
    status: 0
  })
}

// 当分类变化时重新加载文档
watch(() => store.currentCategoryId, (newId) => {
  if (newId && store.currentView === 'category') {
    store.fetchDocuments(newId)
  }
})
</script>

<style scoped>
.document-list-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 8px;
  background: #0a0a0b;
}

.list-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.doc-list {
  flex: 1;
  overflow-y: auto;
}

.doc-card {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #2a2a2a;
  margin-bottom: 6px;
  transition: all 0.2s;
  background: #1a1a1b;
}

.doc-card:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: #3a3a3a;
}

.doc-card.active {
  background: rgba(114, 46, 209, 0.15);
  border-color: #722ed1;
}

.doc-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.doc-title {
  font-size: 14px;
  font-weight: 500;
  color: #f4f4f5;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-summary {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 11px;
}

.doc-time {
  color: #71717a;
}

.list-footer {
  padding-top: 8px;
}
</style>
