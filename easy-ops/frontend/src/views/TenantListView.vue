<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <apartment-outlined style="color: #e8ff59" />
          <span style="font-weight: 600">租户管理</span>
          <a-tooltip title="租户是多产品/业务线的隔离边界。平台管理员可创建租户、绑定成员并分配租户角色。">
            <info-circle-outlined style="color: #999; cursor: help" />
          </a-tooltip>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="openCreate">
          <plus-outlined /> 新增租户
        </a-button>
      </template>

      <a-table
        :columns="columns"
        :data-source="tenants"
        :loading="loading"
        :pagination="false"
        row-key="id"
        @expand="onRowExpand"
      >
        <template #expandedRowRender="{ record }">
          <div class="member-panel">
            <a-space style="margin-bottom: 12px">
              <span style="font-weight: 600">成员管理（{{ record.name }}）</span>
              <a-select
                :value="getAddForm(record.id)?.userId"
                placeholder="选择用户"
                style="width: 180px"
                :options="userOptions.map((u: any) => ({ label: u.username, value: u.id }))"
                show-search
                option-filter-prop="label"
                allow-clear
                @change="(v: number | undefined) => setAddFormUserId(record.id, v)"
              />
              <a-select
                :value="getAddForm(record.id)?.role"
                style="width: 130px"
                :options="ROLE_OPTIONS"
                placeholder="角色"
                @change="(v: string) => setAddFormRole(record.id, v)"
              />
              <a-button size="small" type="primary" :loading="addingMember[record.id]"
                        @click="addMember(record.id)">
                <plus-outlined /> 添加成员
              </a-button>
            </a-space>
            <a-table
              :columns="memberColumns"
              :data-source="memberMap[record.id] || []"
              :loading="memberLoading[record.id]"
              :pagination="false"
              row-key="id"
              size="small"
            >
              <template #bodyCell="{ column, record: m }">
                <template v-if="column.key === 'role'">
                  <a-select
                    :value="m.role"
                    size="small"
                    style="width: 130px"
                    :options="ROLE_OPTIONS"
                    @change="(v: string) => changeRole(record.id, m.userId, v)"
                  />
                </template>
                <template v-if="column.key === 'status'">
                  <a-badge :status="m.status === 1 ? 'success' : 'default'"
                           :text="m.status === 1 ? '启用' : '停用'" />
                </template>
                <template v-if="column.key === 'action'">
                  <a-popconfirm title="确定移除该成员？" ok-text="确定" cancel-text="取消"
                                @confirm="removeMemberAction(record.id, m.userId)">
                    <a-button type="link" size="small" danger>
                      <delete-outlined /> 移除
                    </a-button>
                  </a-popconfirm>
                </template>
              </template>
            </a-table>
          </div>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-badge :status="record.status === 1 ? 'success' : 'default'"
                     :text="record.status === 1 ? '启用' : '停用'" />
          </template>
          <template v-if="column.key === 'stats'">
            <a-space size="middle">
              <a-tag color="blue">节点 {{ record.nodeCount ?? 0 }}</a-tag>
              <a-tag color="green">项目 {{ record.projectCount ?? 0 }}</a-tag>
              <a-tag color="orange">成员 {{ record.memberCount ?? 0 }}</a-tag>
            </a-space>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="record.code !== 'default'" type="link" size="small" @click="openEdit(record)">
                <edit-outlined /> 编辑
              </a-button>
              <a-popconfirm v-if="record.code !== 'default'" title="确定删除该租户？" ok-text="确定" cancel-text="取消"
                            @confirm="deleteTenantAction(record.id)">
                <a-button type="link" size="small" danger>
                  <delete-outlined /> 删除
                </a-button>
              </a-popconfirm>
              <a-tag v-if="record.code === 'default'" color="default">内置</a-tag>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑租户弹窗 -->
    <a-modal v-model:open="modalVisible" :title="editing ? '编辑租户' : '新增租户'"
             :confirm-loading="saving" @ok="handleSave">
      <a-form :model="form" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="租户编码" required>
          <a-input v-model:value="form.code" placeholder="唯一标识，如 product-b" :disabled="!!editing" />
        </a-form-item>
        <a-form-item label="租户名称" required>
          <a-input v-model:value="form.name" placeholder="如：产品B" />
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">停用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import type { TenantModel, TenantMemberModel, UserModel } from '../types'
import { getTenants, createTenant, updateTenant, deleteTenant,
         getTenantMembers, addTenantMember, updateTenantMember, removeTenantMember,
         listUsersForMember } from '../api/tenant'
