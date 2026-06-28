<template>
  <div class="notebook-tree-panel">
    <!-- 虚拟分类节点 -->
    <div class="virtual-categories">
      <div
        class="virtual-item"
        :class="{ active: store.currentView === 'favorites' }"
        @click="viewFavorites"
      >
        <star-outlined class="virtual-icon" />
        <span>收藏</span>
      </div>
      <div
        class="virtual-item"
        :class="{ active: store.currentView === 'recent' }"
        @click="viewRecent"
      >
        <clock-circle-outlined class="virtual-icon" />
        <span>最近访问</span>
      </div>
    </div>

    <a-divider style="margin: 8px 0; border-color: #2a2a2a" />

    <!-- 分类树 -->
    <div class="tree-container">
      <a-tree
        v-if="treeData.length"
        :tree-data="treeData"
        :field-names="{ title: 'name', key: 'id', children: 'children' }"
        :draggable="true"
        default-expand-all
        :selected-keys="selectedKeys"
        @select="onSelect"
        @drop="onDrop"
        @rightClick="onRightClick"
      >
        <template #title="nodeData">
          <span :style="{ color: nodeData.color || '#f4f4f5' }">
            {{ nodeData.icon ? nodeData.icon + ' ' : '' }}{{ nodeData.name }}
          </span>
        </template>
      </a-tree>
      <a-empty v-else description="暂无分类" :image-style="{ height: '40px' }" />
    </div>

    <!-- 新建分类按钮 -->
    <div class="tree-footer">
      <a-button block size="small" @click="showAddCategoryModal">
        <plus-outlined /> 新建分类
      </a-button>
    </div>

    <!-- 右键菜单浮层 -->
    <div
      v-if="contextMenuVisible"
      class="context-menu-overlay"
      :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }"
    >
      <div class="context-menu" @click.stop>
        <div class="context-menu-item" @click="handleContextAction('rename')">重命名</div>
        <div class="context-menu-item" @click="handleContextAction('addChild')">新建子分类</div>
        <div class="context-menu-item danger" @click="handleContextAction('delete')">删除分类</div>
      </div>
    </div>

    <!-- 新建分类弹窗 -->
    <a-modal v-model:open="addCategoryVisible" title="新建分类" @ok="handleAddCategory">
      <a-form layout="vertical">
        <a-form-item label="分类名称">
          <a-input v-model:value="newCategoryName" placeholder="请输入分类名称" />
        </a-form-item>
        <a-form-item label="颜色">
          <a-input v-model:value="newCategoryColor" placeholder="如 #722ed1" />
        </a-form-item>
        <a-form-item label="父分类" v-if="contextNode">
          <a-input :value="contextNode.name" disabled />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 重命名弹窗 -->
    <a-modal v-model:open="renameVisible" title="重命名分类" @ok="handleRename">
      <a-form layout="vertical">
        <a-form-item label="新名称">
          <a-input v-model:value="renameName" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import { useKnowledgeStore } from '../../stores/knowledgeStore'
import type { KbCategoryModel } from '../../types'
import {
  StarOutlined, ClockCircleOutlined, PlusOutlined
} from '@ant-design/icons-vue'

const store = useKnowledgeStore()

// ====== 状态 ======
const addCategoryVisible = ref(false)
const newCategoryName = ref('')
const newCategoryColor = ref('#722ed1')
const renameVisible = ref(false)
const renameName = ref('')
const contextNode = ref<KbCategoryModel | null>(null)
const contextMenuVisible = ref(false)
const contextMenuPos = ref({ x: 0, y: 0 })
const selectedKeys = computed(() => {
  return store.currentCategoryId ? [store.currentCategoryId] : []
})

// ====== 分类树数据 ======
const treeData = computed(() => store.categories)

// ====== 虚拟分类 ======
function viewFavorites() {
  store.setCurrentView('favorites')
  store.setCurrentCategoryId(null)
  store.fetchFavorites()
}

function viewRecent() {
  store.setCurrentView('recent')
  store.setCurrentCategoryId(null)
  store.fetchRecentAccess()
}

