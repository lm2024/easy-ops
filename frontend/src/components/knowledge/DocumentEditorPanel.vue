<template>
  <div class="editor-panel">
    <template v-if="store.currentDocument">
      <!-- 顶部工具栏 -->
      <div class="editor-toolbar">
        <a-input
          v-model:value="editTitle"
          placeholder="文档标题"
          style="flex: 1; font-size: 16px; font-weight: 500"
          @change="onTitleChange"
        />
        <a-space :size="8">
          <a-radio-group v-model:value="store.editorMode" size="small" @change="onModeChange">
            <a-radio-button value="richtext">富文本</a-radio-button>
            <a-radio-button value="markdown">Markdown</a-radio-button>
          </a-radio-group>
          <a-button type="primary" size="small" :loading="store.saving" @click="handleSave">
            <save-outlined /> 保存
          </a-button>
          <a-button size="small" @click="commentDrawerVisible = true">
            <comment-outlined /> 评论
          </a-button>
          <a-button size="small" @click="annotationDrawerVisible = true">
            ✎ 批注
          </a-button>
          <a-button size="small" @click="versionDrawerVisible = true">
            <history-outlined /> 版本
          </a-button>
        </a-space>
      </div>

      <!-- 协作状态栏 -->
      <CollabStatusBar
        :connected="collab.connected.value"
        :online-users="collab.onlineUsers.value"
      />

      <!-- 编辑器内容区 -->
      <div class="editor-content-area">
        <!-- 富文本模式 -->
        <div v-if="store.editorMode === 'richtext'" class="richtext-layout">
          <TiptapEditor
            ref="tiptapEditorRef"
            :content="editContent"
            :editable="true"
            @update:model-value="onEditorContentUpdate"
            @save="handleSave"
          />
        </div>

        <!-- Markdown 模式：双栏布局 -->
        <div v-else class="markdown-layout">
          <div class="md-editor-column">
            <TiptapEditor
              ref="tiptapEditorRef"
              :content="editContent"
              :editable="true"
              @update:model-value="onEditorContentUpdate"
              @save="handleSave"
            />
          </div>
          <div class="md-preview-column">
            <MarkdownPreview :markdown-content="editContent" />
          </div>
        </div>
      </div>

      <!-- 评论抽屉 -->
      <a-drawer
        v-model:open="commentDrawerVisible"
        title="评论"
        :width="340"
        placement="right"
        :styles="{ body: { background: '#0a0a0b', padding: 0 } }"
      >
        <CommentPanel
          v-if="store.currentDocument?.id"
          :document-id="store.currentDocument.id"
          :comments="store.comments"
          @add-comment="onAddComment"
          @reply-comment="onReplyComment"
          @like-comment="onLikeComment"
        />
      </a-drawer>

      <!-- 批注抽屉 -->
      <a-drawer
        v-model:open="annotationDrawerVisible"
        title="批注"
        :width="340"
        placement="right"
        :styles="{ body: { background: '#0a0a0b', padding: 0 } }"
      >
        <AnnotationPanel
          v-if="store.currentDocument?.id"
          ref="annotationPanelRef"
          :annotations="annotations"
          :document-id="store.currentDocument.id"
          @add-annotation="onAddAnnotation"
          @remove-annotation="onRemoveAnnotation"
          @reply-annotation="onReplyAnnotation"
        />
      </a-drawer>

      <!-- 版本历史抽屉 -->
      <a-drawer
        v-model:open="versionDrawerVisible"
        title="版本历史"
        :width="340"
        placement="right"
        :styles="{ body: { background: '#0a0a0b', padding: 0 } }"
      >
        <VersionHistory
          v-if="store.currentDocument?.id"
          :document-id="store.currentDocument.id"
        />
      </a-drawer>
    </template>

    <!-- 无文档选中时的空状态 -->
    <div class="editor-empty" v-else>
      <a-empty description="选择或创建文档开始编辑" :image-style="{ height: '60px' }">
        <template #description>
          <p style="color: #a1a1aa">选择左侧分类中的文档，或新建文档开始编辑</p>
        </template>
      </a-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useKnowledgeStore } from '../../stores/knowledgeStore'
import { useCollab } from '../../composables/useCollab'
import {
  SaveOutlined, CommentOutlined, HistoryOutlined
} from '@ant-design/icons-vue'
import TiptapEditor from './TiptapEditor.vue'
import MarkdownPreview from './MarkdownPreview.vue'
import CollabStatusBar from './CollabStatusBar.vue'
import CommentPanel from './CommentPanel.vue'
import AnnotationPanel from './AnnotationPanel.vue'
import VersionHistory from './VersionHistory.vue'
import type { KbCommentModel } from '../../types'

const store = useKnowledgeStore()

// ====== 协作 ======
const currentDocId = computed(() => store.currentDocument?.id || null)
const collab = useCollab(currentDocId)

