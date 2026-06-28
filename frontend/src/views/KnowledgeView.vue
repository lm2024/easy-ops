<template>
  <div class="knowledge-view">
    <!-- 顶部 Header -->
    <div class="knowledge-header">
      <div class="header-left">
        <book-outlined style="color: #722ed1; font-size: 18px" />
        <span class="header-title">运维文档</span>
      </div>
      <div class="header-center">
        <a-input-search
          v-model:value="store.searchQuery"
          placeholder="搜索文档..."
          style="width: 300px"
          @search="handleGlobalSearch"
        />
      </div>
      <div class="header-right">
        <a-button type="primary" size="small" :disabled="!store.currentCategoryId" @click="handleNewDoc">
          <plus-outlined /> 新建文档
        </a-button>
        <a-button size="small" @click="templateModalVisible = true">
          <file-text-outlined /> 从模板创建
        </a-button>
        <a-button size="small" @click="tagModalVisible = true">
          <tags-outlined /> 标签管理
        </a-button>
      </div>
    </div>

    <!-- 三栏布局 -->
    <div class="knowledge-body">
      <!-- 左侧：笔记本树 -->
      <div class="panel-left">
        <NotebookTreePanel />
      </div>

      <!-- 中间：文档列表 -->
      <div class="panel-middle">
        <DocumentListPanel />
      </div>

      <!-- 右侧：编辑器 -->
      <div class="panel-right">
        <DocumentEditorPanel />
      </div>
    </div>

    <!-- 从模板创建弹窗 -->
    <a-modal
      v-model:open="templateModalVisible"
      title="从模板创建文档"
      @ok="handleCreateFromTemplate"
      :ok-button-props="{ disabled: !selectedTemplateId }"
    >
      <a-list :data-source="store.templates" size="small" :loading="templateLoading">
        <template #renderItem="{ item }">
          <a-list-item
            @click="selectedTemplateId = item.id"
            :class="{ 'template-selected': selectedTemplateId === item.id }"
            style="cursor: pointer"
          >
            <a-list-item-meta :title="item.name" :description="item.description" />
          </a-list-item>
        </template>
      </a-list>
    </a-modal>

    <!-- 标签管理弹窗 -->
    <a-modal v-model:open="tagModalVisible" title="标签管理" :footer="null">
      <div style="margin-bottom: 12px">
        <a-input-search
          v-model:value="newTagName"
          placeholder="输入标签名称..."
          enter-button="创建"
          @search="handleCreateTag"
        />
      </div>
      <a-list :data-source="store.tags" size="small">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-tag :color="item.color || '#722ed1'">{{ item.name }}</a-tag>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import { useKnowledgeStore } from '../stores/knowledgeStore'
import NotebookTreePanel from '../components/knowledge/NotebookTreePanel.vue'
import DocumentListPanel from '../components/knowledge/DocumentListPanel.vue'
import DocumentEditorPanel from '../components/knowledge/DocumentEditorPanel.vue'
import {
  BookOutlined, PlusOutlined, FileTextOutlined, TagsOutlined
} from '@ant-design/icons-vue'

const store = useKnowledgeStore()

const templateModalVisible = ref(false)
const selectedTemplateId = ref<number | null>(null)
const templateLoading = ref(false)
const tagModalVisible = ref(false)
const newTagName = ref('')

/** 全局搜索 */
function handleGlobalSearch(value: string) {
  if (!value) return
  // 切换到搜索结果视图（由 DocumentListPanel 处理）
  store.setCurrentView('category')
  message.info(`搜索: ${value}`)
}

/** 新建文档 */
function handleNewDoc() {
  if (!store.currentCategoryId) {
    message.warning('请先选择分类')
    return
  }
  store.setCurrentDocument({
    categoryId: store.currentCategoryId,
    title: '新文档',
    content: '',
    status: 0
  })
}

/** 从模板创建 */
async function handleCreateFromTemplate() {
  if (!selectedTemplateId.value || !store.currentCategoryId) {
    message.warning('请选择模板和分类')
    return
  }
  await store.createFromTemplateAction(selectedTemplateId.value, store.currentCategoryId)
  templateModalVisible.value = false
  selectedTemplateId.value = null
}

/** 创建标签 */
async function handleCreateTag() {
  if (!newTagName.value) return
  await store.addTag({ name: newTagName.value })
  newTagName.value = ''
}

/** 打开模板弹窗时加载模板 */
async function loadTemplates() {
  templateLoading.value = true
  await store.fetchTemplates()
  templateLoading.value = false
}

// 监听模板弹窗打开
import { watch } from 'vue'
watch(templateModalVisible, (val) => {
  if (val) {
    selectedTemplateId.value = null
    loadTemplates()
  }
})

/** Ctrl+S 保存快捷键 */
function onKeyDown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    store.saveDocument()
  }
}

onMounted(() => {
  store.fetchCategories()
  store.fetchTags()
  store.fetchFavorites()
  store.fetchRecentAccess()
  document.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeyDown)
})
</script>

<style scoped>
.knowledge-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0a0a0b;
  color: #f4f4f5;
}

.knowledge-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
  min-height: 48px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #f4f4f5;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 0 24px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.knowledge-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.panel-left {
  width: 260px;
  border-right: 1px solid #2a2a2a;
  overflow-y: auto;
}

.panel-middle {
  width: 280px;
  border-right: 1px solid #2a2a2a;
  overflow-y: auto;
}

.panel-right {
  flex: 1;
  overflow-y: auto;
}

.template-selected {
  background: rgba(114, 46, 209, 0.12);
  border-radius: 4px;
}
</style>
