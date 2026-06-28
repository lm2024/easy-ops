<template>
  <div class="tiptap-editor-wrapper">
    <!-- 富文本工具栏 -->
    <div class="tiptap-toolbar" v-if="editable && editorMode === 'richtext'">
      <div class="toolbar-group">
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('heading', { level: 1 }) }"
          @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()"
          title="标题1"
        >H1</button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('heading', { level: 2 }) }"
          @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()"
          title="标题2"
        >H2</button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('heading', { level: 3 }) }"
          @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()"
          title="标题3"
        >H3</button>
      </div>
      <div class="toolbar-divider"></div>
      <div class="toolbar-group">
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('bold') }"
          @click="editor?.chain().focus().toggleBold().run()"
          title="粗体"
        ><strong>B</strong></button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('italic') }"
          @click="editor?.chain().focus().toggleItalic().run()"
          title="斜体"
        ><em>I</em></button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('strike') }"
          @click="editor?.chain().focus().toggleStrike().run()"
          title="删除线"
        ><s>S</s></button>
      </div>
      <div class="toolbar-divider"></div>
      <div class="toolbar-group">
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('blockquote') }"
          @click="editor?.chain().focus().toggleBlockquote().run()"
          title="引用块"
        >&ldquo;</button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('bulletList') }"
          @click="editor?.chain().focus().toggleBulletList().run()"
          title="无序列表"
        >&bull;</button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('orderedList') }"
          @click="editor?.chain().focus().toggleOrderedList().run()"
          title="有序列表"
        >1.</button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('codeBlock') }"
          @click="editor?.chain().focus().toggleCodeBlock().run()"
          title="代码块"
        >&lt;/&gt;</button>
      </div>
      <div class="toolbar-divider"></div>
      <div class="toolbar-group">
        <button
          class="toolbar-btn"
          title="插入表格"
          @click="editor?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()"
        >⊞</button>
        <button
          class="toolbar-btn"
          title="插入图片"
          @click="handleInsertImage"
        >🖼</button>
        <button
          class="toolbar-btn"
          :class="{ active: editor?.isActive('link') }"
          title="插入链接"
          @click="handleInsertLink"
        >🔗</button>
        <button
          class="toolbar-btn"
          title="分割线"
          @click="editor?.chain().focus().setHorizontalRule().run()"
        >—</button>
        <button
          class="toolbar-btn"
          title="添加批注"
          @click="handleAddAnnotation"
        >✎</button>
      </div>
    </div>

    <!-- 选中文字时显示的浮动操作条 -->
    <div class="selection-toolbar" v-if="showSelectionToolbar && editorMode === 'richtext' && editable">
      <button
        class="selection-btn"
        :class="{ active: editor?.isActive('bold') }"
        @click="editor?.chain().focus().toggleBold().run()"
      ><strong>B</strong></button>
      <button
        class="selection-btn"
        :class="{ active: editor?.isActive('italic') }"
        @click="editor?.chain().focus().toggleItalic().run()"
      ><em>I</em></button>
      <button
        class="selection-btn"
        :class="{ active: editor?.isActive('link') }"
        @click="handleInsertLink"
      >🔗</button>
      <button
        class="selection-btn"
        @click="handleAddAnnotation"
        title="批注"
      >✎</button>
    </div>

    <!-- 编辑器区域 -->
    <div class="tiptap-editor-body" v-if="editorMode === 'richtext'">
      <EditorContent :editor="editor" class="editor-content" />
    </div>

    <!-- Markdown 编辑模式 -->
    <div class="md-editor-wrapper" v-else>
      <textarea
        ref="mdTextareaRef"
        v-model="mdContent"
        class="md-textarea"
        :readonly="!editable"
        placeholder="Markdown 内容..."
        @input="onMdInput"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Table, TableRow, TableCell, TableHeader } from '@tiptap/extension-table'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { AnnotationMark } from './extensions/AnnotationMark'
import { useKnowledgeStore } from '../../stores/knowledgeStore'
import { Markdown } from 'tiptap-markdown'

