<template>
  <a-card :bordered="false" style="border-radius: 8px; max-width: 700px">
    <template #title>
      <a-space>
        <team-outlined style="color: #2f54eb" />
        <span style="font-weight: 600">{{ isEdit ? '编辑用户' : '新增用户' }}</span>
      </a-space>
    </template>

    <a-form ref="formRef" :model="formState" :rules="rules" layout="vertical" @finish="handleSubmit">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="用户名" name="username">
            <a-input v-model:value="formState.username" placeholder="请输入用户名" :disabled="isEdit" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="密码" name="password">
            <a-input-password v-model:value="formState.password" :placeholder="isEdit ? '留空则不修改' : '请输入密码'" />
            <template #extra>
              <span style="font-size:12px;color:#888">至少8位，含大小写字母、数字、特殊字符</span>
            </template>
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item v-if="isAdmin" label="角色" name="role">
        <a-select v-model:value="formState.role" placeholder="请选择角色">
          <a-select-option value="ADMIN">管理员（平台管理员）</a-select-option>
          <a-select-option value="OPERATOR">普通用户</a-select-option>
        </a-select>
      </a-form-item>
      <!-- 租户绑定：平台管理员可指定租户 + 租户角色；租户管理员锁定本租户 -->
      <a-form-item v-if="canManageTenant" label="所属租户">
        <a-select v-model:value="formState.tenantId" placeholder="选择租户" :disabled="!isAdmin">
          <a-select-option v-for="t in tenantOptions" :key="t.id" :value="t.id">{{ t.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="canManageTenant" label="租户角色">
        <a-select v-model:value="formState.tenantRole" placeholder="选择租户角色" allow-clear>
          <a-select-option value="TENANT_ADMIN">租户管理员</a-select-option>
          <a-select-option value="OPERATOR">操作员</a-select-option>
          <a-select-option value="VIEWER">只读</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading">
            <save-outlined /> 保存
          </a-button>
          <a-button @click="$router.back()">取消</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { UserModel } from '../types'
import { createUser, updateUser, getUserById } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { SaveOutlined, TeamOutlined } from '@ant-design/icons-vue'

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=[\]{}|;':",./<>?`~]).{8,}$/

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isSuperAdmin)
const loading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = computed(() => !!route.params.id)

/** 能否管理用户（平台管理员或租户管理员） */
const canManageTenant = computed(() => isAdmin.value || authStore.isTenantAdmin)

const tenantOptions = ref<any[]>([])
async function loadTenants() {
  if (!canManageTenant.value) return
  try {
    const { getTenants } = await import('../api/tenant')
    const res = await getTenants()
    tenantOptions.value = res.data.list || []
  } catch { /* 静默 */ }
}

const formState = ref<Partial<UserModel>>({
  username: '',
  password: '',
  role: 'OPERATOR',
  tenantId: authStore.tenantId ?? undefined,
  tenantRole: 'OPERATOR'
})

const passwordRule: Rule = {
  validator: async (_rule, value) => {
    if (isEdit.value && (!value || !String(value).trim())) return
    if (!value || !PASSWORD_PATTERN.test(String(value))) {
      throw new Error('密码至少8位，需含大小写字母、数字和特殊字符')
    }
  }
}

const rules: Record<string, Rule[]> = {
  username: [{ required: true, message: '请输入用户名' }],
  password: isEdit.value ? [passwordRule] : [{ required: true, message: '请输入密码' }, passwordRule]
}

async function handleSubmit() {
  try {
    loading.value = true
    const id = route.params.id as string
    const payload = { ...formState.value } as UserModel
    // 普通用户（改自己资料）不传角色/租户，由后端保留原值，防自我提权
    if (!isAdmin.value && !authStore.isTenantAdmin) {
      delete (payload as Partial<UserModel>).role
      delete (payload as Partial<UserModel>).tenantId
      delete (payload as Partial<UserModel>).tenantRole
    }
    if (id) {
      await updateUser(Number(id), payload)
    } else {
      await createUser(payload)
    }
    router.push('/users')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  loadTenants()
  const id = route.params.id as string
  if (id) {
    const res = await getUserById(Number(id))
    formState.value = {
      username: res.data.username,
      password: '',
      role: res.data.role,
      tenantId: res.data.tenantId,
      tenantRole: res.data.tenantRole
    }
  }
})
</script>
