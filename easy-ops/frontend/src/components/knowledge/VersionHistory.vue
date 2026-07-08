<template>
  <div class="version-history">
    <!-- 版本列表 -->
    <div class="version-list">
      <div v-if="versions.length === 0" class="empty-hint">
        <p>暂无版本记录</p>
      </div>
      <div
        v-for="ver in versions"
        :key="ver.versionNo"
        class="version-item"
        :class="{
          selected: selectedOld === ver.versionNo || selectedNew === ver.versionNo,
          'selected-old': selectedOld === ver.versionNo,
          'selected-new': selectedNew === ver.versionNo
        }"
        @click="selectVersion(ver)"
      >
        <div class="version-header">
          <span class="version-no">v{{ ver.versionNo }}</span>
          <span class="version-editor">用户{{ ver.editorId }}</span>
          <span class="version-time">{{ formatTime(ver.createTime) }}</span>
        </div>
        <div class="version-note" v-if="ver.changeNote">
          {{ ver.changeNote }}
        </div>
      </div>
    </div>

    <!-- 选择提示 -->
    <div class="selection-hint" v-if="!selectedOld && !selectedNew">
      <span>点击选择两个版本进行对比</span>
    </div>
    <div class="selection-hint" v-else-if="selectedOld && !selectedNew">
      <span>已选旧版本 v{{ selectedOld }}，请选择新版本</span>
    </div>
    <div class="selection-hint" v-else-if="selectedOld && selectedNew && !showDiffPanel">
      <span>已选 v{{ selectedOld }} → v{{ selectedNew }}，点击「对比版本」查看差异</span>
    </div>

    <!-- Diff 对比区域（使用 VersionDiff 组件） -->
    <div class="diff-area" v-if="showDiffPanel && diffResult">
      <div class="diff-header">
        <span>v{{ selectedOld }} → v{{ selectedNew }} 对比</span>
        <close-outlined
          class="diff-close-btn"
          @click="closeDiffPanel"
        />
      </div>
      <div class="diff-content-wrapper">
        <VersionDiff
          :old-content="diffResult.oldContent"
          :new-content="diffResult.newContent"
        />
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="version-actions">
      <a-button
        size="small"
        :disabled="!selectedOld || !selectedNew"
        @click="showDiff"
      >
        对比版本
      </a-button>
      <a-button
        size="small"
        @click="resetSelection"
      >
        重置选择
      </a-button>
      <a-button
        size="small"
        type="primary"
        danger
        :disabled="!rollbackTarget"
        @click="confirmRollback"
      >
        回滚到 v{{ rollbackTarget }}
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { getVersionDiff, rollbackVersion } from '../../api/knowledge-collab'
import { getDocument } from '../../api/knowledge'
import VersionDiff from './VersionDiff.vue'
import type { KbDocumentModel } from '../../types'

interface VersionItem {
  versionNo: number
  editorId?: number
  changeNote?: string
  createTime?: number
}

const props = defineProps<{
  documentId: number
}>()

const versions = ref<VersionItem[]>([])
const selectedOld = ref<number | null>(null)
const selectedNew = ref<number | null>(null)
const rollbackTarget = ref<number | null>(null)
const diffResult = ref<{ oldContent: string; newContent: string; diffHtml: string } | null>(null)
const showDiffPanel = ref(false)

/** 时间格式化 */
function formatTime(ms: number | undefined): string {
  if (!ms) return ''
  return dayjs(ms).format('MM-DD HH:mm')
}

/** 加载版本列表（简化版：从文档详情中提取版本号） */
async function loadVersions() {
  try {
    const res = await getDocument(props.documentId)
    const doc: KbDocumentModel = res.data
    // 从当前文档获取版本信息，简化版仅显示当前版本
    const currentVersion = doc.versionNo || 1
    const versionList: VersionItem[] = []
    for (let i = 1; i <= currentVersion; i++) {
      versionList.push({
        versionNo: i,
        editorId: doc.lastEditorId,
        changeNote: i === currentVersion ? '当前版本' : `版本 ${i}`,
        createTime: doc.updateTime || doc.createTime,
      })
    }
    versions.value = versionList.reverse()
  } catch {
    versions.value = []
  }
}

