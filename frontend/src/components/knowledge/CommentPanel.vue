<template>
  <div class="comment-panel">
    <!-- 评论列表 -->
    <div class="comment-list">
      <div v-if="comments.length === 0" class="empty-hint">
        <p>暂无评论，快来发表第一条吧</p>
      </div>
      <div v-for="comment in sortedComments" :key="comment.id" class="comment-item">
        <div class="comment-header">
          <a-avatar
            :size="24"
            :style="{ backgroundColor: getUserColor(comment.userId || 0), fontSize: '11px' }"
          >
            U{{ comment.userId }}
          </a-avatar>
          <span class="comment-username">用户{{ comment.userId }}</span>
          <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
        </div>
        <div class="comment-body">
          <p class="comment-content">{{ comment.content }}</p>
        </div>
        <div class="comment-actions">
          <button class="comment-action-btn" @click="toggleLike(comment)">
            <span :class="{ 'liked': likedMap[comment.id || 0] }">❤</span>
            <span class="like-count">{{ comment.likes || 0 }}</span>
          </button>
          <button class="comment-action-btn" @click="showReplyInput(comment)">
            回复
          </button>
        </div>

        <!-- 回复列表 -->
        <div v-if="getReplies(comment.id || 0).length > 0" class="reply-list">
          <div v-for="reply in getReplies(comment.id || 0)" :key="reply.id" class="reply-item">
            <div class="reply-header">
              <a-avatar
                :size="18"
                :style="{ backgroundColor: getUserColor(reply.userId || 0), fontSize: '9px' }"
              >
                U{{ reply.userId }}
              </a-avatar>
              <span class="reply-username">用户{{ reply.userId }}</span>
              <span v-if="reply.replyToId !== comment.id" class="reply-to">
                → 用户{{ findCommentUser(reply.replyToId) }}
              </span>
              <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
            </div>
            <p class="reply-content">{{ reply.content }}</p>
          </div>
        </div>

        <!-- 回复输入框 -->
        <div v-if="replyingTo === (comment.id || 0)" class="reply-input-area">
          <a-input
            v-model:value="replyContent"
            placeholder="回复..."
            size="small"
            @pressEnter="submitReply(comment)"
          >
            <template #addonAfter>
              <a-button size="small" type="primary" @click="submitReply(comment)">回复</a-button>
            </template>
          </a-input>
        </div>
      </div>
    </div>

    <!-- 新评论输入 -->
    <div class="new-comment-area">
      <a-input
        v-model:value="newCommentContent"
        placeholder="添加评论...（输入 @ 提及用户）"
        @pressEnter="submitNewComment"
      >
        <template #addonAfter>
          <a-button size="small" type="primary" @click="submitNewComment">发表</a-button>
        </template>
      </a-input>
      <!-- @ 提及用户选择器 -->
      <div v-if="showMentionSelector" class="mention-selector">
        <div
          v-for="user in mentionedUsers"
          :key="user"
          class="mention-item"
          @click="selectMention(user)"
        >
          用户{{ user }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import dayjs from 'dayjs'
import type { KbCommentModel } from '../../types'

const props = defineProps<{
  documentId: number
  comments: KbCommentModel[]
}>()

const emit = defineEmits<{
  (e: 'addComment', documentId: number, content: string): void
  (e: 'replyComment', commentId: number, content: string, replyToId: number): void
  (e: 'likeComment', commentId: number): void
}>()

const newCommentContent = ref('')
const replyContent = ref('')
const replyingTo = ref<number | null>(null)
const likedMap = ref<Record<number, boolean>>({})
const showMentionSelector = ref(false)

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

/** 评论按时间排序 */
const sortedComments = computed(() => {
  const topLevel = props.comments.filter(
    (c) => !c.parentId && c.type !== 'ANNOTATION'
  )
  return topLevel.sort((a, b) => (b.createTime || 0) - (a.createTime || 0))
})

/** 获取某条评论的回复列表 */
function getReplies(commentId: number): KbCommentModel[] {
  return props.comments
    .filter((c) => c.parentId === commentId && c.type !== 'ANNOTATION')
    .sort((a, b) => (a.createTime || 0) - (b.createTime || 0))
}

/** 找到评论的用户 ID（用于回复 @ 显示） */
function findCommentUser(replyToId: number | undefined): number | string {
  if (!replyToId) return '?'
  const target = props.comments.find((c) => c.id === replyToId)
  return target?.userId || '?'
}

/** 提取已出现的用户 ID 列表（用于 @ 提及） */
const mentionedUsers = computed(() => {
  const userIds = new Set<number>()
  props.comments.forEach((c) => {
    if (c.userId) userIds.add(c.userId)
  })
  return Array.from(userIds)
})

/** 点赞切换 */
function toggleLike(comment: KbCommentModel) {
  const commentId = comment.id || 0
  if (likedMap.value[commentId]) {
    likedMap.value[commentId] = false
  } else {
    likedMap.value[commentId] = true
    emit('likeComment', commentId)
  }
}

/** 显示回复输入框 */
function showReplyInput(comment: KbCommentModel) {
  replyingTo.value = comment.id || null
  replyContent.value = ''
}

/** 提交回复 */
function submitReply(comment: KbCommentModel) {
  if (!replyContent.value || !comment.id) return
  emit('replyComment', comment.id, replyContent.value, comment.id)
  replyContent.value = ''
  replyingTo.value = null
}

/** 提交新评论 */
function submitNewComment() {
  if (!newCommentContent.value) return
  emit('addComment', props.documentId, newCommentContent.value)
  newCommentContent.value = ''
  showMentionSelector.value = false
}

/** 选择 @ 提及的用户 */
function selectMention(userId: number) {
  newCommentContent.value += `@用户${userId} `
  showMentionSelector.value = false
}

// 监听输入框中 @ 符号，弹出提及选择器
watch(newCommentContent, (val) => {
  if (val.endsWith('@')) {
    showMentionSelector.value = true
  } else {
    showMentionSelector.value = false
  }
})
</script>

<style scoped>
.comment-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0a0a0b;
  color: #f4f4f5;
}