const props = defineProps<{
  content: string
  editable: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'save'): void
}>()

const store = useKnowledgeStore()
const editorMode = computed(() => store.editorMode)
const mdTextareaRef = ref<HTMLTextAreaElement | null>(null)
const mdContent = ref(props.content || '')
const showSelectionToolbar = ref(false)

// ====== Tiptap Editor 初始化 ======
const editor = useEditor({
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      codeBlock: {
        HTMLAttributes: {
          class: 'code-block',
        },
      },
    }),
    Table.configure({
      resizable: true,
      HTMLAttributes: {
        class: 'tiptap-table',
      },
    }),
    TableRow,
    TableCell,
    TableHeader,
    Image.configure({
      inline: true,
      allowBase64: true,
      HTMLAttributes: {
        class: 'tiptap-image',
      },
    }),
    Link.configure({
      openOnClick: false,
      HTMLAttributes: {
        class: 'tiptap-link',
        rel: 'noopener noreferrer',
        target: '_blank',
      },
    }),
    Placeholder.configure({
      placeholder: '开始编写...',
    }),
    AnnotationMark,
    Markdown.configure({
      html: true,
      transformPastedText: true,
      transformCopiedText: true,
    }),
  ],
  content: props.content || '',
  editable: props.editable,
  onUpdate: ({ editor: ed }) => {
    const md = (ed.storage as any).markdown?.getMarkdown()
    if (md) {
      emit('update:modelValue', md)
    }
    // 更新选中状态（显示/隐藏浮动操作条）
    showSelectionToolbar.value = ed.state.selection.from !== ed.state.selection.to && ed.state.selection.from > 0
  },
  onSelectionUpdate: ({ editor: ed }) => {
    showSelectionToolbar.value = ed.state.selection.from !== ed.state.selection.to && ed.state.selection.from > 0
  },
  editorProps: {
    handleKeyDown: (_view, event) => {
      if ((event.ctrlKey || event.metaKey) && event.key === 's') {
        event.preventDefault()
        emit('save')
        return true
      }
      return false
    },
  },
})

// ====== 监听 content 变化（从外部加载文档时） ======
watch(
  () => props.content,
  (newContent) => {
    if (editorMode.value === 'richtext' && editor.value) {
      // 仅当内容与当前编辑器不同时才更新（避免覆盖用户正在编辑的内容）
      const currentMd = (editor.value.storage as any).markdown?.getMarkdown() || ''
      if (newContent !== currentMd) {
        editor.value.commands.setContent(newContent || '', { emitUpdate: false })
      }
    } else {
      mdContent.value = newContent || ''
    }
  }
)

// ====== 监听 editable 变化 ======
watch(
  () => props.editable,
  (newEditable) => {
    if (editor.value) {
      editor.value.setEditable(newEditable)
    }
  }
)

// ====== 监听编辑模式切换 ======
watch(editorMode, (newMode, oldMode) => {
  if (newMode === 'markdown' && oldMode === 'richtext' && editor.value) {
    // 从富文本切换到 Markdown：将编辑器内容转为 MD 字符串
    mdContent.value = (editor.value.storage as any).markdown?.getMarkdown() || props.content || ''
  } else if (newMode === 'richtext' && oldMode === 'markdown' && editor.value) {
    // 从 Markdown 切换到富文本：将 MD 字符串加载到编辑器
    editor.value.commands.setContent(mdContent.value || '', { emitUpdate: false })
  }
})

// ====== Markdown 输入事件 ======
function onMdInput() {
  emit('update:modelValue', mdContent.value)
}

// ====== 获取 Markdown 内容 ======
function getMarkdownContent(): string {
  if (editorMode.value === 'richtext' && editor.value) {
    return (editor.value.storage as any).markdown?.getMarkdown() || ''
  }
  return mdContent.value
}

// ====== 插入图片 ======
function handleInsertImage() {
  if (!editor.value) return
  const url = prompt('请输入图片 URL:')
  if (url) {
    editor.value.chain().focus().setImage({ src: url }).run()
  }
}

