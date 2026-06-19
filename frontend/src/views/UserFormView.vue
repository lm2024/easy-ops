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
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="角色" name="role">
        <a-radio-group v-model:value="formState.role">
          <a-radio-button value="admin">管理员</a-radio-button>
          <a-radio-button value="operator">操作员</a-radio-button>
        </a-radio-group>
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
import type { FormInstance, Rule } from 'ant-design-vue'
import {
  SaveOutlined,
  TeamOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = computed(() => !!route.params.id)

const formState = ref<Partial<UserModel>>({
  username: '',
  password: '',
  role: 'operator' as const
})

const rules: Record<string, Rule[]> = {
  username: [{ required: true, message: '请输入用户名' }],
  password: isEdit.value ? [] : [{ required: true, message: '请输入密码' }]
}

async function handleSubmit() {
  try {
    loading.value = true
    const id = route.params.id as string
    if (id) {
      await updateUser(Number(id), formState.value as UserModel)
    } else {
      await createUser(formState.value as UserModel)
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
    formState.value = res.data
  }
})
</script>
