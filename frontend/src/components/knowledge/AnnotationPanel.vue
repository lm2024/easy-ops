<template>
  <div class="annotation-panel">
    <!-- 批注列表 -->
    <div class="annotation-list">
      <div v-if="annotations.length === 0" class="empty-hint">
        <p>暂无批注。选中编辑器中的文字后可添加批注</p>
      </div>
      <div
        v-for="annotation in sortedAnnotations"
        :key="annotation.id"
        class="annotation-item"
        :class="{ active: activeAnnotationId === annotation.annotationId }"
        @click="onAnnotationClick(annotation)"
      >
        <div class="annotation-quote" v-if="annotation.annotationId">
          <span class="quote-icon">✎</span>
          <span class="quote-text">{{ annotation.annotationId }}</span>
        </div>
        <div class="annotation-header">
          <a-avatar
            :size="20"
            :style="{ backgroundColor: getUserColor(annotation.userId || 0), fontSize: '10px' }"
          >
            U{{ annotation.userId }}
          </a-avatar>
          <span class="annotation-username">用户{{ annotation.userId }}</span>
          <span class="annotation-time">{{ formatTime(annotation.createTime) }}</span>
        </div>
        <div class="annotation-body">
          <p class="annotation-content">{{ annotation.content }}</p>
        </div>

        <!-- 回复列表 -->
        <div v-if="getReplies(annotation.id || 0).length > 0" class="reply-list">
          <div v-for="reply in getReplies(annotation.id || 0)" :key="reply.id" class="reply-item">
            <div class="reply-header">
              <a-avatar
                :size="16"
                :style="{ backgroundColor: getUserColor(reply.userId || 0), fontSize: '8px' }"
              >
                U{{ reply.userId }}
              </a-avatar>
              <span class="reply-username">用户{{ reply.userId }}</span>
              <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
            </div>
            <p class="reply-content">{{ reply.content }}</p>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="annotation-actions">
          <button class="annotation-action-btn" @click="showReplyInput(annotation)">
            回复
          </button>
          <button class="annotation-action-btn delete-btn" @click="handleDelete(annotation)">
            删除
          </button>
        </div>

        <!-- 回复输入框 -->
        <div v-if="replyingTo === (annotation.id || 0)" class="reply-input-area">
          <a-input
            v-model:value="replyContent"
            placeholder="回复批注..."
            size="small"
            @pressEnter="submitReply(annotation)"
          >
            <template #addonAfter>
              <a-button size="small" type="primary" @click="submitReply(annotation)">回复</a-button>
            </template>
          </a-input>
        </div>
      </div>
    </div>

    <!-- 新建批注输入 -->
    <div class="new-annotation-area" v-if="pendingAnnotationId">
      <div class="pending-info">
        <span>已选中文字，批注 ID: {{ pendingAnnotationId }}</span>
      </div>
      <a-input
        v-model:value="newAnnotationContent"
        placeholder="填写批注内容..."
        @pressEnter="submitNewAnnotation"
      >
        <template #addonAfter>
          <a-button size="small" type="primary" @click="submitNewAnnotation">添加</a-button>
        </template>
      </a-input>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import dayjs from 'dayjs'
import type { KbCommentModel } from '../../types'

const props = defineProps<{
  annotations: KbCommentModel[]
  documentId: number
}>()

const emit = defineEmits<{
  (e: 'addAnnotation', annotationId: string, content: string, selectedText: string): void
  (e: 'removeAnnotation', annotationId: string): void
  (e: 'replyAnnotation', annotationId: string, content: string): void
}>()

const activeAnnotationId = ref<string | null>(null)
const replyingTo = ref<number | null>(null)
const replyContent = ref('')
const newAnnotationContent = ref('')
const pendingAnnotationId = ref<string | null>(null)
const pendingSelectedText = ref('')

const cursorColors = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']

function getUserColor(userId: number): string {
  const index = userId % cursorColors.length
  return cursorColors[index]
}

/** 时间格式化 */
function formatTime(ms: number | undefined): string {
  if (!ms) return ''
  return dayjs(ms).format('YYYY-MM-DD HH:mm')
}

