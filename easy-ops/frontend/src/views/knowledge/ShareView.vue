<template>
  <div class="share-view">
    <!-- 密码输入 -->
    <div v-if="needPassword && !contentLoaded" class="share-password-section">
      <div class="share-card">
        <lock-outlined style="font-size: 32px; color: #722ed1" />
        <h2 class="share-heading">此文档需要密码访问</h2>
        <a-input-password
          v-model:value="inputPassword"
          placeholder="请输入访问密码"
          style="width: 240px"
          size="large"
          @pressEnter="handleAccessWithPassword"
        />
        <a-button
          type="primary"
          block
          :loading="accessing"
          @click="handleAccessWithPassword"
        >
          确认访问
        </a-button>
      </div>
    </div>

    <!-- 过期提示 -->
    <div v-if="isExpired" class="share-expired-section">
      <div class="share-card">
        <clock-circle-outlined style="font-size: 32px; color: #EF4444" />
        <h2 class="share-heading" style="color: #EF4444">链接已过期</h2>
        <p class="share-hint">此分享链接已过期，请联系分享者获取新链接</p>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="hasError" class="share-error-section">
      <div class="share-card">
        <warning-outlined style="font-size: 32px; color: #EF4444" />
        <h2 class="share-heading" style="color: #EF4444">无法访问</h2>
        <p class="share-hint">{{ errorMsg }}</p>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading && !hasError && !isExpired" class="share-loading">
      <a-spin size="large" />
      <p class="share-hint">正在加载文档...</p>
    </div>

    <!-- 文档内容展示 -->
    <div v-if="contentLoaded && !isExpired && !hasError" class="share-content-section">
      <div class="share-content-wrapper">
        <!-- 文档标题 -->
        <div class="share-doc-header">
          <h1 class="share-doc-title">{{ docTitle }}</h1>
          <span class="share-doc-meta">{{ formatTime(docUpdateTime) }}</span>
        </div>

        <!-- 文档内容（只读 Tiptap 或 MD 渲染） -->
        <div class="share-doc-content">
          <MarkdownPreview :markdown-content="docContent" />
        </div>

        <!-- 底部信息 -->
        <div class="share-footer">
          <span>通过外链分享查看 · 只读模式</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  LockOutlined, ClockCircleOutlined, WarningOutlined
} from '@ant-design/icons-vue'
import { accessByToken } from '../../api/knowledge-share'
import MarkdownPreview from '../../components/knowledge/MarkdownPreview.vue'

const route = useRoute()
const token = ref<string>('')

const loading = ref(false)
const accessing = ref(false)
const needPassword = ref(false)
const contentLoaded = ref(false)
const isExpired = ref(false)
const hasError = ref(false)
const errorMsg = ref('')

const inputPassword = ref('')
const docTitle = ref('')
const docContent = ref('')
const docUpdateTime = ref<number | undefined>(undefined)

/** 尝试访问分享文档 */
async function tryAccess(pwd?: string) {
  loading.value = true
  try {
    const res = await accessByToken(token.value, pwd)
    const data = res.data
    // 检查过期
    if (data.expireTime && data.expireTime < Date.now()) {
      isExpired.value = true
      loading.value = false
      return
    }
    docTitle.value = data.title || '未命名文档'
    docContent.value = data.content || ''
    docUpdateTime.value = data.updateTime
    contentLoaded.value = true
    needPassword.value = false
  } catch (e: any) {
    // 如果返回 403/密码错误 → 显示密码输入
    if (e.response?.status === 403 || e.message?.includes('密码')) {
      needPassword.value = true
      if (pwd) {
        message.error('密码错误，请重新输入')
      }
    } else if (e.response?.status === 404 || e.message?.includes('过期')) {
      isExpired.value = true
    } else {
      hasError.value = true
      errorMsg.value = e.message || '无法加载文档'
    }
  } finally {
    loading.value = false
  }
}

/** 使用密码访问 */
async function handleAccessWithPassword() {
  if (!inputPassword.value) {
    message.warning('请输入密码')
    return
  }
  accessing.value = true
  await tryAccess(inputPassword.value)
  accessing.value = false
}

/** 时间格式化 */
function formatTime(ms: number | undefined): string {
  if (!ms) return ''
  return dayjs(ms).format('YYYY-MM-DD HH:mm')
}

onMounted(async () => {
  token.value = route.params.token as string
  if (!token.value) {
    hasError.value = true
    errorMsg.value = '缺少分享链接 token'
    return
  }
  // 先尝试无密码访问
  await tryAccess()
})
</script>

<style scoped>
.share-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #0a0a0b;
  color: #f4f4f5;
  padding: 24px;
}

.share-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 8px;
  padding: 32px 24px;
  max-width: 400px;
  width: 100%;
}

.share-heading {
  font-size: 18px;
  font-weight: 600;
  color: #f4f4f5;
  margin: 0;
}

.share-hint {
  font-size: 13px;
  color: #71717a;
  margin: 0;
}

.share-password-section,
.share-expired-section,
.share-error-section {
  display: flex;
  align-items: center;
  justify-content: center;
}

.share-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.share-content-section {
  width: 100%;
  max-width: 800px;
}

.share-content-wrapper {
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 8px;
  padding: 24px 32px;
}

.share-doc-header {
  margin-bottom: 20px;
}

.share-doc-title {
  font-size: 22px;
  font-weight: 700;
  color: #f4f4f5;
  margin: 0 0 6px 0;
}

.share-doc-meta {
  font-size: 12px;
  color: #71717a;
}

.share-doc-content {
  padding: 16px 0;
  color: #f4f4f5;
  line-height: 1.8;
}

.share-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #2a2a2a;
  font-size: 11px;
  color: #71717a;
  text-align: center;
}

/* 暗色主题覆盖 */
.share-view :deep(.ant-input-password) {
  background: #1a1a1b;
  border-color: #2a2a2a;
  color: #f4f4f5;
}

.share-view :deep(.ant-btn-primary) {
  background: #722ed1;
  border-color: #722ed1;
}

.share-view :deep(.ant-spin-dot-item) {
  background: #722ed1;
}
</style>