// ====== 编辑状态 ======
const editTitle = ref('')
const editContent = ref('')
const commentDrawerVisible = ref(false)
const annotationDrawerVisible = ref(false)
const versionDrawerVisible = ref(false)

// ====== 组件引用 ======
const tiptapEditorRef = ref<InstanceType<typeof TiptapEditor> | null>(null)
const annotationPanelRef = ref<InstanceType<typeof AnnotationPanel> | null>(null)

// ====== 批注数据 ======
const annotations = computed<KbCommentModel[]>(() => {
  return store.comments.filter((c) => c.type === 'ANNOTATION')
})

// ====== 编辑内容更新 ======
function onEditorContentUpdate(newContent: string) {
  editContent.value = newContent
  if (store.currentDocument) {
    store.currentDocument.content = newContent
  }
}

/** 标题变化 */
function onTitleChange() {
  if (store.currentDocument) {
    store.currentDocument.title = editTitle.value
  }
}

/** 编辑模式变化 */
function onModeChange() {
  // 模式切换由 store 管理，TiptapEditor 内部监听 editorMode 变化处理内容转换
}

/** 保存文档 */
async function handleSave() {
  // 从 TiptapEditor 获取 Markdown 内容
  let mdContent = editContent.value
  if (tiptapEditorRef.value && store.editorMode === 'richtext') {
    mdContent = tiptapEditorRef.value.getMarkdownContent()
  }
  if (store.currentDocument) {
    store.currentDocument.title = editTitle.value
    store.currentDocument.content = mdContent
  }
  await store.saveDocument()
}

/** 添加评论 */
async function onAddComment(documentId: number, content: string) {
  await store.addCommentAction(documentId, content)
}

/** 回复评论 */
async function onReplyComment(_commentId: number, content: string, _replyToId: number) {
  // 目前 addComment API 不直接支持回复，暂时也用 addCommentAction
  // 后端如果支持 parentId，会在 request body 中携带
  if (store.currentDocument?.id) {
    await store.addCommentAction(store.currentDocument.id, content)
  }
}

/** 点赞评论 */
function onLikeComment(commentId: number) {
  // 简化版：暂时只在本地标记，T04 实现后端点赞 API
  console.info('点赞评论', commentId)
}

/** 添加批注 */
async function onAddAnnotation(_annotationId: string, content: string, _selectedText: string) {
  if (store.currentDocument?.id) {
    // 通过 addComment API 发送批注（type=ANNOTATION）
    await store.addCommentAction(store.currentDocument.id, content)
  }
}

/** 移除批注 */
function onRemoveAnnotation(annotationId: string) {
  if (tiptapEditorRef.value) {
    tiptapEditorRef.value.removeAnnotation(annotationId)
  }
  // 删除批注对应的评论记录（简化版：暂不调用删除 API，T04 增强）
  console.info('移除批注', annotationId)
}

/** 回复批注 */
async function onReplyAnnotation(_annotationId: string, content: string) {
  if (store.currentDocument?.id) {
    await store.addCommentAction(store.currentDocument.id, content)
  }
}

// ====== 监听文档变化，同步编辑器 ======
watch(() => store.currentDocument, (doc) => {
  if (doc) {
    editTitle.value = doc.title || ''
    editContent.value = doc.content || ''
    // 加载评论
    if (doc.id) {
      store.fetchComments(doc.id)
    }
  } else {
    editTitle.value = ''
    editContent.value = ''
  }
}, { immediate: true })

// ====== Ctrl+S 保存快捷键 ======
function onKeyDown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    handleSave()
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeyDown)
})
</script>

<style scoped>
.editor-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0a0a0b;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
  min-height: 44px;
}

.editor-content-area {
  flex: 1;
  overflow: hidden;
  display: flex;
}

.richtext-layout {
  flex: 1;
  overflow-y: auto;
}

.markdown-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.md-editor-column {
  flex: 1;
  overflow-y: auto;
}

.md-preview-column {
  flex: 1;
  overflow-y: auto;
  border-left: 1px solid #2a2a2a;
}

.editor-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

/* 暗色主题覆盖 */
.editor-panel :deep(.ant-input) {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.editor-panel :deep(.ant-input:focus) {
  border-color: #722ed1;
}

.editor-panel :deep(.ant-radio-group) {
  color: #f4f4f5;
}

.editor-panel :deep(.ant-radio-button-wrapper) {
  background: #1a1a1b;
  color: #a1a1aa;
  border-color: #2a2a2a;
}

.editor-panel :deep(.ant-radio-button-wrapper-checked) {
  background: #722ed1;
  color: #fff;
  border-color: #722ed1;
}

.editor-panel :deep(.ant-drawer-header) {
  background: #141414;
  color: #f4f4f5;
  border-bottom: 1px solid #2a2a2a;
}

.editor-panel :deep(.ant-drawer-title) {
  color: #f4f4f5;
}

.editor-panel :deep(.ant-drawer-close) {
  color: #a1a1aa;
}
</style>