/** 批注按时间排序 */
const sortedAnnotations = computed(() => {
  return [...props.annotations].sort((a, b) => (b.createTime || 0) - (a.createTime || 0))
})

/** 获取某条批注的回复列表 */
function getReplies(annotationId: number): KbCommentModel[] {
  return props.annotations
    .filter((a) => a.parentId === annotationId)
    .sort((a, b) => (a.createTime || 0) - (b.createTime || 0))
}

/** 点击批注，高亮编辑器中对应 annotation */
function onAnnotationClick(annotation: KbCommentModel) {
  activeAnnotationId.value = annotation.annotationId || null
  // 通过事件或外部组件来高亮编辑器中对应位置
  // 此处仅切换 active 状态，DocumentEditorPanel 负责联动编辑器
}

/** 显示回复输入框 */
function showReplyInput(annotation: KbCommentModel) {
  replyingTo.value = annotation.id || null
  replyContent.value = ''
}

/** 提交回复 */
function submitReply(annotation: KbCommentModel) {
  if (!replyContent.value || !annotation.annotationId) return
  emit('replyAnnotation', annotation.annotationId, replyContent.value)
  replyContent.value = ''
  replyingTo.value = null
}

/** 删除批注 */
function handleDelete(annotation: KbCommentModel) {
  if (annotation.annotationId) {
    emit('removeAnnotation', annotation.annotationId)
  }
}

/** 提交新批注 */
function submitNewAnnotation() {
  if (!newAnnotationContent.value || !pendingAnnotationId.value) return
  emit('addAnnotation', pendingAnnotationId.value, newAnnotationContent.value, pendingSelectedText.value)
  newAnnotationContent.value = ''
  pendingAnnotationId.value = null
  pendingSelectedText.value = ''
}

/** 设置待添加的批注（由编辑器选中文字后调用） */
function setPendingAnnotation(annotationId: string, selectedText: string) {
  pendingAnnotationId.value = annotationId
  pendingSelectedText.value = selectedText
}

defineExpose({
  setPendingAnnotation,
  activeAnnotationId,
})
</script>

<style scoped>
.annotation-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0a0a0b;
  color: #f4f4f5;
}

.annotation-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: #71717a;
  font-size: 13px;
}

.annotation-item {
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.annotation-item:hover {
  border-color: #f59e0b;
}

.annotation-item.active {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.08);
}

.annotation-quote {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  padding: 4px 8px;
  background: rgba(245, 158, 11, 0.15);
  border-radius: 4px;
  font-size: 11px;
  color: #f59e0b;
}

.quote-icon {
  font-size: 14px;
}

.quote-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.annotation-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.annotation-username {
  font-size: 12px;
  font-weight: 500;
  color: #f4f4f5;
}

.annotation-time {
  font-size: 10px;
  color: #71717a;
}

.annotation-body {
  margin-bottom: 6px;
}

.annotation-content {
  font-size: 13px;
  color: #f4f4f5;
  line-height: 1.5;
  margin: 0;
}

.annotation-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.annotation-action-btn {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: none;
  background: transparent;
  color: #71717a;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s;
}

.annotation-action-btn:hover {
  background: #2a2a2a;
  color: #f4f4f5;
}

.annotation-action-btn.delete-btn:hover {
  color: #ef4444;
}

.reply-list {
  margin-top: 6px;
  padding-left: 12px;
  border-left: 2px solid #2a2a2a;
}

.reply-item {
  padding: 4px 8px;
  margin-bottom: 4px;
}

.reply-header {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
}

.reply-username {
  font-size: 11px;
  color: #a1a1aa;
}

.reply-time {
  font-size: 10px;
  color: #71717a;
}

.reply-content {
  font-size: 12px;
  color: #f4f4f5;
  margin: 0;
}

.reply-input-area {
  margin-top: 6px;
}

.new-annotation-area {
  padding: 12px;
  border-top: 1px solid #2a2a2a;
  background: #141414;
}

.pending-info {
  font-size: 12px;
  color: #f59e0b;
  margin-bottom: 6px;
}

/* 暗色主题覆盖 */
.annotation-panel :deep(.ant-input) {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.annotation-panel :deep(.ant-input:focus) {
  border-color: #722ed1;
}
</style>
