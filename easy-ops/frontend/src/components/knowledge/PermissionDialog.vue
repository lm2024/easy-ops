<template>
  <a-modal
    :open="visible"
    :title="`权限设置 — ${targetName}`"
    :width="600"
    :footer="null"
    @cancel="emit('close')"
    :styles="{ body: { background: '#0a0a0b', padding: '16px' } }"
    class="permission-dialog"
  >
    <!-- 继承提示 -->
    <div class="inherit-hint" v-if="targetType === 'DOCUMENT'">
      <info-circle-outlined style="color: #722ed1" />
      <span>文档级权限优先于分类级继承权限。未单独设置的权限将从所属分类继承。</span>
    </div>

    <!-- 当前权限列表 -->
    <div class="permission-list-section">
      <div class="section-title">当前权限</div>
      <div v-if="permissionList.length === 0" class="empty-hint">
        暂无权限设置，所有用户使用默认权限
      </div>
      <div
        v-for="perm in permissionList"
        :key="perm.id"
        class="permission-item"
      >
        <div class="perm-info">
          <span class="perm-user">用户 #{{ perm.userId }}</span>
          <a-tag
            :color="permissionColor(perm.permissionLevel)"
            size="small"
          >
            {{ perm.permissionLevel }}
          </a-tag>
        </div>
        <a-button
          type="text"
          size="small"
          danger
          @click="handleRemovePermission(perm)"
        >
          <delete-outlined /> 移除
        </a-button>
      </div>
    </div>

    <!-- 添加权限 -->
    <div class="add-permission-section">
      <div class="section-title">添加权限</div>
      <div class="add-form">
        <a-input-number
          v-model:value="newUserId"
          placeholder="用户 ID"
          style="width: 120px"
          size="small"
          :min="1"
        />
        <a-select
          v-model:value="newPermissionLevel"
          placeholder="权限等级"
          style="width: 140px"
          size="small"
          :options="permissionLevelOptions"
        />
        <a-button
          type="primary"
          size="small"
          :disabled="!newUserId || !newPermissionLevel"
          @click="handleAddPermission"
        >
          <plus-outlined /> 添加
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  InfoCircleOutlined, DeleteOutlined, PlusOutlined
} from '@ant-design/icons-vue'
import request from '../../utils/request'
import type { Result, KbDocumentPermissionModel } from '../../types'

const props = defineProps<{
  visible: boolean
  targetId: number
  targetType: 'CATEGORY' | 'DOCUMENT'
  targetName: string
}>()

const emit = defineEmits<{
  close: []
}>()

const permissionList = ref<KbDocumentPermissionModel[]>([])
const newUserId = ref<number | undefined>(undefined)
const newPermissionLevel = ref<'VIEW' | 'EDIT' | 'MANAGE' | undefined>(undefined)

const permissionLevelOptions = [
  { value: 'VIEW', label: '查看 (VIEW)' },
  { value: 'EDIT', label: '编辑 (EDIT)' },
  { value: 'MANAGE', label: '管理 (MANAGE)' },
]

/** 权限颜色 */
function permissionColor(level: string): string {
  if (level === 'VIEW') return '#722ed1'
  if (level === 'EDIT') return '#10B981'
  if (level === 'MANAGE') return '#F59E0B'
  return '#a1a1aa'
}

/** 加载权限列表 */
async function loadPermissions() {
  try {
    const res = await request.get<any, Result<KbDocumentPermissionModel[]>>('/kb/permissions', {
      params: { targetId: props.targetId, targetType: props.targetType }
    })
    permissionList.value = res.data || []
  } catch (e: any) {
    message.error('获取权限列表失败: ' + (e.message || '未知错误'))
    permissionList.value = []
  }
}

/** 添加权限 */
async function handleAddPermission() {
  if (!newUserId.value || !newPermissionLevel.value) return
  try {
    await request.post<any, Result<void>>('/kb/permissions', {
      targetId: props.targetId,
      targetType: props.targetType,
      userId: newUserId.value,
      permissionLevel: newPermissionLevel.value,
    })
    message.success('权限已添加')
    newUserId.value = undefined
    newPermissionLevel.value = undefined
    await loadPermissions()
  } catch (e: any) {
    message.error('添加权限失败: ' + (e.message || '未知错误'))
  }
}

/** 移除权限 */
function handleRemovePermission(perm: KbDocumentPermissionModel) {
  Modal.confirm({
    title: '确认移除权限',
    content: `确定要移除用户 #${perm.userId} 的 ${perm.permissionLevel} 权限吗？`,
    okText: '确认移除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await request.delete<any, Result<void>>(`/kb/permissions/${perm.id}`)
        message.success('权限已移除')
        await loadPermissions()
      } catch (e: any) {
        message.error('移除权限失败: ' + (e.message || '未知错误'))
      }
    },
  })
}

/** 弹窗打开时加载权限 */
watch(() => props.visible, (val) => {
  if (val) {
    loadPermissions()
  }
})

onMounted(() => {
  if (props.visible) {
    loadPermissions()
  }
})
</script>

<style scoped>
.inherit-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(114, 46, 209, 0.08);
  border: 1px solid rgba(114, 46, 209, 0.2);
  border-radius: 4px;
  margin-bottom: 16px;
  font-size: 12px;
  color: #a1a1aa;
}

.permission-list-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #722ed1;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 12px;
  color: #71717a;
  padding: 8px 0;
}

.permission-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: #1a1a1b;
  border: 1px solid #2a2a2a;
  border-radius: 4px;
  margin-bottom: 4px;
}

.perm-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.perm-user {
  font-size: 13px;
  color: #f4f4f5;
}

.add-permission-section {
  border-top: 1px solid #2a2a2a;
  padding-top: 12px;
}

.add-form {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 暗色主题覆盖 */
.permission-dialog :deep(.ant-modal-header) {
  background: #141414;
  border-bottom: 1px solid #2a2a2a;
}

.permission-dialog :deep(.ant-modal-title) {
  color: #f4f4f5;
}

.permission-dialog :deep(.ant-modal-close) {
  color: #a1a1aa;
}

.permission-dialog :deep(.ant-modal-content) {
  background: #0a0a0b;
}

.permission-dialog :deep(.ant-input-number) {
  background: #1a1a1b;
  border-color: #2a2a2a;
  color: #f4f4f5;
}

.permission-dialog :deep(.ant-select-selector) {
  background: #1a1a1b;
  border-color: #2a2a2a;
  color: #f4f4f5;
}

.permission-dialog :deep(.ant-btn-primary) {
  background: #722ed1;
  border-color: #722ed1;
}

.permission-dialog :deep(.ant-tag) {
  border-radius: 4px;
}
</style>