// ====== 插入链接 ======
function handleInsertLink() {
  if (!editor.value) return
  if (editor.value.isActive('link')) {
    editor.value.chain().focus().unsetLink().run()
    return
  }
  const url = prompt('请输入链接 URL:')
  if (url) {
    editor.value.chain().focus().setLink({ href: url }).run()
  }
}

// ====== 添加批注 ======
function handleAddAnnotation() {
  if (!editor.value) return
  const annotationId = `ann-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
  editor.value.chain().focus().addAnnotation(annotationId).run()
  emit('update:modelValue', getMarkdownContent())
}

// ====== 公开方法（供外部调用） ======
defineExpose({
  getMarkdownContent,
  addAnnotation: (annotationId: string) => {
    if (editor.value) {
      editor.value.chain().focus().addAnnotation(annotationId).run()
    }
  },
  removeAnnotation: (annotationId: string) => {
    if (editor.value) {
      editor.value.chain().focus().removeAnnotation(annotationId).run()
    }
  },
  getEditor: () => editor.value,
})

onBeforeUnmount(() => {
  if (editor.value) {
    editor.value.destroy()
  }
})
</script>

<style scoped>
.tiptap-editor-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0a0a0b;
  position: relative;
}

.tiptap-toolbar {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
  flex-wrap: wrap;
  gap: 2px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: #2a2a2a;
  margin: 0 6px;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 2px 6px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #a1a1aa;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.toolbar-btn:hover {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.toolbar-btn.active {
  background: #722ed1;
  color: #fff;
  border-color: #722ed1;
}

.tiptap-editor-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #1a1a1b;
}

.editor-content {
  max-width: 800px;
  margin: 0 auto;
}

.md-editor-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #1a1a1b;
}

.md-textarea {
  width: 100%;
  height: 100%;
  min-height: 400px;
  resize: none;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #1a1a1b;
  color: #f4f4f5;
  border: 1px solid #2a2a2a;
  border-radius: 4px;
  padding: 12px;
  outline: none;
}

.md-textarea:focus {
  border-color: #722ed1;
}

.md-textarea::placeholder {
  color: #71717a;
}

/* 选中文字时的浮动操作条 */
.selection-toolbar {
  position: absolute;
  top: 60px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 2px;
  background: #141414;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  padding: 4px 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  z-index: 10;
}

.selection-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #a1a1aa;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.selection-btn:hover {
  background: #2a2a2a;
  color: #f4f4f5;
}

.selection-btn.active {
  background: #722ed1;
  color: #fff;
}

/* ====== ProseMirror 编辑器内部样式 ====== */
.tiptap-editor-body :deep(.ProseMirror) {
  color: #f4f4f5;
  font-size: 14px;
  line-height: 1.7;
  outline: none;
  min-height: 200px;
}

.tiptap-editor-body :deep(.ProseMirror > * + *) {
  margin-top: 0.5em;
}

/* 标题样式 */
.tiptap-editor-body :deep(.ProseMirror h1) {
  font-size: 24px;
  font-weight: 600;
  color: #f4f4f5;
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  line-height: 1.3;
}

.tiptap-editor-body :deep(.ProseMirror h2) {
  font-size: 20px;
  font-weight: 600;
  color: #f4f4f5;
  margin-top: 1.2em;
  margin-bottom: 0.4em;
  line-height: 1.4;
}

.tiptap-editor-body :deep(.ProseMirror h3) {
  font-size: 18px;
  font-weight: 600;
  color: #f4f4f5;
  margin-top: 1em;
  margin-bottom: 0.3em;
  line-height: 1.4;
}

/* 段落 */
.tiptap-editor-body :deep(.ProseMirror p) {
  margin-bottom: 0.5em;
}

/* 粗体/斜体/删除线 */
.tiptap-editor-body :deep(.ProseMirror strong) {
  font-weight: 700;
}

.tiptap-editor-body :deep(.ProseMirror em) {
  font-style: italic;
}

.tiptap-editor-body :deep(.ProseMirror s) {
  text-decoration: line-through;
}

/* 引用块 */
.tiptap-editor-body :deep(.ProseMirror blockquote) {
  border-left: 3px solid #722ed1;
  padding: 8px 12px;
  margin-left: 0;
  color: #a1a1aa;
  background: rgba(114, 46, 209, 0.08);
  border-radius: 4px;
}

/* 列表 */
.tiptap-editor-body :deep(.ProseMirror ul) {
  list-style-type: disc;
  padding-left: 20px;
}

.tiptap-editor-body :deep(.ProseMirror ol) {
  list-style-type: decimal;
  padding-left: 20px;
}

.tiptap-editor-body :deep(.ProseMirror li) {
  margin-bottom: 0.2em;
}

.tiptap-editor-body :deep(.ProseMirror li p) {
  margin-bottom: 0;
}

/* 代码块 */
.tiptap-editor-body :deep(.ProseMirror pre) {
  background: #1e1e1e;
  color: #f4f4f5;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  border: 1px solid #2a2a2a;
}

.tiptap-editor-body :deep(.ProseMirror pre code) {
  background: none;
  padding: 0;
  font-size: inherit;
  color: inherit;
  border-radius: 0;
}

.tiptap-editor-body :deep(.ProseMirror code) {
  background: #1e1e1e;
  color: #f59e0b;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.9em;
}

/* 表格 */
.tiptap-editor-body :deep(.ProseMirror table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
  table-layout: fixed;
}

.tiptap-editor-body :deep(.ProseMirror table td),
.tiptap-editor-body :deep(.ProseMirror table th) {
  border: 1px solid #2a2a2a;
  padding: 8px;
  min-width: 80px;
  vertical-align: top;
  box-sizing: border-box;
  position: relative;
}

.tiptap-editor-body :deep(.ProseMirror table th) {
  background: #1e1e1e;
  color: #f4f4f5;
  font-weight: 600;
}

.tiptap-editor-body :deep(.ProseMirror table td) {
  background: #1a1a1b;
  color: #f4f4f5;
}

.tiptap-editor-body :deep(.ProseMirror table .selectedCell) {
  background: rgba(114, 46, 209, 0.2);
}

.tiptap-editor-body :deep(.ProseMirror table .column-resize-handle) {
  position: absolute;
  right: -2px;
  top: 0;
  bottom: -2px;
  width: 4px;
  background-color: #722ed1;
  pointer-events: none;
}

/* 图片 */
.tiptap-editor-body :deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 0.5em 0;
}

.tiptap-editor-body :deep(.ProseMirror img.ProseMirror-selectednode) {
  outline: 2px solid #722ed1;
}

/* 链接 */
.tiptap-editor-body :deep(.ProseMirror a) {
  color: #722ed1;
  text-decoration: underline;
  cursor: pointer;
}

.tiptap-editor-body :deep(.ProseMirror a:hover) {
  color: #9254de;
}

/* 分割线 */
.tiptap-editor-body :deep(.ProseMirror hr) {
  border: none;
  border-top: 1px solid #2a2a2a;
  margin: 1.5em 0;
}

/* Placeholder */
.tiptap-editor-body :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  color: #71717a;
  pointer-events: none;
  height: 0;
}

/* 批注标记 */
.tiptap-editor-body :deep(.annotation-mark) {
  background: rgba(245, 158, 11, 0.3);
  cursor: pointer;
  position: relative;
  border-radius: 2px;
}

.tiptap-editor-body :deep(.annotation-mark:hover) {
  background: rgba(245, 158, 11, 0.5);
}

/* 协作光标 */
.tiptap-editor-body :deep(.collaboration-cursor__caret) {
  border-left: 1px solid;
  margin-left: -1px;
  padding-left: 1px;
  position: relative;
}

.tiptap-editor-body :deep(.collaboration-cursor__label) {
  position: absolute;
  top: -1.4em;
  left: -1px;
  font-size: 11px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px 3px 3px 0;
  color: #fff;
  white-space: nowrap;
  font-weight: 500;
}
</style>