.comment-list {
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

.comment-item {
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 10px;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.comment-username {
  font-size: 13px;
  font-weight: 500;
  color: #f4f4f5;
}

.comment-time {
  font-size: 11px;
  color: #71717a;
}

.comment-body {
  margin-bottom: 6px;
}

.comment-content {
  font-size: 13px;
  color: #f4f4f5;
  line-height: 1.5;
  margin: 0;
}

.comment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: none;
  background: transparent;
  color: #71717a;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s;
}

.comment-action-btn:hover {
  background: #2a2a2a;
  color: #f4f4f5;
}

.comment-action-btn .liked {
  color: #ef4444;
}

.like-count {
  font-size: 11px;
}

.reply-list {
  margin-top: 6px;
  padding-left: 16px;
  border-left: 2px solid #2a2a2a;
}

.reply-item {
  padding: 4px 8px;
  margin-bottom: 4px;
  background: rgba(26, 26, 27, 0.5);
  border-radius: 4px;
}

.reply-header {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
}

.reply-username {
  font-size: 12px;
  font-weight: 500;
  color: #a1a1aa;
}

.reply-to {
  font-size: 11px;
  color: #722ed1;
}

.reply-time {
  font-size: 10px;
  color: #71717a;
}

.reply-content {
  font-size: 12px;
  color: #f4f4f5;
  margin: 0;
  line-height: 1.4;
}

.reply-input-area {
  margin-top: 6px;
  padding-left: 16px;
}

.new-comment-area {
  padding: 12px;
  border-top: 1px solid #2a2a2a;
  background: #141414;
  position: relative;
}

.mention-selector {
  position: absolute;
  bottom: 48px;
  left: 12px;
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 4px;
  padding: 4px 0;
  z-index: 10;
  max-height: 160px;
  overflow-y: auto;
}

.mention-item {
  padding: 6px 12px;
  font-size: 12px;
  color: #f4f4f5;
  cursor: pointer;
  transition: background 0.15s;
}

.mention-item:hover {
  background: #2a2a2a;
}

/* 暗色主题覆盖 */
.comment-panel :deep(.ant-input) {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.comment-panel :deep(.ant-input:focus) {
  border-color: #722ed1;
}

.comment-panel :deep(.ant-input-group-addon) {
  background: #1a1a1b;
  border-color: #2a2a2a;
  color: #f4f4f5;
}
</style>