/** 选择版本（用于 Diff 对比和回滚） */
function selectVersion(ver: VersionItem) {
  if (!selectedOld.value) {
    selectedOld.value = ver.versionNo
  } else if (!selectedNew.value) {
    selectedNew.value = ver.versionNo
    rollbackTarget.value = ver.versionNo
  } else {
    // 重新选择
    selectedOld.value = ver.versionNo
    selectedNew.value = null
    rollbackTarget.value = null
    diffResult.value = null
    showDiffPanel.value = false
  }
}

/** 重置选择 */
function resetSelection() {
  selectedOld.value = null
  selectedNew.value = null
  rollbackTarget.value = null
  diffResult.value = null
  showDiffPanel.value = false
}

/** 显示版本 Diff */
async function showDiff() {
  if (!selectedOld.value || !selectedNew.value) return
  try {
    const res = await getVersionDiff(props.documentId, selectedOld.value, selectedNew.value)
    diffResult.value = res.data
    showDiffPanel.value = true
  } catch (e: any) {
    message.error('获取版本对比失败: ' + (e.message || '未知错误'))
  }
}

/** 关闭 Diff 面板 */
function closeDiffPanel() {
  showDiffPanel.value = false
}

/** 确认回滚 */
function confirmRollback() {
  if (!rollbackTarget.value) return
  Modal.confirm({
    title: '确认回滚',
    content: `确定要回滚到版本 v${rollbackTarget.value} 吗？当前版本之后的所有修改将丢失。`,
    okText: '确认回滚',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await rollbackVersion(props.documentId, rollbackTarget.value!)
        message.success('已回滚到版本 v' + rollbackTarget.value)
        rollbackTarget.value = null
        selectedOld.value = null
        selectedNew.value = null
        diffResult.value = null
        showDiffPanel.value = false
        await loadVersions()
      } catch (e: any) {
        message.error('回滚失败: ' + (e.message || '未知错误'))
      }
    },
  })
}

onMounted(() => {
  if (props.documentId) {
    loadVersions()
  }
})
</script>

<style scoped>
.version-history {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0a0a0b;
  color: #f4f4f5;
}

.version-list {
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

.version-item {
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 4px;
  padding: 8px 10px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.version-item:hover {
  border-color: #722ed1;
}

.version-item.selected {
  border-color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.version-item.selected-old {
  border-left: 3px solid #EF4444;
}

.version-item.selected-new {
  border-left: 3px solid #10B981;
}

.version-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-no {
  font-size: 13px;
  font-weight: 600;
  color: #722ed1;
}

.version-editor {
  font-size: 12px;
  color: #a1a1aa;
}

.version-time {
  font-size: 11px;
  color: #71717a;
}

.version-note {
  font-size: 12px;
  color: #a1a1aa;
  margin-top: 4px;
}

.selection-hint {
  padding: 8px 12px;
  font-size: 12px;
  color: #722ed1;
  background: rgba(114, 46, 209, 0.06);
  border-top: 1px solid #2a2a2a;
  text-align: center;
}

.diff-area {
  border-top: 1px solid #2a2a2a;
  background: #1a1a1b;
  max-height: 300px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.diff-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: #722ed1;
  font-weight: 500;
}

.diff-close-btn {
  cursor: pointer;
  color: #a1a1aa;
  font-size: 14px;
  transition: color 0.15s;
}

.diff-close-btn:hover {
  color: #f4f4f5;
}

.diff-content-wrapper {
  flex: 1;
  overflow-y: auto;
  max-height: 250px;
}

.version-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #2a2a2a;
  background: #141414;
}

/* 暗色主题覆盖 */
.version-history :deep(.ant-btn) {
  background: #1a1a1b;
  color: #f4f4f5;
  border-color: #2a2a2a;
}

.version-history :deep(.ant-btn-primary) {
  background: #722ed1;
  border-color: #722ed1;
}

.version-history :deep(.ant-btn-dangerous) {
  color: #ef4444;
  border-color: #ef4444;
}
</style>