import { PlusOutlined, EditOutlined, DeleteOutlined, ApartmentOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'

const tenants = ref<TenantModel[]>([])
const loading = ref(false)
const saving = ref(false)
const modalVisible = ref(false)
const editing = ref<TenantModel | null>(null)
const form = reactive<{ code: string; name: string; status: number }>({ code: '', name: '', status: 1 })

const ROLE_OPTIONS = [
  { label: '租户管理员', value: 'TENANT_ADMIN' },
  { label: '操作员', value: 'OPERATOR' },
  { label: '只读', value: 'VIEWER' },
]

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '资源统计', key: 'stats', width: 230 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const }
]

const memberColumns = [
  { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 90 },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '角色', key: 'role', width: 150 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 90 }
]

async function fetchTenants() {
  try {
    loading.value = true
    const res = await getTenants()
    tenants.value = res.data.list || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.code = ''
  form.name = ''
  form.status = 1
  modalVisible.value = true
}

function openEdit(record: TenantModel) {
  editing.value = record
  form.code = record.code
  form.name = record.name
  form.status = record.status
  modalVisible.value = true
}

async function handleSave() {
  if (!form.code.trim() || !form.name.trim()) {
    message.warning('请填写租户编码和名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateTenant(editing.value.id, { name: form.name, status: form.status })
      message.success('租户已更新')
    } else {
      await createTenant({ code: form.code, name: form.name, status: form.status })
      message.success('租户已创建')
    }
    modalVisible.value = false
    await fetchTenants()
  } finally {
    saving.value = false
  }
}

async function deleteTenantAction(id: number) {
  try {
    await deleteTenant(id)
    message.success('租户已删除')
    await fetchTenants()
  } catch (err: any) {
    // 拦截器已提示（有资源/default 禁删）
  }
}

// ============ 成员管理（展开行） ============
const memberMap = reactive<Record<number, TenantMemberModel[]>>({})
const memberLoading = reactive<Record<number, boolean>>({})
const addingMember = reactive<Record<number, boolean>>({})
const addForm = reactive<Record<number, { userId?: number; role?: string }>>({})
const userOptions = ref<UserModel[]>([])
const loadedUsers = ref(false)

async function ensureUserOptions() {
  if (loadedUsers.value) return
  try {
    const res = await listUsersForMember(1, 200)
    userOptions.value = res.data.list || []
    loadedUsers.value = true
  } catch { /* 静默 */ }
}

function getAddForm(tenantId: number) {
  if (!addForm[tenantId]) addForm[tenantId] = { role: 'OPERATOR' }
  return addForm[tenantId]
}

function setAddFormUserId(tenantId: number, v?: number) {
  getAddForm(tenantId).userId = v
}

function setAddFormRole(tenantId: number, v: string) {
  getAddForm(tenantId).role = v
}

async function onRowExpand(expanded: boolean, record: TenantModel) {
  if (expanded) {
    getAddForm(record.id)
    memberLoading[record.id] = true
    try {
      const res = await getTenantMembers(record.id)
      memberMap[record.id] = res.data || []
    } finally {
      memberLoading[record.id] = false
    }
    ensureUserOptions()
  }
}

async function addMember(tenantId: number) {
  const payload = addForm[tenantId] || {}
  if (!payload.userId) {
    message.warning('请选择用户')
    return
  }
  addingMember[tenantId] = true
  try {
    await addTenantMember(tenantId, payload.userId, payload.role || 'OPERATOR')
    message.success('成员已添加')
    const res = await getTenantMembers(tenantId)
    memberMap[tenantId] = res.data || []
    addForm[tenantId] = { role: 'OPERATOR' }
  } finally {
    addingMember[tenantId] = false
  }
}

async function changeRole(tenantId: number, userId: number, role: string) {
  await updateTenantMember(tenantId, userId, { role })
  message.success('角色已更新')
  const res = await getTenantMembers(tenantId)
  memberMap[tenantId] = res.data || []
}

async function removeMemberAction(tenantId: number, userId: number) {
  await removeTenantMember(tenantId, userId)
  message.success('成员已移除')
  const res = await getTenantMembers(tenantId)
  memberMap[tenantId] = res.data || []
}

onMounted(fetchTenants)
</script>

<style scoped>
.member-panel {
  padding: 12px 16px;
  background: var(--eo-content-bg);
  border-radius: 8px;
}
</style>
