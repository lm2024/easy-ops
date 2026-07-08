<template>
  <div class="version-diff">
    <!-- 视图切换 -->
    <div class="diff-toolbar">
      <a-radio-group v-model:value="viewMode" size="small">
        <a-radio-button value="unified">统一视图</a-radio-button>
        <a-radio-button value="split">并排视图</a-radio-button>
      </a-radio-group>
      <span class="diff-stats">
        <span class="stat-add">+{{ addedLines }} 行新增</span>
        <span class="stat-remove">-{{ removedLines }} 行删除</span>
      </span>
    </div>

    <!-- 统一视图 -->
    <div v-if="viewMode === 'unified'" class="diff-unified">
      <div
        v-for="(line, idx) in unifiedLines"
        :key="idx"
        class="diff-line"
        :class="{
          'diff-line-add': line.type === 'add',
          'diff-line-remove': line.type === 'remove',
          'diff-line-context': line.type === 'context'
        }"
      >
        <span class="line-number">{{ line.oldLineNo || '' }}</span>
        <span class="line-number">{{ line.newLineNo || '' }}</span>
        <span class="line-prefix">{{ line.prefix }}</span>
        <span class="line-content" v-html="line.html"></span>
      </div>
    </div>

    <!-- 并排视图 -->
    <div v-if="viewMode === 'split'" class="diff-split">
      <div class="diff-split-column">
        <div class="diff-split-header">旧版本</div>
        <div
          v-for="(line, idx) in splitOldLines"
          :key="idx"
          class="diff-line"
          :class="{
            'diff-line-remove': line.type === 'remove',
            'diff-line-context': line.type === 'context'
          }"
        >
          <span class="line-number">{{ line.lineNo || '' }}</span>
          <span class="line-prefix">{{ line.prefix }}</span>
          <span class="line-content" v-html="line.html"></span>
        </div>
      </div>
      <div class="diff-split-column">
        <div class="diff-split-header">新版本</div>
        <div
          v-for="(line, idx) in splitNewLines"
          :key="idx"
          class="diff-line"
          :class="{
            'diff-line-add': line.type === 'add',
            'diff-line-context': line.type === 'context'
          }"
        >
          <span class="line-number">{{ line.lineNo || '' }}</span>
          <span class="line-prefix">{{ line.prefix }}</span>
          <span class="line-content" v-html="line.html"></span>
        </div>
      </div>
    </div>

    <div v-if="unifiedLines.length === 0" class="diff-empty">
      <p>内容完全相同，无差异</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import DiffMatchPatch from 'diff-match-patch'

/** Diff 行数据 */
interface DiffLine {
  type: 'add' | 'remove' | 'context'
  prefix: string
  html: string
  oldLineNo: number | null
  newLineNo: number | null
  lineNo?: number | null
}

const props = defineProps<{
  oldContent: string
  newContent: string
}>()

const viewMode = ref<'unified' | 'split'>('unified')

const dmp = new DiffMatchPatch()

/** 计算逐行 Diff */
const diffResult = computed(() => {
  // 使用 diff-match-patch 的 line 模式
  dmp.Line_H = 1
  const lineDiff = dmp.diff_linesToChars_(props.oldContent, props.newContent)
  const diffs = dmp.diff_main(lineDiff.chars1, lineDiff.chars2, false)
  dmp.diff_charsToLines_(diffs, lineDiff.lineArray)

  const unifiedLines: DiffLine[] = []
  const splitOldLines: DiffLine[] = []
  const splitNewLines: DiffLine[] = []

  let oldLineNo = 1
  let newLineNo = 1
  let addedCount = 0
  let removedCount = 0

  for (const diff of diffs) {
    const op = diff[0] // -1 = remove, 0 = equal, 1 = add
    const text = diff[1]
    const subLines = text.split('\n')
    // 最后一个元素如果是空字符串（因为文本末尾有 \n），跳过
    const lines = subLines.length > 1 && subLines[subLines.length - 1] === ''
      ? subLines.slice(0, -1)
      : subLines

    for (const line of lines) {
      // 对行内字符做细化 Diff（高亮增删文字）
      if (op === 0) {
        // 上下文行（无变化）
        unifiedLines.push({
          type: 'context',
          prefix: ' ',
          html: escapeHtml(line),
          oldLineNo: oldLineNo,
          newLineNo: newLineNo,
        })
        splitOldLines.push({
          type: 'context',
          prefix: ' ',
          html: escapeHtml(line),
          lineNo: oldLineNo,
          oldLineNo: oldLineNo,
          newLineNo: newLineNo,
        })
        splitNewLines.push({
          type: 'context',
          prefix: ' ',
          html: escapeHtml(line),
          lineNo: newLineNo,
          oldLineNo: oldLineNo,
          newLineNo: newLineNo,
        })
        oldLineNo++
        newLineNo++
      } else if (op === -1) {
        // 删除行
        removedCount++
        const charDiffs = dmp.diff_main(line, '')
        dmp.diff_cleanupSemantic(charDiffs)
        const highlightedHtml = renderCharDiff(charDiffs)
        unifiedLines.push({
          type: 'remove',
          prefix: '-',
          html: highlightedHtml,
          oldLineNo: oldLineNo,
          newLineNo: null,
        })
        splitOldLines.push({
          type: 'remove',
          prefix: '-',
          html: highlightedHtml,
          lineNo: oldLineNo,
          oldLineNo: oldLineNo,
          newLineNo: null,
        })
        oldLineNo++
      } else if (op === 1) {
        // 新增行
        addedCount++
        const charDiffs = dmp.diff_main('', line)
        dmp.diff_cleanupSemantic(charDiffs)
        const highlightedHtml = renderCharDiff(charDiffs)
        unifiedLines.push({
          type: 'add',
          prefix: '+',
          html: highlightedHtml,
          oldLineNo: null,
          newLineNo: newLineNo,
        })
        splitNewLines.push({
          type: 'add',
          prefix: '+',
          html: highlightedHtml,
          lineNo: newLineNo,
          oldLineNo: null,
          newLineNo: newLineNo,
        })
        newLineNo++
      }
    }
  }

  return { unifiedLines, splitOldLines, splitNewLines, addedCount, removedCount }
})

