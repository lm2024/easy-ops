<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <notification-outlined style="color: #fa541c" />
          <span style="font-weight: 600">告警配置</span>
        </a-space>
      </template>

      <a-form :model="formState" layout="vertical" @finish="handleSave" style="max-width: 600px">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="SMTP服务器" name="smtpHost">
              <a-input v-model:value="formState.smtpHost" placeholder="smtp.example.com" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="SMTP端口" name="smtpPort">
              <a-input-number v-model:value="formState.smtpPort" style="width: 100%" :min="1" :max="65535" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="发件人邮箱" name="smtpUser">
              <a-input v-model:value="formState.smtpUser" placeholder="alert@example.com" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="邮箱密码" name="smtpPassword">
              <a-input-password v-model:value="formState.smtpPassword" placeholder="••••••••" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="接收地址" name="receiveAddress">
          <a-input v-model:value="formState.receiveAddress" placeholder="多个邮箱用逗号分隔" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="loading">
              <save-outlined /> 保存
            </a-button>
            <a-button @click="sendTest">
              <mail-outlined /> 发送测试邮件
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { AlarmConfigModel } from '../types'
import { getAlarmConfig, saveAlarmConfig } from '../api/monitor'
import {
  SaveOutlined,
  MailOutlined,
  NotificationOutlined
} from '@ant-design/icons-vue'

const formState = ref<Partial<AlarmConfigModel>>({
  smtpHost: '',
  smtpPort: 465,
  smtpUser: '',
  smtpPassword: '',
  receiveAddress: ''
})
const loading = ref(false)

async function fetchData() {
  const res = await getAlarmConfig()
  formState.value = res.data
}

async function handleSave() {
  try {
    loading.value = true
    await saveAlarmConfig(formState.value as AlarmConfigModel)
  } finally {
    loading.value = false
  }
}

async function sendTest() {
  // Placeholder: send test alarm
}

onMounted(fetchData)
</script>
