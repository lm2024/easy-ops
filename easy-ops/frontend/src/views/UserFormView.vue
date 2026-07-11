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
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { SaveOutlined, TeamOutlined } from '@ant-design/icons-vue'

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=[\]{}|;':",./<>?`~]).{8,}$/

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = computed(() => !!route.params.id)

const formState = ref<Partial<UserModel>>({
  username: '',
  password: ''
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
    const payload = { ...formState.value, role: 'ADMIN' as const } as UserModel
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
  const id = route.params.id as string
  if (id) {
    const res = await getUserById(Number(id))
    formState.value = { username: res.data.username, password: '' }
  }
})
</script>