// ====== 分类树交互 ======
function onSelect(keys: (string | number)[]) {
  if (keys.length) {
    const id = Number(keys[0])
    store.setCurrentCategoryId(id)
    store.setCurrentView('category')
    store.fetchDocuments(id)
    store.setCurrentDocument(null)
  }
}

/** 拖拽排序 */
function onDrop(info: any) {
  const dragKey = info.dragNode.key
  const dropKey = info.node.key
  const dropPosition = info.dropPosition
  // 调用 API 更新排序
  store.updateCategoryAction(Number(dragKey), {
    parentId: Number(dropKey),
    sortOrder: dropPosition
  })
}

/** 右键菜单 */
function onRightClick({ node, event }: any) {
  event.preventDefault()
  contextNode.value = node.dataRef || node
  contextMenuPos.value = { x: event.clientX, y: event.clientY }
  contextMenuVisible.value = true
}

function handleContextAction(key: string) {
  contextMenuVisible.value = false
  if (!contextNode.value) return
  switch (key) {
    case 'rename':
      renameName.value = contextNode.value.name
      renameVisible.value = true
      break
    case 'addChild':
      newCategoryName.value = ''
      newCategoryColor.value = '#722ed1'
      addCategoryVisible.value = true
      break
    case 'delete':
      store.deleteCategoryAction(contextNode.value.id)
      break
  }
}

/** 点击其他区域关闭右键菜单 */
function onDocumentClick() {
  contextMenuVisible.value = false
}

/** 重命名分类 */
async function handleRename() {
  if (!contextNode.value || !renameName.value) return
  await store.updateCategoryAction(contextNode.value.id, { name: renameName.value })
  renameVisible.value = false
}

/** 新建分类弹窗 */
function showAddCategoryModal() {
  contextNode.value = null // 清除父分类
  newCategoryName.value = ''
  newCategoryColor.value = '#722ed1'
  addCategoryVisible.value = true
}

/** 新建分类 */
async function handleAddCategory() {
  if (!newCategoryName.value) {
    message.warning('请输入分类名称')
    return
  }
  await store.addCategory({
    name: newCategoryName.value,
    color: newCategoryColor.value,
    parentId: contextNode.value?.id || 0,
    sortOrder: 0
  })
  addCategoryVisible.value = false
}

onMounted(() => {
  store.fetchCategories()
  document.addEventListener('click', onDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})
</script>

<style scoped>
.notebook-tree-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 8px;
  background: #0a0a0b;
}

.virtual-categories {
  margin-bottom: 4px;
}

.virtual-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 4px;
  color: #a1a1aa;
  font-size: 13px;
  transition: all 0.2s;
}

.virtual-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #f4f4f5;
}

.virtual-item.active {
  background: rgba(114, 46, 209, 0.15);
  color: #722ed1;
}

.virtual-icon {
  font-size: 14px;
}

.tree-container {
  flex: 1;
  overflow-y: auto;
}

.tree-footer {
  padding-top: 8px;
}

/* 右键菜单 */
.context-menu-overlay {
  position: fixed;
  z-index: 1050;
}

.context-menu {
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  padding: 4px 0;
  min-width: 140px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.context-menu-item {
  padding: 8px 16px;
  cursor: pointer;
  color: #f4f4f5;
  font-size: 13px;
  transition: background 0.2s;
}

.context-menu-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.context-menu-item.danger {
  color: #ef4444;
}

.context-menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.12);
}

/* 暗色主题下的树样式覆盖 */
.tree-container :deep(.ant-tree) {
  color: #f4f4f5;
  background: transparent;
}

.tree-container :deep(.ant-tree-node-content-wrapper) {
  color: #f4f4f5;
}

.tree-container :deep(.ant-tree-node-content-wrapper:hover) {
  background: rgba(255, 255, 255, 0.08);
}

.tree-container :deep(.ant-tree-node-content-wrapper.ant-tree-node-selected) {
  background: rgba(114, 46, 209, 0.15);
}

.tree-container :deep(.ant-tree-switcher) {
  color: #a1a1aa;
}
</style>
