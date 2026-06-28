<template>
  <div class="markdown-preview">
    <div class="preview-content" v-html="renderedHtml" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  markdownContent: string
  scrollPosition?: number
}>()

/**
 * 简化版 Markdown → HTML 转换器
 * 使用正则将常见 Markdown 语法转为 HTML，避免创建 Tiptap Editor 实例
 */
function simpleMarkdownToHtml(md: string): string {
  if (!md) return '<p class="empty-hint">暂无内容</p>'
  let html = md

  // 代码块（先处理，避免内部被后续规则干扰）
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_match, lang, code) => {
    const escapedCode = code
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
    return `<pre><code class="language-${lang}">${escapedCode}</code></pre>`
  })

  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')

  // 标题
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')

  // 分割线
  html = html.replace(/^---$/gm, '<hr />')

  // 粗体和斜体
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')

  // 删除线
  html = html.replace(/~~(.+?)~~/g, '<s>$1</s>')

  // 链接
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')

  // 图片
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1" />')

  // 引用块
  html = html.replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')

  // 无序列表
  html = html.replace(/^[*-] (.+)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>.*<\/li>\n?)+/g, (match) => `<ul>${match}</ul>`)

  // 有序列表
  html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>')

  // 段落处理：将非标签行转为 <p>
  const lines = html.split('\n')
  const resultLines: string[] = []
  for (const line of lines) {
    const trimmed = line.trim()
    if (
      trimmed.startsWith('<h') ||
      trimmed.startsWith('<pre') ||
      trimmed.startsWith('<ul') ||
      trimmed.startsWith('<ol') ||
      trimmed.startsWith('<li') ||
      trimmed.startsWith('<blockquote') ||
      trimmed.startsWith('<hr') ||
      trimmed.startsWith('<img') ||
      trimmed === ''
    ) {
      resultLines.push(line)
    } else {
      resultLines.push(`<p>${trimmed}</p>`)
    }
  }

  return resultLines.join('\n')
}

const renderedHtml = computed(() => {
  return simpleMarkdownToHtml(props.markdownContent || '')
})
</script>

<style scoped>
.markdown-preview {
  height: 100%;
  overflow-y: auto;
  padding: 16px;
  background: #1a1a1b;
  border-left: 1px solid #2a2a2a;
}

.preview-content {
  max-width: 800px;
  margin: 0 auto;
  color: #f4f4f5;
  font-size: 14px;
  line-height: 1.7;
}

.preview-content .empty-hint {
  color: #71717a;
  font-size: 13px;
}

.preview-content h1 {
  font-size: 24px;
  font-weight: 600;
  color: #f4f4f5;
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  border-bottom: 1px solid #2a2a2a;
}

.preview-content h2 {
  font-size: 20px;
  font-weight: 600;
  color: #f4f4f5;
  margin-top: 1.2em;
  margin-bottom: 0.4em;
}

.preview-content h3 {
  font-size: 18px;
  font-weight: 600;
  color: #f4f4f5;
  margin-top: 1em;
  margin-bottom: 0.3em;
}

.preview-content p {
  margin-bottom: 0.5em;
}

.preview-content strong {
  font-weight: 700;
  color: #f4f4f5;
}

.preview-content em {
  font-style: italic;
}

.preview-content s {
  text-decoration: line-through;
  color: #71717a;
}

.preview-content blockquote {
  border-left: 3px solid #722ed1;
  padding: 8px 12px;
  margin-left: 0;
  color: #a1a1aa;
  background: rgba(114, 46, 209, 0.08);
  border-radius: 4px;
}

.preview-content ul {
  list-style-type: disc;
  padding-left: 20px;
}

.preview-content ol {
  list-style-type: decimal;
  padding-left: 20px;
}

.preview-content li {
  margin-bottom: 0.2em;
}

.preview-content pre {
  background: #1e1e1e;
  color: #f4f4f5;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  border: 1px solid #2a2a2a;
}

.preview-content code {
  background: #1e1e1e;
  color: #f59e0b;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.9em;
}

.preview-content pre code {
  background: none;
  padding: 0;
}

.preview-content table {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}

.preview-content th,
.preview-content td {
  border: 1px solid #2a2a2a;
  padding: 8px;
}

.preview-content th {
  background: #1e1e1e;
  font-weight: 600;
}

.preview-content td {
  background: #1a1a1b;
}

.preview-content hr {
  border: none;
  border-top: 1px solid #2a2a2a;
  margin: 1.5em 0;
}

.preview-content a {
  color: #722ed1;
  text-decoration: underline;
}

.preview-content img {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
}
</style>