const unifiedLines = computed(() => diffResult.value.unifiedLines)
const splitOldLines = computed(() => diffResult.value.splitOldLines)
const splitNewLines = computed(() => diffResult.value.splitNewLines)
const addedLines = computed(() => diffResult.value.addedCount)
const removedLines = computed(() => diffResult.value.removedCount)

/** HTML 转义 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 渲染字符级 Diff（红/绿标注） */
function renderCharDiff(charDiffs: [number, string][]): string {
  let html = ''
  for (const [op, text] of charDiffs) {
    const escaped = escapeHtml(text)
    if (op === 0) {
      // 上下文文字 — 正常显示
      html += escaped
    } else if (op === -1) {
      // 删除文字 — 红色标注
      html += `<span class="char-remove">${escaped}</span>`
    } else if (op === 1) {
      // 新增文字 — 绿色标注
      html += `<span class="char-add">${escaped}</span>`
    }
  }
  return html
}
</script>

<style scoped>
.version-diff {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0a0a0b;
  color: #f4f4f5;
}

.diff-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
}

.diff-stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
}

.stat-add {
  color: #10B981;
}

.stat-remove {
  color: #EF4444;
}

/* 统一视图 */
.diff-unified {
  flex: 1;
  overflow-y: auto;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.diff-line {
  display: flex;
  align-items: flex-start;
  padding: 1px 8px;
  min-height: 20px;
}

.diff-line-context {
  background: transparent;
}

.diff-line-add {
  background: rgba(16, 185, 129, 0.12);
}

.diff-line-remove {
  background: rgba(239, 68, 68, 0.12);
}

.line-number {
  width: 40px;
  text-align: right;
  color: #71717a;
  font-size: 11px;
  user-select: none;
  flex-shrink: 0;
}

.line-prefix {
  width: 20px;
  text-align: center;
  font-weight: 600;
  flex-shrink: 0;
}

.diff-line-add .line-prefix {
  color: #10B981;
}

.diff-line-remove .line-prefix {
  color: #EF4444;
}

.line-content {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 字符级标注 */
:deep(.char-add) {
  background: rgba(16, 185, 129, 0.2);
  color: #10B981;
  border-radius: 2px;
  padding: 0 1px;
}

:deep(.char-remove) {
  background: rgba(239, 68, 68, 0.2);
  color: #EF4444;
  border-radius: 2px;
  padding: 0 1px;
}

/* 并排视图 */
.diff-split {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.diff-split-column {
  flex: 1;
  overflow-y: auto;
  border-right: 1px solid #2a2a2a;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.diff-split-column:last-child {
  border-right: none;
}

.diff-split-header {
  padding: 6px 12px;
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
  font-size: 12px;
  font-weight: 600;
  color: #722ed1;
  text-align: center;
}

.diff-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: #10B981;
  font-size: 14px;
}

/* 暗色主题覆盖 */
.version-diff :deep(.ant-radio-button-wrapper) {
  background: #1a1a1b;
  color: #a1a1aa;
  border-color: #2a2a2a;
}

.version-diff :deep(.ant-radio-button-wrapper-checked) {
  background: #722ed1;
  color: #fff;
  border-color: #722ed1;
}
</style>
