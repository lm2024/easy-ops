<template>
  <div class="login-container">
    <a-card title="EasyOps - 登录" style="max-width: 400px; margin: 100px auto">
      <a-form :model="formState" @finish="handleLogin">
        <a-form-item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
          <a-input v-model:value="formState.username" placeholder="用户名" size="large">
            <template #prefix><user-outlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <a-input-password v-model:value="formState.password" placeholder="密码" size="large">
            <template #prefix><lock-outlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block :loading="loading" size="large">
            登录
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login } from '../api/auth'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import type { FormInstance } from 'ant-design-vue'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()

const formState = reactive({
  username: '',
  password: ''
})

async function handleLogin() {
  try {
    loading.value = true
    const res = await login(formState.username, formState.password)
    authStore.setToken(res.data.token)
    // Fetch user info and set
    router.push('/')
  } catch (e: any) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
