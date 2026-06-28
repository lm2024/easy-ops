<template>
  <a-modal
    :open="visible"
    title="外链分享"
    :width="500"
    :footer="null"
    @cancel="emit('close')"
    :styles="{ body: { background: '#0a0a0b', padding: '16px' } }"
    class="share-link-dialog"
  >
    <!-- 生成分享链接 -->
    <div class="share-section" v-if="!shareLink">
      <div class="section-desc">为文档「{{ documentTitle }}」生成分享链接</div>

      <!-- 密码设置 -->
      <div class="option-row">
        <a-checkbox v-model:checked="usePassword">设置密码保护</a-checkbox>
        <a-input-password
          v-if="usePassword"
          v-model:value="password"
          placeholder="6位以上密码"
          style="width: 200px; margin-left: 12px"
          size="small"
        />
      </div>

      <!-- 过期时间 -->
      <div class="option-row">
        <a-checkbox v-model:checked="useExpireTime">设置过期时间</a-checkbox>
        <a-date-picker
          v-if="useExpireTime"
          v-model:value="expireTime"
          placeholder="选择过期日期"
          style="width: 200px; margin-left: 12px"
          size="small"
          :disabled-date="disabledDate"
        />
      </div>

      <a-button
        type="primary"
        block
        :loading="generating"
        @click="handleCreateShareLink"
      >
        <link-outlined /> 生成分享链接
      </a-button>
    </div>

    <!-- 已生成的分享链接 -->
    <div class="share-section" v-else>
      <div class="section-title">分享链接已生成</div>
      <div class="share-url-box">
        <span class="share-url">{{ shareUrl }}</span>
        <a-button size="small" @click="copyShareUrl">
          <copy-outlined /> 复制
        </a-button>
      </div>

      <div class="share-detail" v-if="shareLink.password">
        <lock-outlined style="color: #F59E0B" />
        <span>密码保护: {{ shareLink.password }}</span>
      </div>
      <div class="share-detail" v-if="shareLink.expireTime">
        <clock-circle-outlined style="color: #EF4444" />
        <span>过期时间: {{ formatTime(shareLink.expireTime) }}</span>
      </div>

      <!-- 删除分享链接 -->
      <a-button
        danger
        block
        style="margin-top: 12px"
        @click="handleDeleteShareLink"
      >
        <delete-outlined /> 删除分享链接
      </a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import {
  LinkOutlined, CopyOutlined, LockOutlined,
  ClockCircleOutlined, DeleteOutlined
} from '@ant-design/icons-vue'
import { createShareLink, deleteShareLink } from '../../api/knowledge-share'
import type { KbShareLinkModel } from '../../types'

const props = defineProps<{
  visible: boolean
  documentId: number
  documentTitle: string
}>()

const emit = defineEmits<{
  close: []
}>()

const usePassword = ref(false)
const password = ref('')
const useExpireTime = ref(false)
const expireTime = ref<Dayjs | null>(null)
const generating = ref(false)
const shareLink = ref<KbShareLinkModel | null>(null)

/** 生成的分享 URL */
const shareUrl = computed(() => {
  if (!shareLink.value) return ''
  return `${window.location.origin}/knowledge/share/${shareLink.value.token}`
})

/** 禁止选择过去的日期 */
function disabledDate(current: Dayjs): boolean {
  return current && current < dayjs().endOf('day')
}

/** 生成分享链接 */
async function handleCreateShareLink() {
  generating.value = true
  try {
    const data: { documentId: number; password?: string; expireTime?: number } = {
      documentId: props.documentId,
    }
    if (usePassword.value && password.value) {
      if (password.value.length < 6) {
        message.warning('密码至少6位')
        generating.value = false
        return
      }
      data.password = password.value
    }
    if (useExpireTime.value && expireTime.value) {
      data.expireTime = expireTime.value.valueOf()
    }
    const res = await createShareLink(data)
    shareLink.value = res.data
    message.success('分享链接已生成')
  } catch (e: any) {
    message.error('生成分享链接失败: ' + (e.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

/** 复制分享 URL */
async function copyShareUrl() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    message.success('链接已复制到剪贴板')
  } catch {
    // clipboard API 可能不可用，fallback
    const textarea = document.createElement('textarea')
    textarea.value = shareUrl.value
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('链接已复制到剪贴板')
  }
}

/** 删除分享链接 */
function handleDeleteShareLink() {
  if (!shareLink.value) return
  Modal.confirm({
    title: '确认删除分享链接',
    content: '删除后，所有通过此链接的访问将失效。确定要删除吗？',
    okText: '确认删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteShareLink(shareLink.value!.id)
        message.success('分享链接已删除')
        shareLink.value = null
        emit('close')
      } catch (e: any) {
        message.error('删除分享链接失败: ' + (e.message || '未知错误'))
      }
    },
  })
}

/** 时间格式化 */
function formatTime(ms: number): string {
  return dayjs(ms).format('YYYY-MM-DD HH:mm')
}
</script>

<style scoped>
.share-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-desc {
  font-size: 13px;
  color: #a1a1aa;
  margin-bottom: 4px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #722ed1;
  margin-bottom: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}

.share-url-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 4px;
  padding: 8px 12px;
}

.share-url {
  flex: 1;
  font-size: 13px;
  color: #722ed1;
  word-break: break-all;
  user-select: all;
}

.share-detail {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 4px;
}

/* 暗色主题覆盖 */
.share-link-dialog :deep(.ant-modal-header) {
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
}

.share-link-dialog :deep(.ant-modal-title) {
  color: #f4f4f5;
}

.share-link-dialog :deep(.ant-modal-close) {
  color: #a1a1aa;
}

.share-link-dialog :deep(.ant-modal-content) {
  background: #0a0a0b;
}

.share-link-dialog :deep(.ant-checkbox-wrapper) {
  color: #f4f4f5;
}

.share-link-dialog :deep(.ant-input-password) {
  background: #1a1a1b;
  border-color: #2a2a2a;
  color: #f4f4f5;
}

.share-link-dialog :deep(.ant-picker) {
  background: #1a1a1b;
  border-color: #2a2a2a;
}

.share-link-dialog :deep(.ant-picker-input > input) {
  color: #f4f4f5;
}

.share-link-dialog :deep(.ant-btn-primary) {
  background: #722ed1;
  border-color: #722ed1;
}
</style>
