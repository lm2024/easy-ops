<template>
  <div class="tag-sidebar" v-if="visible">
    <!-- 面板头部 -->
    <div class="tag-sidebar-header">
      <span class="panel-title">标签管理</span>
      <close-outlined class="close-btn" @click="emit('close')" />
    </div>

    <!-- 创建标签 -->
    <div class="create-tag-area">
      <a-input
        v-model:value="newTagName"
        placeholder="标签名称"
        size="small"
        style="width: 140px"
      />
      <input
        type="color"
        v-model="newTagColor"
        class="color-picker"
        title="选择标签颜色"
      />
      <a-button
        type="primary"
        size="small"
        :disabled="!newTagName.trim()"
        @click="handleCreateTag"
      >
        <plus-outlined /> 创建
      </a-button>
    </div>

    <!-- 标签列表 -->
    <div class="tag-list">
      <div v-if="loading" class="loading-state">
        <a-spin />
      </div>
      <div v-else-if="tagList.length === 0" class="empty-hint">
        暂无标签，创建标签开始分类
      </div>
      <div
        v-for="tag in tagList"
        :key="tag.id"
        class="tag-item"
        @click="emit('filterByTag', tag.id)"
      >
        <span class="tag-dot" :style="{ background: tag.color || '#722ed1' }"></span>
        <span class="tag-name">{{ tag.name }}</span>
        <a-button
          type="text"
          size="small"
          danger
          class="tag-delete-btn"
          @click.stop="handleDeleteTag(tag)"
        >
          <delete-outlined />
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { CloseOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { listTags, createTag, deleteTag } from '../../api/knowledge-tag'
import type { KbTagModel } from '../../types'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  filterByTag: [tagId: number]
}>()

const loading = ref(false)
const tagList = ref<KbTagModel[]>([])
const newTagName = ref('')
const newTagColor = ref('#722ed1')

/** 加载标签列表 */
async function loadTags() {
  loading.value = true
  try {
    const res = await listTags()
    tagList.value = res.data || []
  } catch (e: any) {
    message.error('获取标签列表失败: ' + (e.message || '未知错误'))
    tagList.value = []
  } finally {
    loading.value = false
  }
}

/** 创建标签 */
async function handleCreateTag() {
  if (!newTagName.value.trim()) return
  try {
    await createTag({ name: newTagName.value.trim(), color: newTagColor.value })
    message.success('标签已创建')
    newTagName.value = ''
    await loadTags()
  } catch (e: any) {
    message.error('创建标签失败: ' + (e.message || '未知错误'))
  }
}

/** 删除标签 */
function handleDeleteTag(tag: KbTagModel) {
  Modal.confirm({
    title: '确认删除标签',
    content: `删除标签「${tag.name}」后，所有关联此标签的文档-标签关系也将被移除。确定删除吗？`,
    okText: '确认删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteTag(tag.id)
        message.success('标签已删除')
        await loadTags()
      } catch (e: any) {
        message.error('删除标签失败: ' + (e.message || '未知错误'))
      }
    },
  })
}

/** 面板打开时加载 */
watch(() => props.visible, (val) => {
  if (val) {
    loadTags()
  }
})

onMounted(() => {
  if (props.visible) {
    loadTags()
  }
})
</script>

<style scoped>
.tag-sidebar {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: 320px;
  background: #0a0a0b;
  border-left: 1px solid #2a2a2a;
  z-index: 100;
  display: flex;
  flex-direction: column;
  color: #f4f4f5;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.3);
}

.tag-sidebar-header {
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

.create-tag-area {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
}

.color-picker {
  width: 28px;
  height: 28px;
  border: 1px solid #2a2a2a;
  border-radius: 4px;
  background: #1a1a1b;
  cursor: pointer;
  padding: 1px;
}

.tag-list {
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

.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: #71717a;
  font-size: 13px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 4px;
  margin-bottom: 4px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.tag-item:hover {
  border-color: #722ed1;
}

.tag-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tag-name {
  flex: 1;
  font-size: 13px;
  color: #f4f4f5;
}

.tag-delete-btn {
  opacity: 0;
  transition: opacity 0.15s;
}

.tag-item:hover .tag-delete-btn {
  opacity: 1;
}

/* 暗色主题覆盖 */
.tag-sidebar :deep(.ant-input) {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.tag-sidebar :deep(.ant-btn-primary) {
  background: #722ed1;
  border-color: #722ed1;
}
</style>
